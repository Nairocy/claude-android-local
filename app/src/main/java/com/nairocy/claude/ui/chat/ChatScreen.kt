package com.nairocy.claude.ui.chat

import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.filterNotNull
import com.nairocy.claude.R
import com.nairocy.claude.data.ChatMessage
import com.nairocy.claude.data.Role
import com.nairocy.claude.ui.components.AttachSheet
import com.nairocy.claude.ui.components.Composer
import com.nairocy.claude.ui.components.MarkdownText
import com.nairocy.claude.ui.components.MessageActions
import com.nairocy.claude.ui.components.ContextMenuCard
import com.nairocy.claude.ui.components.ContextMenuDivider
import com.nairocy.claude.ui.components.ContextMenuRow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.nairocy.claude.ui.components.SparkAnimation
import com.nairocy.claude.ui.components.SparkKind
import com.nairocy.claude.ui.components.ThinkingRow
import com.nairocy.claude.ui.theme.AnthropicSans
import com.nairocy.claude.ui.theme.ClaudeTheme
import java.io.File

/** 对话页。对应截图 4。 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onMenuClick: () -> Unit = {},
    initialMessage: String? = null,
    onConsumed: () -> Unit = {},
    onNewChat: () -> Unit = {},
    onDeleted: () -> Unit = {},
    onPickImage: () -> Unit = {},
    onCamera: () -> Unit = {},
    onPickFile: () -> Unit = {},
    projects: List<com.nairocy.claude.data.Project> = emptyList(),
    onAddToProject: (String) -> Unit = {},
    /** 当前套餐：模型面板据此给 fable / opus 上锁。 */
    plan: com.nairocy.claude.data.Plan = com.nairocy.claude.data.Plan.FREE,
    /** 点「查看方案」→ 账单页。 */
    onSeePlans: () -> Unit = {},
    /** artifact 卡片上的运行按钮：交给 WebView 渲染页。 */
    onRunArtifact: (lang: String, code: String, title: String) -> Unit = { _, _, _ -> },
    vm: ChatViewModel = viewModel(),
) {
    val colors = ClaudeTheme.colors
    // 在 imePadding 之外读，才拿得到真实的键盘可见性
    val keyboardVisible = WindowInsets.ime
        .getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    val listState = rememberLazyListState()
    // 浮升的「已显示到第几个字」按消息+块存在这里：列表回收条目再回来时不重播。
    // ⚠️ 用进程级单例（不是 remember）：切页面 / Activity 重建后回来也要接着显示，
    // 否则正在生成的这条会从头重放，看起来像「结果丢了」。
    val riseStore = com.nairocy.claude.ui.components.AppRiseRevealStore
    val clipboard = LocalClipboardManager.current
    var input by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var attachOpen by remember { mutableStateOf(false) }
    var projectPickerOpen by remember { mutableStateOf(false) }
    var menuMsg by remember { mutableStateOf<Int?>(null) }
    var modelSheet by remember { mutableStateOf(false) }
    val tier = com.nairocy.claude.ui.components.modelTierById(
        com.nairocy.claude.ui.components.effectiveTierId(vm.modelTier, plan)
    )
    var selectMsg by remember { mutableStateOf<String?>(null) }
    // 赞/踩的装饰态：ts -> 1 / -1。只在本页面存活（不上报服务端），但列表回收后回来不会丢。
    val votes = remember { androidx.compose.runtime.mutableStateMapOf<Long, Int>() }
    val editFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    // 任何弹层/菜单打开时都收回输入焦点，键盘不要盖在上面
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    androidx.compose.runtime.LaunchedEffect(menuOpen, attachOpen, projectPickerOpen, renameOpen, deleteOpen) {
        if (menuOpen || attachOpen || projectPickerOpen || renameOpen || deleteOpen) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    val ctx = LocalContext.current
    val density = LocalDensity.current
    // 图片在账号目录下（AppDirs.images = files/users/<uid>/images）；
    // 这里绝不能再写 File(filesDir, "images")，那是账号隔离改造前的旧路径 ——
    // 会让「模型能收到图、界面却一片空白」。
    val imageDir = remember { com.nairocy.claude.data.AppDirs.images(ctx) }
    var viewerPath by remember { mutableStateOf<String?>(null) }
    val menuOffset = with(density) { IntOffset(x = 0.dp.roundToPx(), y = 42.dp.roundToPx()) }

    // 顶栏「文档」与 ⋯ 菜单里的「导出对话」共用：把整段对话交给系统分享面板
    fun shareConversation() {
        if (vm.messages.isEmpty()) {
            Toast.makeText(ctx, "当前对话还没有内容", Toast.LENGTH_SHORT).show()
            return
        }
        val body = buildString {
            append(vm.title).append("\n\n")
            vm.messages.forEach { m ->
                append(if (m.role == Role.USER) "我：" else "Claude：")
                append(m.content).append("\n\n")
            }
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, vm.title)
            putExtra(Intent.EXTRA_TEXT, body.trim())
        }
        ctx.startActivity(Intent.createChooser(send, "分享对话"))
    }

    LaunchedEffect(initialMessage) {
        if (!initialMessage.isNullOrBlank()) {
            vm.send(initialMessage)
            onConsumed()
        }
    }

    // 流式跟随：默认贴底跟随；用户主动上滑就停；用户再滑回底部自动恢复。
    // ⚠️ 不能用「当前是否在底部」直接决定 followBottom —— 文本增长本身会把最后一条
    // 撑出视口，那一瞬间 atBottom 变 false，会被误判成「用户上滑」而永久停跟随。
    // 只有「正在拖动/惯性滚动」时才允许把跟随关掉。
    var followBottom by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            val atBottom = last == null || (
                last.index >= info.totalItemsCount - 1 &&
                    last.offset + last.size <= info.viewportEndOffset + 24
                )
            listState.isScrollInProgress to atBottom
        }.collect { (scrolling, atBottom) ->
            when {
                // 用户手势滚动到/留在底部：恢复跟随
                scrolling && atBottom -> followBottom = true
                // 用户手势离开底部：停止跟随（内容自己长高不算）
                scrolling && !atBottom -> followBottom = false
                // 没有手势时只在「到底」时恢复，离底保持原状（可能只是文本长高了）
                !scrolling && atBottom -> followBottom = true
            }
        }
    }
    // 消息条数变化（发出新消息 / 进入会话）或换了会话：强制回到底部。
    // 换会话必须带上 currentId —— 两条会话消息条数一样时，只靠 size 不会重新触发，
    // 视口会停在上一段会话的滚动位置。
    LaunchedEffect(vm.messages.size, vm.currentId) {
        if (vm.messages.isNotEmpty()) {
            followBottom = true
            listState.scrollToItem(vm.messages.lastIndex, 1_000_000)
        }
    }
    // 同一条回复在流式增长：仅在跟随状态且用户没在拖时贴底
    LaunchedEffect(vm.messages.lastOrNull()?.content) {
        if (followBottom && vm.messages.isNotEmpty() && !listState.isScrollInProgress) {
            listState.scrollToItem(vm.messages.lastIndex, 1_000_000)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .systemBarsPadding()
            .imePadding()
    ) {
        // 顶栏
        if (vm.incognito) {
            // 无痕页（对齐官方截图）：上排 汉堡 / X，标题单独一行居中
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                ) {
                    IconTap(onClick = onMenuClick, modifier = Modifier.align(Alignment.CenterStart)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_menu),
                            contentDescription = "菜单",
                            tint = colors.onSurface,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    IconTap(
                        onClick = {
                            vm.exitIncognito()
                            onBack()
                        },
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = "退出无痕对话",
                            tint = colors.onSurface,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Text(
                    text = "无痕对话",
                    fontFamily = AnthropicSans,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    fontSize = 19.sp,
                    color = colors.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            ) {
                IconTap(onClick = onMenuClick, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_menu),
                        contentDescription = "菜单",
                        tint = colors.onSurface,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    text = vm.title,
                    fontFamily = AnthropicSans,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    fontSize = 19.sp,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 96.dp),
                )
                Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                    IconTap(onClick = onNewChat) {
                        NewChatGlyph(bubble = colors.onSurface, plus = colors.surface)
                    }
                    Box {
                        IconTap(onClick = { menuOpen = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_more),
                                contentDescription = "更多",
                                tint = colors.onSurface,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        if (menuOpen) {
                            Popup(
                                alignment = Alignment.TopEnd,
                                offset = menuOffset,
                                onDismissRequest = { menuOpen = false },
                                properties = PopupProperties(focusable = true),
                            ) {
                                ChatMenuCard(
                                    title = vm.title,
                                    pinned = vm.pinned,
                                    onShare = {
                                        menuOpen = false
                                        shareConversation()
                                    },
                                    onRename = {
                                        menuOpen = false
                                        renameText = vm.title
                                        renameOpen = true
                                    },
                                    onTogglePin = {
                                        menuOpen = false
                                        vm.togglePin()
                                    },
                                    onAddToProject = {
                                        menuOpen = false
                                        projectPickerOpen = true
                                    },
                                    onDelete = {
                                        menuOpen = false
                                        deleteOpen = true
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (vm.incognito && vm.messages.isEmpty()) {
            // 无痕空态（对齐官方截图）：魂灵 + 说明 + 了解更多，垂直居中
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_ghost_solid),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(22.dp))
                Text(
                    text = "无痕对话不会出现在您的历史记录和搜索中，也不会用于训练 Claude。",
                    fontFamily = AnthropicSans,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = colors.secondaryText,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "了解更多",
                    fontFamily = AnthropicSans,
                    fontSize = 14.sp,
                    color = colors.secondaryText,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        runCatching {
                            ctx.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(
                                        "https://privacy.claude.com/zh-CN/articles/10023580-%E6%88%91%E7%9A%84%E6%95%B0%E6%8D%AE%E6%98%AF%E5%90%A6%E7%94%A8%E4%BA%8E%E6%A8%A1%E5%9E%8B%E8%AE%AD%E7%BB%83"
                                    )
                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                )
            }
        } else {
        // 消息流
        androidx.compose.runtime.CompositionLocalProvider(
            com.nairocy.claude.ui.components.LocalRiseRevealStore provides riseStore
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // ⚠️ 必须给稳定 key + 关掉「消失淡出」：
            // 以前没有 key，LazyColumn 按**下标**认条目；在侧边栏切换会话时列表整体换掉，
            // 旧会话的条目还在做淡出动画、新会话的同下标条目已经画上来了 → 两段文字叠在一起。
            // key 用「下标 + ts」：同一会话内保持稳定（增删消息仍有位移动画），换会话时全变 → 旧条目直接撤掉。
            itemsIndexed(
                items = vm.messages,
                key = { index, m -> "$index:${m.ts}" },
            ) { index, m ->
                Box(Modifier.animateItem(fadeOutSpec = null)) {
                MessageRow(
                    m = m,
                    isLastAssistant = index == vm.messages.lastIndex && m.role == Role.ASSISTANT,
                    streaming = vm.streaming,
                    onRunArtifact = onRunArtifact,
                    thinkingHint = if (index == vm.messages.lastIndex) vm.toolHint else null,
                    onOpenImage = { path -> viewerPath = path },
                    menuOpen = menuMsg == index,
                    onLongPress = { if (m.role == Role.USER) menuMsg = index },
                    onDismissMenu = { menuMsg = null },
                    onCopyMessage = {
                        clipboard.setText(AnnotatedString(m.content))
                        menuMsg = null
                    },
                    onSelectText = {
                        selectMsg = m.content
                        menuMsg = null
                    },
                    onEdit = {
                        menuMsg = null
                        vm.beginEdit(index)
                        input = m.content
                        editFocus.requestFocus()
                    },
                    onCopy = {
                        clipboard.setText(AnnotatedString(m.content))
                    },
                    onRegenerate = { vm.regenerate() },
                    vote = votes[m.ts] ?: 0,
                    onVote = { votes[m.ts] = it },
                )
                }
            }
            if (vm.messages.isNotEmpty() && !vm.streaming) {
                item {
                    Text(
                        text = "Claude 是 AI，可能会出错。",
                        fontFamily = AnthropicSans,
                        fontSize = 12.sp,
                        color = colors.tertiaryText,
                        modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                    )
                }
            }
        }
        }
        }

        // 输入条
        if (vm.editingIndex != null) {
            Text(
                text = "编辑此消息将从此处重新开始对话。",
                fontFamily = AnthropicSans,
                fontSize = 13.sp,
                color = colors.tertiaryText,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 8.dp),
            )
        }
        Composer(
            value = input,
            onValueChange = { input = it },
            onSend = {
                if (input.isNotBlank()) {
                    if (vm.editingIndex != null) vm.sendEdited(input) else vm.send(input)
                    input = ""
                    focusManager.clearFocus(force = true)
                    keyboard?.hide()
                }
            },
            placeholder = if (vm.incognito) "与 Claude 对话..." else "回复 Claude...",
            dashedBorder = vm.incognito,
            images = vm.pendingImages,
            attachments = vm.pendingAttachments,
            imageDir = imageDir,
            onPickImage = { attachOpen = true },
            onRemoveImage = { vm.removePendingImage(it) },
            onRemoveAttachment = { vm.removePendingAttachment(it) },
            streaming = vm.streaming,
            onStop = { vm.stop() },
            keyboardVisible = keyboardVisible,
            modelLabel = com.nairocy.claude.ui.components.localModelLabel(),
            onModelClick = {
                // 一步到位：先自己收键盘（与面板升起并行），别等系统在弹出窗口里把输入法掐掉，
                // 那样看起来像「先弹掉输入法、再唤起模型页」，中间空一拍。
                focusManager.clearFocus(force = true)
                keyboard?.hide()
                modelSheet = true
            },
            editing = vm.editingIndex != null,
            onCancelEdit = {
                vm.cancelEdit()
                input = ""
            },
            focusRequester = editFocus,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 18.dp),
        )
    }

    if (projectPickerOpen) {
        AlertDialog(
            onDismissRequest = { projectPickerOpen = false },
            containerColor = colors.card,
            titleContentColor = colors.onSurface,
            title = { Text("添加到项目", fontFamily = AnthropicSans, fontSize = 17.sp) },
            text = {
                if (projects.isEmpty()) {
                    Text(
                        "还没有项目。去抽屉 → 项目 里新建一个。",
                        fontFamily = AnthropicSans,
                        fontSize = 14.sp,
                        color = colors.tertiaryText,
                    )
                } else {
                    Column {
                        projects.forEach { p ->
                            Text(
                                text = p.name,
                                fontFamily = AnthropicSans,
                                fontSize = 15.sp,
                                color = colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onAddToProject(p.id)
                                        projectPickerOpen = false
                                    }
                                    .padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { projectPickerOpen = false }) {
                    Text("关闭", fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.brand)
                }
            },
        )
    }

    viewerPath?.let { path ->
        com.nairocy.claude.ui.components.ImageViewerDialog(
            path = path,
            onDismiss = { viewerPath = null },
        )
    }

    AttachSheet(
        visible = attachOpen,
        projectLabel = projects.firstOrNull { vm.currentId in it.conversationIds }?.name ?: "无",
        onAddToProject = {
            attachOpen = false
            projectPickerOpen = true
        },
        webSearch = vm.webSearch,
        onWebSearchChange = { vm.updateWebSearch(it) },
        memory = vm.memory,
        onMemoryChange = { vm.updateMemory(it) },
        onDismiss = { attachOpen = false },
        onCamera = {
            attachOpen = false
            onCamera()
        },
        onPhoto = {
            attachOpen = false
            onPickImage()
        },
        onFile = {
            attachOpen = false
            onPickFile()
        },
    )

    com.nairocy.claude.ui.components.ModelSheet(
        visible = modelSheet,
        selectedId = com.nairocy.claude.ui.components.effectiveTierId(vm.modelTier, plan),
        currentPlan = plan,
        onSeePlans = {
            modelSheet = false
            onSeePlans()
        },
        onSelect = {
            vm.updateModelTier(it.id)
            modelSheet = false
        },
        onDismiss = { modelSheet = false },
        onLearnMore = {
            runCatching {
                ctx.startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(com.nairocy.claude.ui.components.FABLE_INFO_URL)
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        },
    )

    selectMsg?.let { txt ->
        AlertDialog(
            onDismissRequest = { selectMsg = null },
            containerColor = colors.card,
            titleContentColor = colors.onSurface,
            title = { Text("选择文本", fontFamily = AnthropicSans, fontSize = 17.sp) },
            text = {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        text = txt,
                        fontFamily = AnthropicSans,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = colors.onSurface,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectMsg = null }) {
                    Text("完成", fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.brand)
                }
            },
        )
    }

    if (renameOpen) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            containerColor = colors.card,
            titleContentColor = colors.onSurface,
            textContentColor = colors.secondaryText,
            title = { Text("重命名对话", fontFamily = AnthropicSans, fontSize = 17.sp) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    BasicTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = AnthropicSans,
                            fontSize = 15.sp,
                            color = colors.onSurface,
                        ),
                        cursorBrush = SolidColor(colors.brand),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.rename(renameText)
                    renameOpen = false
                }) {
                    Text("保存", fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.brand)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameOpen = false }) {
                    Text("取消", fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.tertiaryText)
                }
            },
        )
    }

    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            containerColor = colors.card,
            titleContentColor = colors.onSurface,
            textContentColor = colors.secondaryText,
            title = { Text("删除这段对话？", fontFamily = AnthropicSans, fontSize = 17.sp) },
            text = { Text("删除后无法恢复。", fontFamily = AnthropicSans, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    deleteOpen = false
                    vm.deleteCurrent()
                    onDeleted()
                }) {
                    Text("删除", fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteOpen = false }) {
                    Text("取消", fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.tertiaryText)
                }
            },
        )
    }
}

/** 长按用户消息的菜单：时间戳 + 复制 / 选择文本 / 编辑（宽度贴合内容）。 */
@Composable
private fun MessageMenuCard(
    ts: Long,
    onCopy: () -> Unit,
    onSelectText: () -> Unit,
    onEdit: () -> Unit,
) {
    val colors = ClaudeTheme.colors
    val time = remember(ts) {
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA).format(java.util.Date(ts))
    }
    ContextMenuCard {
        Text(
            text = time,
            fontFamily = AnthropicSans,
            fontSize = 12.sp,
            color = colors.tertiaryText,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
        ContextMenuDivider()
        ContextMenuRow(R.drawable.cds_icon_copy, "复制消息", onClick = onCopy)
        ContextMenuRow(R.drawable.ic_select_text, "选择文本", onClick = onSelectText)
        ContextMenuRow(R.drawable.ic_edit, "编辑", onClick = onEdit)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    m: ChatMessage,
    isLastAssistant: Boolean,
    streaming: Boolean,
    onRunArtifact: (lang: String, code: String, title: String) -> Unit = { _, _, _ -> },
    thinkingHint: String? = null,
    onOpenImage: (path: String) -> Unit = {},
    menuOpen: Boolean = false,
    onLongPress: () -> Unit = {},
    onDismissMenu: () -> Unit = {},
    onCopyMessage: () -> Unit = {},
    onSelectText: () -> Unit = {},
    onEdit: () -> Unit = {},
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    vote: Int = 0,
    onVote: (Int) -> Unit = {},
) {
    val colors = ClaudeTheme.colors
    // 真正「正在写的」只有最后一条助手消息：其余消息不参与任何流式动画
    val streamThis = streaming && isLastAssistant
    if (m.role == Role.USER) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Box {
            Column(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.userBubble)
                    .combinedClickable(onClick = {}, onLongClick = onLongPress)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (m.images.isNotEmpty()) {
                    val ctx = LocalContext.current
                    // 账号隔离后的真实目录：files/users/<uid>/images（旧写法 filesDir/images 读不到）
                    val imageDir = remember(ctx) { com.nairocy.claude.data.AppDirs.images(ctx) }
                    m.images.forEach { name ->
                        val path = remember(imageDir, name) { File(imageDir, name).absolutePath }
                        val bmp = remember(path) {
                            BitmapFactory.decodeFile(path)?.asImageBitmap()
                        }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp,
                                contentDescription = "查看大图",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .padding(bottom = 6.dp)
                                    .widthIn(max = 240.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onOpenImage(path) },
                            )
                        }
                    }
                }
                m.attachments.forEach { att ->
                    Row(
                        modifier = Modifier.padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_paperclip),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(colors.tertiaryText),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = att.name,
                            fontFamily = AnthropicSans,
                            fontSize = 13.sp,
                            color = colors.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 200.dp),
                        )
                    }
                }
                if (m.content.isNotBlank()) {
                    Text(
                        text = m.content,
                        fontFamily = AnthropicSans,
                        fontSize = 16.sp,
                        lineHeight = 25.sp,
                        color = colors.onSurface,
                    )
                }
            }
                if (menuOpen) {
                    val dy = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.roundToPx() }
                    Popup(
                        popupPositionProvider = com.nairocy.claude.ui.components.AnchoredPopupPosition(dy, alignRight = true),
                        onDismissRequest = onDismissMenu,
                        properties = PopupProperties(focusable = true),
                    ) {
                        MessageMenuCard(
                            ts = m.ts,
                            onCopy = onCopyMessage,
                            onSelectText = onSelectText,
                            onEdit = onEdit,
                        )
                    }
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (m.content.isBlank() && streaming) {
                ThinkingRow(overrideText = thinkingHint)
            } else {
                MarkdownText(
                    text = m.content,
                    onRunArtifact = onRunArtifact,
                    streaming = streamThis,
                    // 浮升只给「正在写的这一条」：历史消息、别的消息一律不动画。
                    // 逐字上屏与代码块直出都在 MarkdownText / RisingInlineText 里。
                    rise = streamThis,
                    riseKey = m.ts,
                )
                // 搜索 / 抓取进行中：已经有正文也要显示「搜索以太网... / 抓取页面...」
                if (streaming && thinkingHint != null) {
                    Spacer(Modifier.height(10.dp))
                    ThinkingRow(overrideText = thinkingHint)
                }
            }
            if (m.error != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "⚠ ${m.error}",
                    fontFamily = AnthropicSans,
                    fontSize = 12.sp,
                    color = colors.error,
                )
            }
            // 回复写完才浮出操作条：淡入 + 上移 6dp，别在结尾"啪"一下出现
            androidx.compose.animation.AnimatedVisibility(
                visible = isLastAssistant && m.content.isNotBlank() && !streaming && m.error == null,
                enter = androidx.compose.animation.fadeIn(
                    androidx.compose.animation.core.tween(
                        com.nairocy.claude.ui.motion.Motion.ENTER_MS,
                        easing = com.nairocy.claude.ui.motion.Motion.Standard,
                    )
                ) + androidx.compose.animation.slideInVertically(
                    animationSpec = androidx.compose.animation.core.tween(
                        com.nairocy.claude.ui.motion.Motion.ENTER_MS,
                        easing = com.nairocy.claude.ui.motion.Motion.Decel,
                    ),
                    initialOffsetY = { it / 3 },
                ),
                exit = androidx.compose.animation.fadeOut(
                    androidx.compose.animation.core.tween(com.nairocy.claude.ui.motion.Motion.EXIT_MS)
                ),
            ) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    MessageActions(
                        onCopy = onCopy,
                        onRegenerate = onRegenerate,
                        vote = vote,
                        onVote = onVote,
                    )
                }
            }
        }
    }
}

/** 左上「新建对话」：实心气泡 + 镂空加号，颜色随主题反转。 */
@Composable
private fun NewChatGlyph(bubble: Color, plus: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val r = w * 0.40f
        val cx = w * 0.5f
        val cy = h * 0.46f
        val path = Path().apply {
            addOval(Rect(center = Offset(cx, cy), radius = r))
            moveTo(cx - r * 0.58f, cy + r * 0.78f)
            lineTo(cx - r * 1.00f, cy + r * 1.28f)
            lineTo(cx - r * 0.10f, cy + r * 0.98f)
            close()
        }
        drawPath(path, bubble)
        val pr = r * 0.44f
        val sw = r * 0.18f
        drawLine(plus, Offset(cx - pr, cy), Offset(cx + pr, cy), sw, StrokeCap.Round)
        drawLine(plus, Offset(cx, cy - pr), Offset(cx, cy + pr), sw, StrokeCap.Round)
    }
}

/**
 * 顶栏 ⋯ 卡片菜单：会话标题 + 五项，样式对齐原版。
 *
 * ⚠️ 宽度由**菜单行**决定，不由标题决定（用户 2026-10-29 要求）：
 * 以前 Column 跟着最宽的孩子走，标题一长整张卡就被撑开，菜单大小不统一。
 * 现在按最长的那行按钮（图标 + 间距 + 文字 + 内边距）算出统一宽度，标题在这宽度里省略号截断。
 */
@Composable
internal fun ChatMenuCard(
    title: String,
    pinned: Boolean,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onAddToProject: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = ClaudeTheme.colors
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    // ⚠️ AnthropicSans 是 @Composable get()（字体样式切换会换字体），
    // 不能直接在 remember 的 lambda 里取，先在这里拿好再传进去。
    val sans = AnthropicSans
    // 行内文字 + 左右内边距(14×2) + 图标 20 + 间距 12 = 60dp，右边再留 16dp 余量
    val width = remember(density, sans) {
        val rowStyle = androidx.compose.ui.text.TextStyle(
            fontFamily = sans,
            fontSize = 15.sp,
        )
        val labelPx = listOf("分享", "重命名", "置顶", "取消置顶", "添加到项目", "删除")
            .maxOf { measurer.measure(it, rowStyle).size.width.toFloat() }
        with(density) { (labelPx + 76.dp.toPx()).toDp() }.coerceAtLeast(168.dp)
    }
    Column(
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = title,
            fontFamily = AnthropicSans,
            fontSize = 15.sp,
            color = colors.tertiaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 9.dp),
        )
        ChatMenuRow(R.drawable.ic_share, "分享", onClick = onShare)
        ChatMenuRow(R.drawable.ic_edit, "重命名", onClick = onRename)
        ChatMenuRow(R.drawable.ic_pin, if (pinned) "取消置顶" else "置顶", onClick = onTogglePin)
        ChatMenuRow(R.drawable.ic_project, "添加到项目", onClick = onAddToProject)
        ChatMenuRow(R.drawable.ic_trash, "删除", danger = true, onClick = onDelete)
    }
}

@Composable
private fun ChatMenuRow(
    icon: Int,
    label: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit = {},
) {
    val colors = ClaudeTheme.colors
    val iconTint = when {
        danger -> colors.error
        !enabled -> colors.tertiaryText.copy(alpha = 0.45f)
        else -> colors.tertiaryText
    }
    val textColor = when {
        danger -> colors.error
        !enabled -> colors.tertiaryText.copy(alpha = 0.55f)
        else -> colors.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(label, fontFamily = AnthropicSans, fontSize = 15.sp, color = textColor)
    }
}

@Composable
private fun IconTap(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
