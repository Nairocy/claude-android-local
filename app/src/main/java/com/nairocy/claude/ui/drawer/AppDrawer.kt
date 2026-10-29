package com.nairocy.claude.ui.drawer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nairocy.claude.R
import com.nairocy.claude.ui.components.UserAvatar
import com.nairocy.claude.data.Conversation
import com.nairocy.claude.ui.theme.AnthropicSerifDisplay
import com.nairocy.claude.ui.theme.AnthropicSans
import com.nairocy.claude.ui.theme.ClaudeTheme
import kotlin.math.roundToInt

/**
 * 左侧抽屉。对应截图 2：大标题 Claude → 四项导航 → 最近使用 → 底部头像 + 新建胶囊。
 * 宽度占屏 85%，右边缘大圆角。
 */
@Composable
fun AppDrawer(
    open: Boolean,
    conversations: List<Conversation>,
    /** 当前账号头像：首字母（默认砖红底）+ 自定义头像文件 */
    avatarInitial: String = "N",
    avatarFile: java.io.File? = null,
    onClose: () -> Unit,
    onNewChat: () -> Unit,
    onOpenConversations: () -> Unit,
    onOpenProjects: () -> Unit = {},
    onOpenCode: () -> Unit = {},
    onOpenArtifacts: () -> Unit = {},
    onSettings: () -> Unit = {},
    onSelect: (Conversation) -> Unit,
    onRenameConv: (Conversation) -> Unit = {},
    onTogglePinConv: (Conversation) -> Unit = {},
    onAddToProjectConv: (Conversation) -> Unit = {},
    onDeleteConv: (Conversation) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val colors = ClaudeTheme.colors
    var menuConvId by remember { mutableStateOf<String?>(null) }
    // 抽屉常驻组合（不再用 AnimatedVisibility 反复拆装整棵抽屉）：首次打开只剩位移动画，
    // 避免第一次点汉堡要现场组合会话列表 + 解码头像/字体造成的卡顿。
    val drawerWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    val slide by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (open) 0f else -drawerWidthPx,
        animationSpec = androidx.compose.animation.core.tween(
            if (open) com.nairocy.claude.ui.motion.Motion.ENTER_MS + 40
            else com.nairocy.claude.ui.motion.Motion.EXIT_MS + 40,
            easing = if (open) com.nairocy.claude.ui.motion.Motion.Decel
            else com.nairocy.claude.ui.motion.Motion.Accel,
        ),
        label = "drawerSlide",
    )
    val scrim by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (open) 0.30f else 0f,
        animationSpec = androidx.compose.animation.core.tween(
            if (open) com.nairocy.claude.ui.motion.Motion.ENTER_MS
            else com.nairocy.claude.ui.motion.Motion.EXIT_MS,
            easing = com.nairocy.claude.ui.motion.Motion.Standard,
        ),
        label = "drawerScrim",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // 字体预热（1dp、几乎透明）：把四套 variable font 的解码从「第一次用到」挪到启动时
        com.nairocy.claude.ui.motion.FontWarmup()
        content()

        // 遮罩（常驻，靠 alpha 过渡；关着的时候不接点击）
        run {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrim))
                    .then(
                        if (open) Modifier.clickable(
                            interactionSource = androidx.compose.runtime.remember {
                                androidx.compose.foundation.interaction.MutableInteractionSource()
                            },
                            indication = null,
                            onClick = onClose,
                        ) else Modifier
                    )
            )
        }

        // 面板：启动后空闲时预热一次（不抢首帧），之后常驻；完全关着的时候不绘制。
        var warm by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(900)
            warm = true
        }
        if (warm || open) run {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.85f)
                    .offset { androidx.compose.ui.unit.IntOffset(slide.roundToInt(), 0) }
                    .drawWithContent {
                        // 完全收起时不绘制（省掉遮罩与整棵抽屉的 raster）
                        if (open || slide > -drawerWidthPx + 0.5f) drawContent()
                    }
                    .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
                    .background(colors.surfaceVariant)
                    .padding(top = 48.dp, bottom = 16.dp),
            ) {
                Text(
                    text = "Claude",
                    fontFamily = AnthropicSerifDisplay,
                    fontSize = 34.sp,
                    color = colors.onSurface,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(24.dp))

                NavItem(R.drawable.cds_icon_chat_nav, "对话", enabled = true) { onOpenConversations() }
                NavItem(R.drawable.cds_icon_projects, "项目", enabled = true) { onOpenProjects() }
                NavItem(R.drawable.cds_icon_code, "Code", enabled = true) { onOpenCode() }
                NavItem(R.drawable.cds_icon_artifacts, "Artifacts", enabled = true) { onOpenArtifacts() }

                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(1.dp)
                        .background(colors.line)
                )
                Spacer(Modifier.height(16.dp))

                val pinnedList = conversations.filter { it.pinned }
                val recentList = conversations.filterNot { it.pinned }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    if (pinnedList.isNotEmpty()) {
                        item { SectionLabel("已置顶") }
                        items(pinnedList) { conv ->
                            ConversationRow(
                                conv, colors, onSelect,
                                menuOpen = menuConvId == conv.id,
                                onLongPress = { menuConvId = conv.id },
                                onDismiss = { menuConvId = null },
                                onRename = { menuConvId = null; onRenameConv(conv) },
                                onTogglePin = { menuConvId = null; onTogglePinConv(conv) },
                                onAddToProject = { menuConvId = null; onAddToProjectConv(conv) },
                                onDelete = { menuConvId = null; onDeleteConv(conv) },
                            )
                        }
                        item { SectionLabel("最近使用") }
                    } else {
                        item { SectionLabel("最近使用") }
                    }
                    items(recentList) { conv ->
                        ConversationRow(
                            conv, colors, onSelect,
                            menuOpen = menuConvId == conv.id,
                            onLongPress = { menuConvId = conv.id },
                            onDismiss = { menuConvId = null },
                            onRename = { menuConvId = null; onRenameConv(conv) },
                            onTogglePin = { menuConvId = null; onTogglePinConv(conv) },
                            onAddToProject = { menuConvId = null; onAddToProjectConv(conv) },
                            onDelete = { menuConvId = null; onDeleteConv(conv) },
                        )
                    }
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(1.dp)
                        .background(colors.line)
                )
                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UserAvatar(
                        size = 36.dp,
                        initial = avatarInitial,
                        file = avatarFile,
                        modifier = Modifier.clickable(onClick = onSettings),
                    )
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(colors.onSurface)
                            .clickable { onNewChat() }
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("＋", fontFamily = AnthropicSans, fontSize = 14.sp, color = colors.surface)
                        Text(
                            "新建对话",
                            fontFamily = AnthropicSans,
                            fontSize = 14.sp,
                            color = colors.surface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val colors = ClaudeTheme.colors
    Text(
        text = text,
        fontFamily = AnthropicSans,
        fontSize = 12.sp,
        color = colors.tertiaryText,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conv: Conversation,
    colors: com.nairocy.claude.ui.theme.ClaudeColors,
    onSelect: (Conversation) -> Unit,
    menuOpen: Boolean = false,
    onLongPress: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onRename: () -> Unit = {},
    onTogglePin: () -> Unit = {},
    onAddToProject: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { onSelect(conv) }, onLongClick = onLongPress)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(if (conv.pinned) R.drawable.ic_pin else R.drawable.cds_icon_chat),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(colors.tertiaryText),
            )
            Text(
                text = conv.title,
                fontFamily = AnthropicSans,
                fontSize = 14.sp,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (menuOpen) {
            val dx = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.roundToPx() }
            androidx.compose.ui.window.Popup(
                popupPositionProvider = com.nairocy.claude.ui.components.AnchoredPopupPosition(dx, alignRight = true),
                onDismissRequest = onDismiss,
                properties = androidx.compose.ui.window.PopupProperties(focusable = true),
            ) {
                com.nairocy.claude.ui.components.ContextMenuCard(minWidth = 118, maxWidth = 176) {
                    com.nairocy.claude.ui.components.ContextMenuRow(R.drawable.ic_edit, "重命名", onClick = onRename)
                    com.nairocy.claude.ui.components.ContextMenuRow(
                        R.drawable.ic_pin,
                        if (conv.pinned) "取消置顶" else "置顶",
                        onClick = onTogglePin,
                    )
                    com.nairocy.claude.ui.components.ContextMenuRow(R.drawable.ic_project, "添加到项目", onClick = onAddToProject)
                    com.nairocy.claude.ui.components.ContextMenuRow(
                        R.drawable.ic_trash, "删除", danger = true, onClick = onDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItem(icon: Int, label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = ClaudeTheme.colors
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val tint = if (enabled) colors.onSurface else colors.tertiaryText
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (enabled) {
                    Modifier.clickable {
                        com.nairocy.claude.data.Haptics.tap(ctx)
                        onClick()
                    }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 24.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint),
        )
        Text(
            text = label,
            fontFamily = AnthropicSans,
            fontSize = 15.sp,
            color = tint,
        )
    }
}
