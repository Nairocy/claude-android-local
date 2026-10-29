package com.nairocy.claude

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nairocy.claude.data.Account
import com.nairocy.claude.data.AccountStore
import com.nairocy.claude.data.AppDirs
import com.nairocy.claude.data.AvatarStore
import com.nairocy.claude.data.ContextInfo
import com.nairocy.claude.ui.home.Greeting
import com.nairocy.claude.data.ArtifactItem
import com.nairocy.claude.data.Artifacts
import com.nairocy.claude.data.ConversationStore
import com.nairocy.claude.data.Plan
import com.nairocy.claude.data.Project
import com.nairocy.claude.data.Prefs
import com.nairocy.claude.data.ProjectStore
import com.nairocy.claude.ui.settings.ProfileScreen
import com.nairocy.claude.ui.settings.ProfileViewModel
import com.nairocy.claude.ui.chat.ChatScreen
import com.nairocy.claude.ui.chat.ChatSessions
import com.nairocy.claude.ui.artifacts.ArtifactDetailScreen
import com.nairocy.claude.ui.artifacts.ArtifactViewScreen
import com.nairocy.claude.ui.artifacts.ArtifactsScreen
import com.nairocy.claude.ui.chat.ChatViewModel
import com.nairocy.claude.ui.code.CodePreviewScreen
import com.nairocy.claude.ui.code.CodeScreen
import com.nairocy.claude.ui.code.CodeSessions
import com.nairocy.claude.ui.projects.ProjectDetailScreen
import com.nairocy.claude.ui.projects.ProjectsScreen
import com.nairocy.claude.ui.conversations.ConversationsScreen
import com.nairocy.claude.ui.drawer.AppDrawer
import com.nairocy.claude.ui.drawer.RenameConversationDialog
import com.nairocy.claude.ui.drawer.DeleteConversationDialog
import com.nairocy.claude.ui.drawer.AddConversationToProjectDialog
import com.nairocy.claude.ui.home.HomeScreen
import com.nairocy.claude.ui.features.FeaturesScreen
import com.nairocy.claude.ui.focus.FocusScreen
import com.nairocy.claude.ui.memory.MemoryTopicScreen
import com.nairocy.claude.ui.memory.MemoryTopicsScreen
import com.nairocy.claude.ui.notifications.NotificationsScreen
import com.nairocy.claude.ui.permissions.PermissionsScreen
import com.nairocy.claude.ui.privacy.PrivacyScreen
import com.nairocy.claude.ui.settings.ApiSettingsScreen
import com.nairocy.claude.ui.settings.SettingsScreen
import com.nairocy.claude.ui.splash.SplashGate
import com.nairocy.claude.ui.theme.ClaudeTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** 点「会话已完成」通知进来时带的目标会话 id；由 Compose 侧消费后置空。 */
    private var deepConvId by mutableStateOf<String?>(null)
    /** 点「Code 会话已完成」通知进来时带的目标 Code 会话 id。 */
    private var deepCodeConvId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 冷启动即从通知进来：先把 extra 接住，登录后 Compose 侧会落到那条会话
        deepConvId = intent?.getStringExtra(com.nairocy.claude.data.ChatNotifications.EXTRA_CONV)
        deepCodeConvId = intent?.getStringExtra(com.nairocy.claude.data.CodeNotifications.EXTRA_CODE_CONV)
        // debug 包开 WebView 远程调试：artifact 渲染白屏时可以用 chrome://inspect / CDP 直接看页面
        if (BuildConfig.DEBUG) android.webkit.WebView.setWebContentsDebuggingEnabled(true)
        enableEdgeToEdge()
        setContent {
            val ctx = LocalContext.current
            val prefs = remember { Prefs(ctx) }
            var themeMode by remember { mutableStateOf(prefs.themeMode) }
            var fontMode by remember { mutableStateOf(prefs.fontMode) }
            var haptic by remember { mutableStateOf(prefs.haptic) }
            // 深浅色只算一次：启动页画在 ClaudeTheme 外面（SplashGate 是它的父级），
            // 读不到主题色板，必须显式传下去。
            val dark = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            SplashGate(dark = dark) {
            ClaudeTheme(
                darkTheme = dark,
                systemFont = fontMode == "system",
            ) {
                val accountStore = remember { AccountStore(ctx) }
                var account by remember { mutableStateOf(accountStore.snapshot()) }
                val profileVm: ProfileViewModel = viewModel()
                val scope = androidx.compose.runtime.rememberCoroutineScope()

                // 资料页每次保存成功 → 落盘 + 刷新设置/抽屉里的展示
                androidx.compose.runtime.LaunchedEffect(profileVm.savedAccount) {
                    profileVm.savedAccount?.let { next ->
                        accountStore.save(next)
                        accountStore.avatarSyncedVer = next.avatarVer
                        account = next
                        profileVm.consumeSaved()
                    }
                }

                val convStore = remember(account.uid) { ConversationStore(ctx) }
                val projectStore = remember(account.uid) { ProjectStore(ctx) }
                // 本地版没有订阅体系：全部能力解锁，账号行固定显示「本机」
                val plan = Plan.MAX
                val planReady = true
                var projects by remember { mutableStateOf(projectStore.loadAll()) }
                var selectedProject by remember { mutableStateOf<Project?>(null) }
                var convs by remember { mutableStateOf(convStore.loadAll()) }
                var drawerOpen by remember { mutableStateOf(false) }
                var renameConv by remember { mutableStateOf<com.nairocy.claude.data.Conversation?>(null) }
                var renameConvText by remember { mutableStateOf("") }
                var deleteConv by remember { mutableStateOf<com.nairocy.claude.data.Conversation?>(null) }
                var addConv by remember { mutableStateOf<com.nairocy.claude.data.Conversation?>(null) }
                // 打开抽屉/弹层时收回键盘焦点：否则输入法会浮在抽屉上面
                val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
                val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                androidx.compose.runtime.LaunchedEffect(drawerOpen) {
                    if (drawerOpen) {
                        focusManager.clearFocus(force = true)
                        keyboard?.hide()
                    }
                }
                // ⚠️ 会话状态必须放进程级单例，不能再用 viewModel(key = …)：
                // ViewModel 挂在 Activity 的 ViewModelStore 上，Activity 在后台被系统销毁
                // （国产 ROM 上是常态）时 onCleared 会把正在跑的流一起取消 —— 用户切页面 /
                // 回桌面回来，正在生成的回答就没了。
                val app = ctx.applicationContext as android.app.Application
                val hadChatSession = remember(account.uid) { ChatSessions.has(account.uid) }
                val chatVm: ChatViewModel = remember(account.uid) { ChatSessions.of(app, account.uid) }
                // Code 页同样是进程级单例（理由同上）：切页面 / Activity 重建都不该把正在改代码的流掐断
                val codeVm = remember(account.uid) { CodeSessions.of(app, account.uid) }
                var codePreviewPath by remember { mutableStateOf("/index.html") }
                // Activity 重建后回到对话页：流在进程里没断，回来就能看见它接着写。
                // 其它二级页依赖的选中项都是 remember 的（重建即空），所以只恢复 CHAT 一页。
                var screen by androidx.compose.runtime.saveable.rememberSaveable(
                    stateSaver = androidx.compose.runtime.saveable.Saver<Screen, String>(
                        save = { it.name },
                        restore = { name ->
                            if (hadChatSession && name == Screen.CHAT.name) Screen.CHAT else Screen.HOME
                        },
                    ),
                ) { mutableStateOf(Screen.HOME) }
                var pending by remember { mutableStateOf<String?>(null) }
                var chatFromList by remember { mutableStateOf(false) }
                // 点「会话已完成」通知进来：直接落到那条会话（冷启动 / 前台都走这里）。
                // load() 对「此刻正在生成」的那条是 no-op，所以不会把还在写的流掐掉。
                androidx.compose.runtime.LaunchedEffect(deepConvId, account.uid) {
                    val id = deepConvId ?: return@LaunchedEffect
                    deepConvId = null
                    val conv = runCatching {
                        convStore.loadAll().firstOrNull { it.id == id }
                    }.getOrNull() ?: return@LaunchedEffect
                    chatVm.load(conv)
                    pending = null
                    chatFromList = false
                    screen = Screen.CHAT
                }
                // 点 Code 完成通知进来：切到 Code 页并打开那条会话
                androidx.compose.runtime.LaunchedEffect(deepCodeConvId, account.uid) {
                    val id = deepCodeConvId ?: return@LaunchedEffect
                    deepCodeConvId = null
                    runCatching {
                        val conv = codeVm.historyGroups().flatMap { it.second }.firstOrNull { it.id == id }
                        if (conv != null) {
                            if (conv.workspaceId.isNotBlank() && conv.workspaceId != codeVm.workspaceId) {
                                codeVm.switchWorkspace(conv.workspaceId)
                            }
                            codeVm.openConversation(id)
                        }
                    }
                    screen = Screen.CODE
                }
                var selectedArtifact by remember { mutableStateOf<ArtifactItem?>(null) }
                // artifact 渲染页（WebView）：记住是从对话卡片还是详情页进来的，返回时回原处
                var previewArtifact by remember { mutableStateOf<ArtifactItem?>(null) }
                var previewFrom by remember { mutableStateOf(Screen.CHAT) }
                var selectedMemoryTopic by remember { mutableStateOf<String?>(null) }
                val artifacts = remember(convs) { Artifacts.collect(convs) }
                // launcher 必须在 Activity 层：放进 HomeScreen/ChatScreen 会让 Paparazzi 的 JVM 渲染崩
                val pickChatImage = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia(),
                ) { uri -> if (uri != null) chatVm.addImage(uri) }
                val pickHomeImage = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia(),
                ) { uri ->
                    if (uri != null) {
                        chatVm.startFreshIfStale()
                        chatVm.addImage(uri)
                        pending = null
                        chatFromList = false
                        screen = Screen.CHAT
                    }
                }
                var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
                var cameraFromHome by remember { mutableStateOf(false) }
                val takePicture = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.TakePicture(),
                ) { ok ->
                    val uri = cameraUri
                    if (ok && uri != null) {
                        if (cameraFromHome) chatVm.startFreshIfStale()
                        chatVm.addImage(uri)
                        if (cameraFromHome) {
                            pending = null
                            chatFromList = false
                            screen = Screen.CHAT
                        }
                    }
                    cameraUri = null
                }
                fun openCamera(fromHome: Boolean) {
                    val dir = java.io.File(ctx.cacheDir, "camera").apply { mkdirs() }
                    val file = java.io.File(dir, "shot_" + System.currentTimeMillis() + ".jpg")
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        ctx,
                        ctx.packageName + ".fileprovider",
                        file,
                    )
                    cameraFromHome = fromHome
                    cameraUri = uri
                    takePicture.launch(uri)
                }

                val pickChatFile = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri -> if (uri != null) chatVm.addFile(uri) }
                val pickAvatar = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia(),
                ) { uri ->
                    if (uri != null) profileVm.uploadAvatar(ctx, account.token, account, uri)
                }

                // 首次进入主界面：请求位置 / 日历权限（只主动问一次），授权后立刻抓一次上下文
                val permLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions(),
                ) { _ ->
                    scope.launch { runCatching { ContextInfo.refresh(ctx) } }
                }
                androidx.compose.runtime.LaunchedEffect(account.uid) {
                    val ask = mutableListOf<String>()
                    if (!prefs.permAsked) {
                        prefs.permAsked = true
                        ask += ContextInfo.missingPermissions(ctx)
                    }
                    // 通知权限单独记账（老用户升级上来也要被问一次）：Android 13+ 才是运行时权限
                    if (!prefs.notifyAsked && android.os.Build.VERSION.SDK_INT >= 33 &&
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            ctx, android.Manifest.permission.POST_NOTIFICATIONS,
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        prefs.notifyAsked = true
                        ask += android.Manifest.permission.POST_NOTIFICATIONS
                    }
                    if (ask.isNotEmpty()) {
                        permLauncher.launch(ask.toTypedArray())
                        return@LaunchedEffect
                    }
                    // 每次进主界面都刷一次（跨天时日程会变）
                    runCatching { ContextInfo.refresh(ctx) }
                }

                // 首页问候：按小时分档，普通时段每天随机一个词；只在文案变化时才触发重组
                var greeting by remember {
                    mutableStateOf(Greeting.forMoment(java.time.LocalDateTime.now(), account.name()))
                }
                androidx.compose.runtime.LaunchedEffect(account.uid, account.displayName, account.preferredName) {
                    while (true) {
                        val next = Greeting.forMoment(java.time.LocalDateTime.now(), account.name())
                        if (next != greeting) greeting = next
                        kotlinx.coroutines.delay(60_000)
                    }
                }
                val pickHomeFile = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        chatVm.startFreshIfStale()
                        chatVm.addFile(uri)
                        pending = null
                        chatFromList = false
                        screen = Screen.CHAT
                    }
                }

                androidx.compose.runtime.LaunchedEffect(drawerOpen) {
                    if (drawerOpen) convs = convStore.loadAll()
                }

                BackHandler(enabled = drawerOpen || screen != Screen.HOME) {
                    when {
                        drawerOpen -> drawerOpen = false
                        screen == Screen.ARTIFACT_VIEW -> screen = previewFrom
                        screen == Screen.CODE_PREVIEW -> screen = Screen.CODE
                        screen == Screen.ARTIFACT_DETAIL -> screen = Screen.ARTIFACTS
                        screen == Screen.PROJECT_DETAIL -> screen = Screen.PROJECTS
                        screen == Screen.CHAT && chatFromList -> screen = Screen.CONVERSATIONS
                        screen == Screen.API_SETTINGS -> screen = Screen.SETTINGS
                        screen == Screen.FEATURES -> screen = Screen.SETTINGS
                        screen == Screen.PERMISSIONS -> screen = Screen.SETTINGS
                        screen == Screen.NOTIFICATIONS -> screen = Screen.SETTINGS
                        screen == Screen.FOCUS -> screen = Screen.SETTINGS
                        screen == Screen.PRIVACY -> screen = Screen.SETTINGS
                        screen == Screen.PROFILE -> screen = Screen.SETTINGS
                        screen == Screen.MEMORY_TOPICS -> screen = Screen.FEATURES
                        screen == Screen.MEMORY_TOPIC -> screen = Screen.MEMORY_TOPICS
                        else -> screen = Screen.HOME
                    }
                }

                renameConv?.let { conv ->
                    RenameConversationDialog(
                        initial = renameConvText,
                        onDismiss = { renameConv = null },
                        onConfirm = { name ->
                            if (name.isNotEmpty()) {
                                convStore.upsert(conv.copy(title = name))
                                convs = convStore.loadAll()
                                if (chatVm.currentId == conv.id) chatVm.rename(name)
                            }
                            renameConv = null
                        },
                    )
                }
                deleteConv?.let { conv ->
                    DeleteConversationDialog(
                        title = conv.title,
                        onDismiss = { deleteConv = null },
                        onConfirm = {
                            convStore.delete(conv.id)
                            convs = convStore.loadAll()
                            // 删掉的就是当前打开的那条 → 先把状态清成新会话；
                            // 删的是别的会话就什么都不动（用户要的「非本页→不变」）。
                            if (chatVm.currentId == conv.id) {
                                chatVm.load(null)
                                // 正看着这条会话被删掉：回首页那张原始起始页（星芒 + 问候）。
                                // 不能把人留在一张空白对话页上（用户 2026-10-29 报）。
                                if (screen == Screen.CHAT) {
                                    pending = null
                                    chatFromList = false
                                    screen = Screen.HOME
                                }
                            }
                            deleteConv = null
                        },
                    )
                }
                addConv?.let { conv ->
                    AddConversationToProjectDialog(
                        projects = projects,
                        onDismiss = { addConv = null },
                        onPick = { proj ->
                            projectStore.addConversation(proj.id, conv.id)
                            projects = projectStore.loadAll()
                            addConv = null
                        },
                    )
                }

                AppDrawer(
                    open = drawerOpen,
                    conversations = convs,
                    avatarInitial = account.initial(),
                    avatarFile = AppDirs.avatar(ctx),
                    onClose = { drawerOpen = false },
                    onNewChat = {
                        drawerOpen = false
                        chatVm.load(null)
                        pending = null
                        chatFromList = false
                        screen = Screen.HOME
                    },
                    onOpenConversations = {
                        drawerOpen = false
                        screen = Screen.CONVERSATIONS
                    },
                    onOpenProjects = {
                        drawerOpen = false
                        projects = projectStore.loadAll()
                        screen = Screen.PROJECTS
                    },
                    onOpenArtifacts = {
                        drawerOpen = false
                        screen = Screen.ARTIFACTS
                    },
                    onOpenCode = {
                        drawerOpen = false
                        screen = Screen.CODE
                    },
                    onSettings = {
                        drawerOpen = false
                        screen = Screen.SETTINGS
                    },
                    onSelect = { conv ->
                        drawerOpen = false
                        chatVm.load(conv)
                        pending = null
                        chatFromList = false
                        screen = Screen.CHAT
                    },
                    onRenameConv = { conv ->
                        renameConvText = conv.title
                        renameConv = conv
                    },
                    onTogglePinConv = { conv ->
                        convStore.upsert(conv.copy(pinned = !conv.pinned))
                        convs = convStore.loadAll()
                        if (chatVm.currentId == conv.id) chatVm.togglePin()
                    },
                    onAddToProjectConv = { conv -> addConv = conv },
                    onDeleteConv = { conv -> deleteConv = conv },
                ) {
                    // 页面过渡（全局）：深度决定方向；进入轻微右滑+淡入，返回反向、更快。
                    // 位移只有页宽的 1/5 ~ 1/9，观感靠淡入淡出承担 —— 不做整页横推。
                    androidx.compose.animation.AnimatedContent(
                        targetState = screen,
                        transitionSpec = {
                            // ⚠️ 用「淡入淡出 + 极轻微缩放」而不是 slideIn/Out：
                            // slide 走的是 layout（offset），整页逐帧重新布局，第一次跑必卡；
                            // fade/scale 都在绘制阶段（graphicsLayer），几乎没有成本。
                            androidx.compose.animation.fadeIn(
                                androidx.compose.animation.core.tween(
                                    com.nairocy.claude.ui.motion.Motion.ENTER_MS / 2,
                                    easing = com.nairocy.claude.ui.motion.Motion.Standard,
                                )
                            ) + androidx.compose.animation.scaleIn(
                                initialScale = 0.985f,
                                animationSpec = androidx.compose.animation.core.tween(
                                    com.nairocy.claude.ui.motion.Motion.ENTER_MS,
                                    easing = com.nairocy.claude.ui.motion.Motion.Decel,
                                ),
                            ) togetherWith androidx.compose.animation.fadeOut(
                                androidx.compose.animation.core.tween(com.nairocy.claude.ui.motion.Motion.EXIT_MS)
                            ) + androidx.compose.animation.scaleOut(
                                targetScale = 1.012f,
                                animationSpec = androidx.compose.animation.core.tween(
                                    com.nairocy.claude.ui.motion.Motion.EXIT_MS,
                                    easing = com.nairocy.claude.ui.motion.Motion.Accel,
                                ),
                            )
                        },
                        label = "page",
                    ) { scr ->
                    when (scr) {
                        Screen.HOME -> HomeScreen(
                            onMenuClick = { drawerOpen = true },
                            onGhostClick = { screen = Screen.SETTINGS },
                            onSend = { text ->
                                // 首页 = 新对话起点：先把上一段会话切掉，否则这条会追加进旧会话
                                chatVm.startFreshIfStale()
                                pending = text
                                screen = Screen.CHAT
                            },
                            onPickImage = {
                                pickHomeImage.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onPickFile = { pickHomeFile.launch(arrayOf("*/*")) },
                            onCamera = { openCamera(true) },
                            onStartIncognito = {
                                chatVm.startIncognito()
                                pending = null
                                chatFromList = false
                                screen = Screen.CHAT
                            },
                            modelTierId = chatVm.modelTier,
                            onModelTierChange = { chatVm.updateModelTier(it) },
                            plan = plan,
                            onSeePlans = { screen = Screen.API_SETTINGS },
                            webSearch = chatVm.webSearch,
                            onWebSearchChange = { chatVm.updateWebSearch(it) },
                            memory = chatVm.memory,
                            onMemoryChange = { chatVm.updateMemory(it) },
                            greeting = greeting,
                        )

                        Screen.CHAT -> ChatScreen(
                            onBack = {
                                if (chatFromList) screen = Screen.CONVERSATIONS else screen = Screen.HOME
                            },
                            onMenuClick = { drawerOpen = true },
                            initialMessage = pending,
                            onConsumed = { pending = null },
                            onNewChat = {
                                chatVm.load(null)
                                pending = null
                                chatFromList = false
                                screen = Screen.HOME
                            },
                            onDeleted = {
                                convs = convStore.loadAll()
                                pending = null
                                screen = Screen.HOME
                            },
                            onPickImage = {
                                pickChatImage.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onPickFile = { pickChatFile.launch(arrayOf("*/*")) },
                            onCamera = { openCamera(false) },
                            projects = projects,
                            onAddToProject = { pid ->
                                projectStore.addConversation(pid, chatVm.currentId)
                                projects = projectStore.loadAll()
                            },
                            plan = plan,
                            onSeePlans = { screen = Screen.API_SETTINGS },
                            onRunArtifact = { lang, code, title ->
                                previewArtifact = ArtifactItem(
                                    id = "preview-" + System.currentTimeMillis(),
                                    title = title,
                                    lang = lang,
                                    code = code,
                                    convId = chatVm.currentId,
                                    convTitle = chatVm.title,
                                    updatedAt = System.currentTimeMillis(),
                                )
                                previewFrom = Screen.CHAT
                                screen = Screen.ARTIFACT_VIEW
                            },
                            vm = chatVm,
                        )

                        Screen.CONVERSATIONS -> ConversationsScreen(
                            conversations = convs,
                            onMenuClick = { drawerOpen = true },
                            onDelete = { ids ->
                                ids.forEach { convStore.delete(it) }
                                convs = convStore.loadAll()
                                // 批量删除里包含当前会话时，同样把对话页换成新的空会话
                                if (chatVm.currentId in ids) chatVm.load(null)
                            },
                            onOpen = { conv ->
                                chatVm.load(conv)
                                pending = null
                                chatFromList = true
                                screen = Screen.CHAT
                            },
                            onNewChat = {
                                chatVm.load(null)
                                pending = null
                                chatFromList = false
                                screen = Screen.HOME
                            },
                        )

                        Screen.PROJECTS -> ProjectsScreen(
                            projects = projects,
                            onMenuClick = { drawerOpen = true },
                            onOpen = { p ->
                                selectedProject = p
                                screen = Screen.PROJECT_DETAIL
                            },
                            onCreate = { nm ->
                                projectStore.create(nm)
                                projects = projectStore.loadAll()
                            },
                        )

                        Screen.PROJECT_DETAIL -> selectedProject?.let { sel ->
                            val latest = projects.firstOrNull { it.id == sel.id } ?: sel
                            ProjectDetailScreen(
                                project = latest,
                                conversations = convs,
                                onBack = { screen = Screen.PROJECTS },
                                onMenuClick = { drawerOpen = true },
                                onOpenConversation = { conv ->
                                    chatVm.load(conv)
                                    pending = null
                                    chatFromList = false
                                    screen = Screen.CHAT
                                },
                                onAdd = { ids ->
                                    ids.forEach { projectStore.addConversation(latest.id, it) }
                                    projects = projectStore.loadAll()
                                },
                                onRemove = { cid ->
                                    projectStore.removeConversation(latest.id, cid)
                                    projects = projectStore.loadAll()
                                },
                            )
                        }

                        Screen.ARTIFACTS -> ArtifactsScreen(
                            artifacts = artifacts,
                            onMenuClick = { drawerOpen = true },
                            onOpen = { item ->
                                selectedArtifact = item
                                screen = Screen.ARTIFACT_DETAIL
                            },
                        )

                        Screen.ARTIFACT_DETAIL -> selectedArtifact?.let { item ->
                            ArtifactDetailScreen(
                                item = item,
                                onBack = { screen = Screen.ARTIFACTS },
                                onMenuClick = { drawerOpen = true },
                                onRun = {
                                    previewArtifact = item
                                    previewFrom = Screen.ARTIFACT_DETAIL
                                    screen = Screen.ARTIFACT_VIEW
                                },
                            )
                        }

                        Screen.ARTIFACT_VIEW -> previewArtifact?.let { item ->
                            ArtifactViewScreen(
                                title = item.title,
                                lang = item.lang,
                                code = item.code,
                                onBack = { screen = previewFrom },
                            )
                        }

                        Screen.CODE -> CodeScreen(
                            vm = codeVm,
                            plan = plan,
                            onMenuClick = { drawerOpen = true },
                            onOpenPreview = { path ->
                                codePreviewPath = path
                                screen = Screen.CODE_PREVIEW
                            },
                            onSeePlans = { screen = Screen.API_SETTINGS },
                        )

                        Screen.CODE_PREVIEW -> CodePreviewScreen(
                            path = codePreviewPath,
                            wsId = codeVm.workspaceId,
                            onBack = { screen = Screen.CODE },
                        )

                        Screen.SETTINGS -> SettingsScreen(
                            onMenuClick = { drawerOpen = true },
                            email = account.email,
                            plan = plan,
                            showBadge = planReady,
                            haptic = haptic,
                            onHapticChange = { on ->
                                haptic = on
                                prefs.haptic = on
                                // 打开开关时立刻给一次反馈（关掉时自然没有）
                                if (on) com.nairocy.claude.data.Haptics.tap(ctx)
                            },
                            onOpenProfile = { screen = Screen.PROFILE },
                            themeMode = themeMode,
                            onThemeChange = { mode ->
                                themeMode = mode
                                prefs.themeMode = mode
                            },
                            fontMode = fontMode,
                            onFontModeChange = { mode ->
                                fontMode = mode
                                prefs.fontMode = mode
                            },
                            onOpenApiSettings = { screen = Screen.API_SETTINGS },
                            onOpenFeatures = { screen = Screen.FEATURES },
                            onOpenPermissions = { screen = Screen.PERMISSIONS },
                            onOpenNotifications = { screen = Screen.NOTIFICATIONS },
                            onOpenFocus = { screen = Screen.FOCUS },
                            onOpenPrivacy = { screen = Screen.PRIVACY },
                        )

                        Screen.PERMISSIONS -> PermissionsScreen(
                            onBack = { screen = Screen.SETTINGS },
                        )

                        Screen.NOTIFICATIONS -> NotificationsScreen(
                            onBack = { screen = Screen.SETTINGS },
                        )

                        Screen.FOCUS -> FocusScreen(
                            onBack = { screen = Screen.SETTINGS },
                        )

                        Screen.PRIVACY -> PrivacyScreen(
                            onBack = { screen = Screen.SETTINGS },
                        )

                        Screen.PROFILE -> ProfileScreen(
                            email = account.email,
                            initial = account.initial(),
                            avatarFile = AppDirs.avatar(ctx),
                            fullName = account.displayName,
                            preferredName = account.preferredName,
                            preferences = account.preferences,
                            hasCustomAvatar = account.avatarVer > 0,
                            saving = profileVm.saving,
                            error = profileVm.error,
                            onBack = { screen = Screen.SETTINGS },
                            onPickAvatar = {
                                profileVm.clearError()
                                pickAvatar.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onResetAvatar = { profileVm.resetAvatar(ctx, account.token, account) },
                            onSaveProfile = { name, nick ->
                                profileVm.saveProfile(account.token, account, name, nick)
                            },
                            onSavePreferences = { text ->
                                profileVm.savePreferences(account.token, account, text)
                            },
                            onDeleteAccount = {},
                            handoffUrl = null,
                        )

                        Screen.FEATURES -> FeaturesScreen(
                            onBack = { screen = Screen.SETTINGS },
                            onOpenMemoryFiles = { screen = Screen.MEMORY_TOPICS },
                        )

                        Screen.MEMORY_TOPICS -> MemoryTopicsScreen(
                            onBack = { screen = Screen.FEATURES },
                            onOpenTopic = { id ->
                                selectedMemoryTopic = id
                                screen = Screen.MEMORY_TOPIC
                            },
                        )

                        Screen.MEMORY_TOPIC -> selectedMemoryTopic?.let { id ->
                            MemoryTopicScreen(
                                topicId = id,
                                onBack = { screen = Screen.MEMORY_TOPICS },
                            )
                        }

                        Screen.API_SETTINGS -> ApiSettingsScreen(
                            onBack = { screen = Screen.SETTINGS },
                        )

                    }
                    }   // AnimatedContent 尾
                }
            }
            }
        }
    }

    /**
     * 前台时点通知：singleTop 复用这个 Activity，把目标会话交回 Compose。
     * 冷启动那一条在 onCreate 里读 extra。
     */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepConvId = intent.getStringExtra(com.nairocy.claude.data.ChatNotifications.EXTRA_CONV)
        deepCodeConvId = intent.getStringExtra(com.nairocy.claude.data.CodeNotifications.EXTRA_CODE_CONV)
    }
}

private fun openExternal(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
        )
    }
}

private enum class Screen {
    HOME, CHAT, CONVERSATIONS, PROJECTS, PROJECT_DETAIL, ARTIFACTS, ARTIFACT_DETAIL, ARTIFACT_VIEW, CODE, CODE_PREVIEW, SETTINGS, API_SETTINGS,
    FEATURES, MEMORY_TOPICS, MEMORY_TOPIC, PERMISSIONS, NOTIFICATIONS, FOCUS, PRIVACY, PROFILE,
}

/** 页面层级：只用来决定过渡方向（进 = 向前滑，退 = 反向滑）。 */
private fun pageDepth(screen: Screen): Int = when (screen) {
    Screen.HOME -> 0
    Screen.CHAT, Screen.CONVERSATIONS, Screen.PROJECTS, Screen.ARTIFACTS,
    Screen.CODE, Screen.SETTINGS -> 1
    Screen.PROJECT_DETAIL, Screen.ARTIFACT_DETAIL, Screen.ARTIFACT_VIEW, Screen.CODE_PREVIEW, Screen.API_SETTINGS,
    Screen.FEATURES, Screen.PERMISSIONS, Screen.NOTIFICATIONS, Screen.FOCUS,
    Screen.PRIVACY, Screen.PROFILE -> 2
    Screen.MEMORY_TOPICS -> 3
    Screen.MEMORY_TOPIC -> 4
}

