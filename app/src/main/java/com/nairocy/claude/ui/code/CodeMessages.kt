package com.nairocy.claude.ui.code

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.nairocy.claude.R
import com.nairocy.claude.data.CodeDiffLine
import com.nairocy.claude.data.CodeMessage
import com.nairocy.claude.data.CodeToolRun
import com.nairocy.claude.data.Role
import com.nairocy.claude.data.textOf
import com.nairocy.claude.ui.components.AnchoredPopupPosition
import com.nairocy.claude.ui.components.ContextMenuCard
import com.nairocy.claude.ui.components.ContextMenuRow
import com.nairocy.claude.ui.components.MarkdownText
import com.nairocy.claude.ui.components.SparkAnimation
import com.nairocy.claude.ui.components.SparkKind
import com.nairocy.claude.ui.theme.AnthropicSans
import com.nairocy.claude.ui.theme.AnthropicSerif
import com.nairocy.claude.ui.theme.ClaudeTheme
import com.nairocy.claude.ui.theme.JetBrainsMono

/**
 * Code 页的消息流。
 *
 * 与对话页的区别：助手消息是**有序块**（text / tool），工具卡夹在正文之间，
 * 顺序就是模型实际的调用顺序（ls → read → edit …），不做归并、不排序。
 *
 * 单独抽成 Composable 是为了 Paparazzi 能直接喂假数据出图（截图测试里不能建 ViewModel）。
 */
@Composable
fun CodeMessageList(
    messages: List<CodeMessage>,
    streaming: Boolean = false,
    toolHint: String? = null,
    currentId: String = "",
    /** 工具卡的入场动画；Paparazzi 截图传 false（只渲染一帧，动画态会截到「还没出现」）。 */
    animateIn: Boolean = true,
    onRunArtifact: ((lang: String, code: String, title: String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(
            messages,
            // 与对话页同一套 key 规则：不带 key 时 LazyColumn 按下标认条目，换会话会把两段内容叠在一起。
            key = { index, m -> "$currentId:$index:${m.ts}" },
        ) { index, m ->
            CodeMessageRow(
                m = m,
                streaming = streaming && index == messages.lastIndex,
                toolHint = toolHint,
                onRunArtifact = onRunArtifact,
                animateIn = animateIn,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CodeMessageRow(
    m: CodeMessage,
    streaming: Boolean,
    toolHint: String?,
    onRunArtifact: ((lang: String, code: String, title: String) -> Unit)?,
    animateIn: Boolean,
) {
    val colors = ClaudeTheme.colors
    if (m.role == Role.USER) {
        // 长按用户消息：只要「复制消息 / 选择文本」（Code 页不做编辑重发，用户 2026-10-29 明确）
        var menuOpen by remember { mutableStateOf(false) }
        var selectOpen by remember { mutableStateOf(false) }
        val clipboard = LocalClipboardManager.current
        val ctx = LocalContext.current
        val d = LocalDensity.current
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box {
                Box(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.userBubble)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { menuOpen = true },
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = m.textOf(),
                        fontFamily = AnthropicSans,
                        fontSize = 16.sp,
                        lineHeight = 25.sp,
                        color = colors.onSurface,
                    )
                }
                if (menuOpen) {
                    val dy = with(d) { 8.dp.roundToPx() }
                    Popup(
                        popupPositionProvider = AnchoredPopupPosition(dy, alignRight = true),
                        onDismissRequest = { menuOpen = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        ContextMenuCard {
                            ContextMenuRow(R.drawable.cds_icon_copy, "复制消息") {
                                clipboard.setText(androidx.compose.ui.text.AnnotatedString(m.textOf()))
                                menuOpen = false
                                android.widget.Toast.makeText(ctx, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            ContextMenuRow(R.drawable.ic_select_text, "选择文本") {
                                menuOpen = false
                                selectOpen = true
                            }
                        }
                    }
                }
            }
        }
        if (selectOpen) {
            AlertDialog(
                onDismissRequest = { selectOpen = false },
                containerColor = colors.card,
                titleContentColor = colors.onSurface,
                title = { Text("选择文本", fontFamily = AnthropicSans, fontSize = 17.sp) },
                text = {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            text = m.textOf(),
                            fontFamily = AnthropicSans,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = colors.onSurface,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectOpen = false }) {
                        Text("完成", fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.brand)
                    }
                },
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        val parts = m.parts
        // 还在等第一个字：整条只有一条「思考中」
        if (parts.isEmpty() && streaming) {
            CodeThinkingRow(overrideText = toolHint)
        } else {
            parts.forEachIndexed { index, part ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                when (part.kind) {
                    "tool" -> part.tool?.let { tool ->
                        var appeared by remember { mutableStateOf(!animateIn) }
                        LaunchedEffect(Unit) { appeared = true }
                        androidx.compose.animation.AnimatedVisibility(
                            visible = appeared,
                            enter = androidx.compose.animation.fadeIn(
                                androidx.compose.animation.core.tween(
                                    com.nairocy.claude.ui.motion.Motion.ENTER_MS,
                                    easing = com.nairocy.claude.ui.motion.Motion.Decel,
                                )
                            ) + androidx.compose.animation.slideInVertically(
                                animationSpec = androidx.compose.animation.core.tween(
                                    com.nairocy.claude.ui.motion.Motion.ENTER_MS,
                                    easing = com.nairocy.claude.ui.motion.Motion.Decel,
                                ),
                                initialOffsetY = { it / 4 },
                            ),
                        ) { CodeToolCard(tool) }
                    }
                    else -> if (part.text.isNotBlank()) {
                        MarkdownText(
                            text = part.text,
                            onRunArtifact = onRunArtifact,
                            streaming = streaming && index == parts.lastIndex,
                            // 浮升只给「正在写的这一块」：工具卡与历史正文一律不动画。
                            rise = streaming && index == parts.lastIndex,
                            riseKey = "${m.ts}#$index",
                        )
                    }
                }
            }
            // 工具执行中（最后一块是工具、模型还没接着写）：正文下方补一条提示
            if (streaming && parts.lastOrNull()?.kind == "tool") {
                Spacer(Modifier.height(10.dp))
                CodeThinkingRow(overrideText = toolHint)
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

    }
}

/** 工具名 → 卡片上的显示名（首字母大写，与参考稿一致）。 */
internal fun toolDisplayName(name: String): String = when (name) {
    "ls" -> "Ls"
    "read" -> "Read"
    "write" -> "Write"
    "edit" -> "Edit"
    "grep" -> "Grep"
    "web_search" -> "Web search"
    "fetch_page" -> "Fetch page"
    else -> name.replaceFirstChar { it.uppercase() }
}

/**
 * 工具卡：白底 + 1dp 边 + 14dp 圆角（行与 diff 共用同一张卡）。
 *
 * 三种形态：
 * 1. 只有一行（ls / grep / 联网搜索）：右边灰字是目标，输出折叠成 2 行、点击展开；
 * 2. 带 diff（write / edit）：行下方紧贴 diff 块，默认 130dp、点击展开到 480dp；
 * 3. 失败：右侧一枚 ×（红），不弹 toast —— 错误正文本来就在 output 里。
 */
@Composable
internal fun CodeToolCard(tool: CodeToolRun) {
    val colors = ClaudeTheme.colors
    // ⚠️ 展开状态不能带 key：流式时 diff/output 一直在变，带 key 会把展开打回折叠（对话页踩过）。
    var diffOpen by remember { mutableStateOf(false) }
    var outOpen by remember { mutableStateOf(false) }
    val serif = AnthropicSerif

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .border(1.dp, colors.line, RoundedCornerShape(14.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = toolDisplayName(tool.name),
                fontFamily = serif,
                fontSize = 18.sp,
                // 用户要求：动作名用砖红（与发送键/星芒同一支强调色），一眼能从正文里分出来
                color = colors.brand,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (tool.running) "${tool.target} …" else tool.target,
                fontFamily = JetBrainsMono,
                fontSize = 14.sp,
                color = if (tool.running) colors.mutedText else colors.tertiaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!tool.ok) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = "执行失败",
                    tint = colors.error,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        if (tool.diff.isNotEmpty() || tool.output.isNotBlank()) {
            // 行与内容之间一条细线：参考稿也是靠这条线把「动作」和「结果」分开
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.line)
            )
        }
        if (tool.diff.isNotEmpty()) {
            CodeDiffBlock(
                lines = tool.diff,
                expanded = diffOpen,
                onToggle = { diffOpen = !diffOpen },
            )
        } else if (tool.output.isNotBlank()) {
            CodeOutputBlock(
                text = tool.output,
                expanded = outOpen,
                failed = !tool.ok,
                onToggle = { outOpen = !outOpen },
            )
        }
    }
}

/** 行 diff：+ 行绿底、- 行红底，左侧 46dp 行号列 + 1dp 竖分隔（照参考稿）。 */
@Composable
private fun CodeDiffBlock(
    lines: List<CodeDiffLine>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = ClaudeTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = if (expanded) 480.dp else 130.dp)
            .clipToBounds()
            .clickable(onClick = onToggle),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            lines.forEach { line ->
                val add = line.op == "+"
                val del = line.op == "-"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .background(
                            when {
                                add -> colors.codeDiffAdd
                                del -> colors.codeDiffDel
                                else -> Color.Transparent
                            }
                        ),
                ) {
                    Text(
                        text = "${line.op}${line.no}",
                        fontFamily = JetBrainsMono,
                        fontSize = 12.sp,
                        color = colors.tertiaryText,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .width(46.dp)
                            .padding(end = 10.dp, top = 3.dp, bottom = 3.dp),
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(colors.line),
                    )
                    Text(
                        text = line.text.ifEmpty { " " },
                        fontFamily = JetBrainsMono,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                        color = colors.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
                    )
                }
            }
        }
        if (!expanded) {
            // 底部渐隐，暗示「还能点开看更多」
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, colors.card.copy(alpha = 0.92f)),
                        )
                    ),
            )
        }
    }
}

/** ls / read / grep 的纯文本输出：默认只露两行，点击展开到 320dp。 */
@Composable
private fun CodeOutputBlock(
    text: String,
    expanded: Boolean,
    failed: Boolean,
    onToggle: () -> Unit,
) {
    val colors = ClaudeTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = if (expanded) 320.dp else 44.dp)
            .clipToBounds()
            .clickable(onClick = onToggle),
    ) {
        Text(
            text = text.trimEnd(),
            fontFamily = JetBrainsMono,
            fontSize = 12.5.sp,
            lineHeight = 19.sp,
            color = if (failed) colors.error else colors.secondaryText,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
        if (!expanded) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(18.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, colors.card.copy(alpha = 0.92f)),
                        )
                    ),
            )
        }
    }
}

/** Code 页的思考文案：都是「正在干活」的说法，轮换节奏与对话页一致（3–5s，不连续重复）。 */
private val CODE_THINKING_PHRASES = listOf(
    "构建进行中...",
    "正在读工作区...",
    "搭页面结构...",
    "调样式细节...",
    "对齐间距与断点...",
    "马上就能预览了...",
)

/**
 * Code 页的加载行：**左侧**动态星芒 + 轮换文案（用户要求：信息流底部要有加载动画，靠左）。
 *
 * 与对话页的 [com.nairocy.claude.ui.components.ThinkingRow] 区别：这里用星芒动画而不是三点，
 * 因为 Code 页的主视觉就是那颗星；[overrideText] 非空时固定显示（读文件 / 写文件 / 搜索中）。
 */
@Composable
fun CodeThinkingRow(
    modifier: Modifier = Modifier,
    overrideText: String? = null,
) {
    val colors = ClaudeTheme.colors
    var phrase by remember { mutableStateOf(CODE_THINKING_PHRASES.random()) }
    LaunchedEffect(overrideText) {
        if (overrideText != null) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(kotlin.random.Random.nextLong(4_500, 7_001))
            phrase = CODE_THINKING_PHRASES.filterNot { it == phrase }.random()
        }
    }
    val shown = overrideText ?: phrase
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SparkAnimation(kind = SparkKind.THINKING, size = 20.dp, frameMs = 220L)
        androidx.compose.animation.Crossfade(
            targetState = shown,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 320),
            label = "codeThinkingText",
        ) { text ->
            Text(
                text = text,
                fontFamily = AnthropicSans,
                fontSize = 14.sp,
                color = colors.tertiaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // 固定最小宽度：长短句切换时文字不左右跳
                modifier = Modifier.widthIn(min = 132.dp),
            )
        }
    }
}
