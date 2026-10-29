package com.nairocy.claude.ui.code

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.nairocy.claude.R
import com.nairocy.claude.data.CodeConversation
import com.nairocy.claude.data.CodeWorkspace
import com.nairocy.claude.data.CodeWorkspaceMeta
import com.nairocy.claude.data.CodeWorkspaceStore
import com.nairocy.claude.data.WorkspaceExport
import com.nairocy.claude.data.Plan
import com.nairocy.claude.data.WorkspaceFile
import com.nairocy.claude.data.textOf
import com.nairocy.claude.ui.components.Composer
import com.nairocy.claude.ui.components.ContextMenuCard
import com.nairocy.claude.ui.components.ContextMenuDivider
import com.nairocy.claude.ui.components.ContextMenuRow
import com.nairocy.claude.ui.components.ModelSheet
import com.nairocy.claude.ui.components.effectiveTierId
import com.nairocy.claude.ui.components.modelTierById
import com.nairocy.claude.ui.theme.AnthropicSans
import com.nairocy.claude.ui.theme.ClaudeTheme
import com.nairocy.claude.ui.theme.JetBrainsMono

/**
 * Code 页（v0.17）：手机上写静态网页。
 *
 * 与对话页的区别：
 * - 工作区在手机里（`files/users/<uid>/workspace/`），模型用 ls/read/write/edit/grep 改它；
 * - 助手消息按**顺序**存块，工具卡（带 diff）夹在正文之间；
 * - 顶栏下面多一条「工作区 · N 个文件」，右侧「文件 / 预览」两个入口。
 *
 * 不做的：附件、图片、无痕、shell（Android 里没有 node/python，跑不了构建）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeScreen(
    vm: CodeViewModel,
    onMenuClick: () -> Unit = {},
    /** 打开预览页：path 以 / 开头。 */
    onOpenPreview: (String) -> Unit = {},
    /** 点「查看方案」→ 账单页。 */
    onSeePlans: () -> Unit = {},
    plan: Plan = Plan.FREE,
) {
    val colors = ClaudeTheme.colors
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    // 在 imePadding 之外读，才拿得到真实的键盘可见性（两行态只跟输入法走，不跟焦点）
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0

    var input by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    var filesOpen by remember { mutableStateOf(false) }
    var modelSheet by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var deleteOpen by remember { mutableStateOf(false) }
    var wsSheetOpen by remember { mutableStateOf(false) }
    var wsCreateOpen by remember { mutableStateOf(false) }
    var wsRenameTarget by remember { mutableStateOf<com.nairocy.claude.data.CodeWorkspaceMeta?>(null) }
    var wsRenameText by remember { mutableStateOf("") }
    var wsDeleteTarget by remember { mutableStateOf<com.nairocy.claude.data.CodeWorkspaceMeta?>(null) }
    var historyEdit by remember { mutableStateOf<CodeConversation?>(null) }
    var historyEditText by remember { mutableStateOf("") }
    var historyDelete by remember { mutableStateOf<CodeConversation?>(null) }
    val listState = rememberLazyListState()
    val tier = modelTierById(effectiveTierId(vm.modelTier, plan))
    val menuOffset = with(density) { IntOffset(x = 0, y = 42.dp.roundToPx()) }

    // 任何弹层打开时都收回输入焦点，键盘不要盖在上面
    LaunchedEffect(menuOpen, historyOpen, filesOpen, modelSheet, renameOpen, deleteOpen, wsSheetOpen, wsCreateOpen, wsRenameTarget, wsDeleteTarget, historyEdit, historyDelete) {
        if (menuOpen || historyOpen || filesOpen || modelSheet || renameOpen || deleteOpen ||
            wsSheetOpen || wsCreateOpen || wsRenameTarget != null || wsDeleteTarget != null ||
                historyEdit != null || historyDelete != null
        ) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || vm.streaming) return
        vm.send(t)
        input = ""
        focusManager.clearFocus(force = true)
        keyboard?.hide()
    }

    /** 预览入口：默认 /index.html；没有就挑第一个可预览的页；一个都没有就提示。 */
    fun openPreviewDefault() {
        vm.refreshFiles()
        val list = vm.files
        val target = list.firstOrNull { it.path == "/index.html" }?.path
            ?: list.firstOrNull { CodeWorkspace.isPreviewable(it.path) }?.path
        if (target == null) {
            Toast.makeText(ctx, "工作区还没有网页，让 Claude 写一个吧", Toast.LENGTH_SHORT).show()
        } else {
            onOpenPreview(target)
        }
    }

    // 进页面就刷一次：文件数/会话列表可能在别的页面里被改过（模型上一轮改过文件、
    // 或者在对话页删过东西），别拿登录那一刻的快照糊弄。
    LaunchedEffect(Unit) {
        vm.refreshFiles()
        vm.refreshHistory()
    }

    // 流式跟随：与对话页同一套规则（只有手势滚动才改跟随状态，静止时贴底才恢复）
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
                scrolling && atBottom -> followBottom = true
                scrolling && !atBottom -> followBottom = false
                !scrolling && atBottom -> followBottom = true
            }
        }
    }
    LaunchedEffect(vm.messages.size, vm.currentId) {
        if (vm.messages.isNotEmpty()) {
            followBottom = true
            listState.scrollToItem(vm.messages.lastIndex, 1_000_000)
        }
    }
    // 卡片内容（diff 逐行出现、输出展开）也会改 messages —— key 用整条列表，页面跟着往下滚
    LaunchedEffect(vm.messages) {
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
        // ── 顶栏：汉堡 / 标题 / ＋ / ⋯ ──
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
                text = if (vm.messages.isEmpty()) "Code" else vm.title,
                fontFamily = AnthropicSans,
                fontWeight = FontWeight.Medium,
                fontSize = 19.sp,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 96.dp),
            )
            Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                IconTap(onClick = {
                    vm.newConversation()
                    input = ""
                    focusManager.clearFocus(force = true)
                    keyboard?.hide()
                }) {
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
                            ContextMenuCard(minWidth = 168, maxWidth = 220) {
                                Text(
                                    text = vm.title,
                                    fontFamily = AnthropicSans,
                                    fontSize = 13.sp,
                                    color = colors.tertiaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                                ContextMenuDivider()
                                ContextMenuRow(R.drawable.cds_icon_chats, "历史会话") {
                                    menuOpen = false
                                    vm.refreshHistory()
                                    historyOpen = true
                                }
                                ContextMenuRow(R.drawable.ic_edit, "重命名") {
                                    menuOpen = false
                                    renameText = vm.title
                                    renameOpen = true
                                }
                                ContextMenuRow(R.drawable.ic_trash, "删除", danger = true) {
                                    menuOpen = false
                                    deleteOpen = true
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 工作区条：文件数 + 「文件 / 预览」 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clickable {
                        vm.refreshHistory()
                        wsSheetOpen = true
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.cds_icon_file),
                    contentDescription = null,
                    tint = colors.tertiaryText,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = vm.workspaceName,
                    fontFamily = AnthropicSans,
                    fontSize = 13.sp,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "· ${vm.files.size} 个文件",
                    fontFamily = AnthropicSans,
                    fontSize = 13.sp,
                    color = colors.tertiaryText,
                    maxLines = 1,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_down),
                    contentDescription = "切换工作区",
                    tint = colors.tertiaryText,
                    modifier = Modifier.size(12.dp),
                )
            }
            WorkspaceAction("文件") {
                vm.refreshFiles()
                filesOpen = true
            }
            Spacer(Modifier.width(10.dp))
            WorkspaceAction("预览") { openPreviewDefault() }
        }

        // ── 消息区 ──
        Box(modifier = Modifier.weight(1f)) {
            if (vm.messages.isEmpty()) {
                CodeEmptyState()
            } else {
                CodeMessageList(
                    messages = vm.messages,
                    streaming = vm.streaming,
                    toolHint = vm.toolHint,
                    currentId = vm.currentId,
                    listState = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 12.dp),
                )
            }
        }

        // ── 输入条（没有「+」：Code 页不带附件） ──
        Composer(
            value = input,
            onValueChange = { input = it },
            onSend = { send(input) },
            placeholder = "描述你想做的页面…",
            streaming = vm.streaming,
            onStop = { vm.stop() },
            keyboardVisible = keyboardVisible,
            modelLabel = com.nairocy.claude.ui.components.localModelLabel(),
            onModelClick = {
                focusManager.clearFocus(force = true)
                keyboard?.hide()
                modelSheet = true
            },
            showPlus = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }

    // ── 模型面板（与对话页同一套，含套餐锁） ──
    ModelSheet(
        visible = modelSheet,
        selectedId = effectiveTierId(vm.modelTier, plan),
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

    // ── 工作区面板（创建 / 切换 / 改名 / 删除） ──
    if (wsSheetOpen) {
        CodeSheetScaffold(
            title = "工作区",
            onClose = { wsSheetOpen = false },
            action = {
                WorkspaceAction("新建") {
                    wsSheetOpen = false
                    wsCreateOpen = true
                }
            },
        ) {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                vm.workspaces.forEach { ws ->
                    WorkspaceRow(
                        ws = ws,
                        current = ws.id == vm.workspaceId,
                        sessions = runCatching { vm.historyGroups().firstOrNull { it.first.id == ws.id }?.second?.size }.getOrNull() ?: 0,
                        onClick = {
                            wsSheetOpen = false
                            vm.switchWorkspace(ws.id)
                        },
                        onLongClick = {
                            wsRenameTarget = ws
                            wsRenameText = ws.name
                        },
                    )
                }
                Text(
                    text = "长按工作区可重命名或删除。每个工作区的文件完全独立。",
                    fontFamily = AnthropicSans,
                    fontSize = 12.sp,
                    color = colors.tertiaryText,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
        }
    }

    // ── 历史会话（按 工作区 → 时间 分组） ──
    if (historyOpen) {
        CodeSheetScaffold(title = "Code 会话", onClose = { historyOpen = false }) {
            val groups = remember(historyOpen, vm.workspaces, vm.currentId) { vm.historyGroups() }
            if (groups.sumOf { it.second.size } == 0) {
                SheetEmpty("还没有会话")
            } else {
                LazyColumn(Modifier.fillMaxWidth().height(400.dp)) {
                    groups.forEach { (ws, convs) ->
                        item(key = "ws-${ws.id}") {
                            WorkspaceGroupHeader(
                                name = ws.name,
                                count = convs.size,
                                current = ws.id == vm.workspaceId,
                                onSwitch = {
                                    historyOpen = false
                                    vm.switchWorkspace(ws.id)
                                },
                            )
                        }
                        items(convs, key = { it.id }) { conv ->
                            HistoryRow(
                                conv = conv,
                                active = conv.id == vm.currentId,
                                onClick = {
                                    historyOpen = false
                                    if (ws.id != vm.workspaceId) vm.switchWorkspace(ws.id)
                                    vm.openConversation(conv.id)
                                },
                                onLongClick = {
                                    historyEdit = conv
                                    historyEditText = conv.title
                                },
                            )
                        }
                        if (convs.isEmpty()) {
                            item(key = "empty-${ws.id}") {
                                Text(
                                    text = "这个工作区还没有会话",
                                    fontFamily = AnthropicSans,
                                    fontSize = 13.sp,
                                    color = colors.tertiaryText,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 工作区文件（每行右侧「导出」；标题栏右侧「导出整个工作区」） ──
    if (filesOpen) {
        val rootDir = remember(vm.workspaceId) { CodeWorkspaceStore.dirOf(ctx, vm.workspaceId) }
        fun exportOne(path: String) {
            runCatching { WorkspaceExport.shareFile(ctx, rootDir, path) }
                .onFailure { Toast.makeText(ctx, "导出失败：" + (it.message ?: "未知错误"), Toast.LENGTH_SHORT).show() }
        }
        fun exportAll() {
            runCatching { WorkspaceExport.shareZip(ctx, rootDir, vm.workspaceName) }
                .onFailure { Toast.makeText(ctx, "导出失败：" + (it.message ?: "未知错误"), Toast.LENGTH_SHORT).show() }
        }
        CodeSheetScaffold(
            title = "工作区文件",
            onClose = { filesOpen = false },
            action = {
                IconTap(onClick = { exportAll() }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_share),
                        contentDescription = "导出整个工作区",
                        tint = colors.tertiaryText,
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
        ) {
            if (vm.files.isEmpty()) {
                SheetEmpty("工作区是空的，让 Claude 写一个吧")
            } else {
                LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
                    items(vm.files, key = { it.path }) { f ->
                        WorkspaceFileRow(
                            f = f,
                            onClick = {
                                filesOpen = false
                                onOpenPreview(f.path)
                            },
                            onExport = { exportOne(f.path) },
                        )
                    }
                }
            }
        }
    }

    if (renameOpen) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            containerColor = colors.card,
            titleContentColor = colors.onSurface,
            textContentColor = colors.secondaryText,
            title = { Text("重命名会话", fontFamily = AnthropicSans, fontSize = 17.sp) },
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
                }) { Text("保存", fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.brand) }
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
            title = { Text("删除这个会话？", fontFamily = AnthropicSans, fontSize = 17.sp) },
            text = { Text("删除后无法恢复，工作区文件不受影响。", fontFamily = AnthropicSans, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    deleteOpen = false
                    vm.deleteCurrent()
                    input = ""
                }) { Text("删除", fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteOpen = false }) {
                    Text("取消", fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.tertiaryText)
                }
            },
        )
    }

    // ── 会话改名 / 删除（历史 sheet 长按） ──
    historyEdit?.let { conv ->
        WsNameDialog(
            title = "重命名会话",
            initial = historyEditText,
            confirm = "保存",
            onDismiss = { historyEdit = null },
            onConfirm = { name ->
                vm.renameConversation(conv.id, name)
                historyEdit = null
            },
            extraAction = { historyDelete = conv; historyEdit = null },
            extraLabel = "删除会话",
        )
    }

    historyDelete?.let { conv ->
        AlertDialog(
            onDismissRequest = { historyDelete = null },
            containerColor = colors.card,
            titleContentColor = colors.onSurface,
            textContentColor = colors.secondaryText,
            title = { Text("删除这个会话？", fontFamily = AnthropicSans, fontSize = 17.sp) },
            text = { Text("「${conv.title}」会被删除，工作区文件不受影响。", fontFamily = AnthropicSans, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteConversation(conv.id)
                    historyDelete = null
                }) { Text("删除", fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.error) }
            },
            dismissButton = {
                TextButton(onClick = { historyDelete = null }) {
                    Text("取消", fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.tertiaryText)
                }
            },
        )
    }

    // ── 新建工作区 ──
    if (wsCreateOpen) {
        WsNameDialog(
            title = "新建工作区",
            initial = "",
            confirm = "创建",
            onDismiss = { wsCreateOpen = false },
            onConfirm = { name ->
                wsCreateOpen = false
                vm.createWorkspace(name)
                input = ""
            },
        )
    }

    // ── 重命名 / 删除工作区 ──
    wsRenameTarget?.let { ws ->
        WsNameDialog(
            title = "重命名工作区",
            initial = wsRenameText,
            confirm = "保存",
            onDismiss = { wsRenameTarget = null },
            onConfirm = { name ->
                vm.renameWorkspace(ws.id, name)
                wsRenameTarget = null
            },
            extraAction = if (vm.workspaces.size > 1) {
                { wsDeleteTarget = ws; wsRenameTarget = null }
            } else null,
        )
    }

    wsDeleteTarget?.let { ws ->
        AlertDialog(
            onDismissRequest = { wsDeleteTarget = null },
            containerColor = colors.card,
            titleContentColor = colors.onSurface,
            textContentColor = colors.secondaryText,
            title = { Text("删除工作区？", fontFamily = AnthropicSans, fontSize = 17.sp) },
            text = {
                Text(
                    "「${ws.name}」里的文件和会话会一起删除，无法恢复。",
                    fontFamily = AnthropicSans,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    wsDeleteTarget = null
                    vm.deleteWorkspace(ws.id)
                    input = ""
                }) { Text("删除", fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.error) }
            },
            dismissButton = {
                TextButton(onClick = { wsDeleteTarget = null }) {
                    Text("取消", fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.tertiaryText)
                }
            },
        )
    }
}

/** 工作区命名对话框（新建与重命名共用）；[extraAction] 只在可删除时给一个红字「删除工作区」。 */
@Composable
private fun WsNameDialog(
    title: String,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    extraAction: (() -> Unit)? = null,
    extraLabel: String = "删除工作区",
) {
    val colors = ClaudeTheme.colors
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        titleContentColor = colors.onSurface,
        textContentColor = colors.secondaryText,
        title = { Text(title, fontFamily = AnthropicSans, fontSize = 17.sp) },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
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
                if (extraAction != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = extraLabel,
                        fontFamily = AnthropicSans,
                        fontSize = 14.sp,
                        color = colors.error,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = extraAction)
                            .padding(vertical = 6.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(confirm, fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.brand)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.tertiaryText)
            }
        },
    )
}

/** 底部 sheet 的通用外壳：标题行（左 ×、中标题）+ 分隔线 + 内容。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodeSheetScaffold(
    title: String,
    onClose: () -> Unit,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = ClaudeTheme.colors
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = colors.card,
        scrimColor = Color.Black.copy(alpha = 0.32f),
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(42.dp)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "关闭",
                        tint = colors.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = title,
                    fontFamily = AnthropicSans,
                    fontSize = 17.sp,
                    color = colors.onSurface,
                    modifier = Modifier.align(Alignment.Center),
                )
                if (action != null) {
                    Box(modifier = Modifier.align(Alignment.CenterEnd)) { action() }
                }
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(colors.line))
            content()
        }
    }
}

@Composable
private fun SheetEmpty(text: String) {
    val colors = ClaudeTheme.colors
    Text(
        text = text,
        fontFamily = AnthropicSans,
        fontSize = 14.sp,
        color = colors.tertiaryText,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
    )
}

/** 工作区面板的一行：名字 + 会话数，当前工作区右侧一枚对勾；长按改名/删除。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkspaceRow(
    ws: CodeWorkspaceMeta,
    current: Boolean,
    sessions: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = ClaudeTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = ws.name,
                fontFamily = AnthropicSans,
                fontSize = 15.sp,
                color = if (current) colors.brand else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "$sessions 个会话",
                fontFamily = AnthropicSans,
                fontSize = 12.sp,
                color = colors.tertiaryText,
            )
        }
        if (current) {
            Icon(
                painter = painterResource(R.drawable.cds_icon_check),
                contentDescription = "当前工作区",
                tint = colors.brand,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** 历史 sheet 的工作区分组标题：名字 + 会话数；点一下切到那个工作区。 */
@Composable
private fun WorkspaceGroupHeader(
    name: String,
    count: Int,
    current: Boolean,
    onSwitch: () -> Unit,
) {
    val colors = ClaudeTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSwitch)
            .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            fontFamily = AnthropicSans,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (current) colors.brand else colors.secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (current) "当前 · $count" else "$count 个会话",
            fontFamily = AnthropicSans,
            fontSize = 12.sp,
            color = colors.tertiaryText,
        )
    }
}

/** 历史会话一行：标题 + 相对时间，当前会话右侧一枚对勾；长按改名/删除。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    conv: CodeConversation,
    active: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val colors = ClaudeTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.cds_icon_chat),
            contentDescription = null,
            tint = if (active) colors.brand else colors.tertiaryText,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = conv.title,
                fontFamily = AnthropicSans,
                fontSize = 15.sp,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = relativeTime(conv.updatedAt),
                fontFamily = AnthropicSans,
                fontSize = 12.sp,
                color = colors.tertiaryText,
            )
        }
        if (active) {
            Icon(
                painter = painterResource(R.drawable.cds_icon_check),
                contentDescription = "当前会话",
                tint = colors.brand,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** 工作区文件一行：等宽路径 + 大小。 */
@Composable
private fun WorkspaceFileRow(
    f: WorkspaceFile,
    onClick: () -> Unit,
    onExport: () -> Unit,
) {
    val colors = ClaudeTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.cds_icon_file),
            contentDescription = null,
            tint = colors.tertiaryText,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = f.path,
            fontFamily = JetBrainsMono,
            fontSize = 13.5.sp,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = formatSize(f.size),
            fontFamily = AnthropicSans,
            fontSize = 12.sp,
            color = colors.tertiaryText,
        )
        Spacer(Modifier.width(4.dp))
        IconTap(onClick = onExport) {
            Icon(
                painter = painterResource(R.drawable.ic_share),
                contentDescription = "导出 " + f.path,
                tint = colors.tertiaryText,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

/** 刚刚 / 15分钟前 / 2小时前 / 3天前 / 10月21日（与对话列表同一套文案）。 */
private fun relativeTime(ts: Long): String {
    val diff = (System.currentTimeMillis() - ts).coerceAtLeast(0)
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        diff < minute -> "刚刚"
        diff < hour -> "${diff / minute}分钟前"
        diff < day -> "${diff / hour}小时前"
        diff < 7 * day -> "${diff / day}天前"
        else -> {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = ts }
            "${cal.get(java.util.Calendar.MONTH) + 1}月${cal.get(java.util.Calendar.DAY_OF_MONTH)}日"
        }
    }
}

/** 空态：砖红代码标志 + 一句标题 + 一句说明（不再摆推荐词，用户 2026-10-29 要求去掉）。 */
@Composable
internal fun CodeEmptyState() {
    val colors = ClaudeTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.cds_icon_code),
                contentDescription = null,
                // 用户要求：空态的标志用砖红，和工具名/发送键同一支强调色
                tint = colors.brand,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "让 Claude 写点什么",
            fontFamily = AnthropicSans,
            fontSize = 17.sp,
            color = colors.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "我会读你的工作区、改文件，并在预览里立刻呈现。",
            fontFamily = AnthropicSans,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = colors.tertiaryText,
            textAlign = TextAlign.Center,
        )
    }
}

/** 工作区条右侧的文字按钮（36dp 点击区，不做成实心按钮）。 */
@Composable
private fun WorkspaceAction(label: String, onClick: () -> Unit) {
    val colors = ClaudeTheme.colors
    Box(
        modifier = Modifier
            .height(36.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontFamily = AnthropicSans, fontSize = 13.sp, color = colors.brand)
    }
}

/** 左上「新建对话」：实心气泡 + 镂空加号（与对话页同款，颜色随主题反转）。 */
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
