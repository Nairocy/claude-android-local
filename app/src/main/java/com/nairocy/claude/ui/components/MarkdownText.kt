package com.nairocy.claude.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nairocy.claude.R
import com.nairocy.claude.ui.theme.AnthropicSans
import com.nairocy.claude.ui.theme.AnthropicSerif
import com.nairocy.claude.ui.theme.AnthropicSerifDisplay
import com.nairocy.claude.ui.theme.ClaudeTheme
import com.nairocy.claude.ui.theme.JetBrainsMono

/**
 * Markdown 渲染：段落 / 标题 / 有序无序嵌套列表 / 任务列表 / 引用块 / 分隔线 /
 * 围栏代码块（含复制）/ 表格 / 行内粗体斜体·代码·删除线·链接。
 */
/** 含中日韩字符就用系统无衬线字体：Anthropic 字体没有中文字形，自定义字体的 fallback 会走到设备主题字体。 */
internal fun hasCjk(text: String): Boolean = text.any {
    it.code in 0x4E00..0x9FFF || it.code in 0x3000..0x303F || it.code in 0xFF00..0xFFEF
}

@Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun bodyFont(text: String): FontFamily =
    if (hasCjk(text)) FontFamily.SansSerif else AnthropicSans

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    /** 点 artifact 的运行按钮：把代码交给 WebView 渲染页。 */
    onRunArtifact: ((lang: String, code: String, title: String) -> Unit)? = null,
    /** 这条消息还在生成：末尾的 artifact 卡显示加载动画、不给运行按钮。 */
    streaming: Boolean = false,
    /** 流式浮升（见 RiseText.kt）：只给「正在写的这一条」开。代码块永远不参与。 */
    rise: Boolean = false,
    /** 消息身份（用 ts 即可）：换消息时浮升状态重置。 */
    riseKey: Any? = null,
) {
    val colors = ClaudeTheme.colors
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val blocks = rememberParseMarkdown(text)

    SelectionContainer {
        Column(modifier = modifier) {
            blocks.forEach { block ->
                // 流式浮升只作用在最后一块（尾巴）。前面早就写完了。
                val isTail = block === blocks.lastOrNull()
                when (block) {
                    is MdBlock.Paragraph -> {
                        if (isTail && rise) {
                            RisingInlineText(
                                raw = block.text,
                                identity = RiseBlockKey(riseKey, blocks.size, 0),
                                build = { s -> withCitations(inline(s, colors.onSurface, colors.brand)) },
                                style = TextStyle(
                                    fontFamily = AnthropicSerif,
                                    fontSize = 17.sp,
                                    lineHeight = 27.sp,
                                ),
                                color = colors.onSurface,
                                rise = streaming,
                            )
                        } else {
                            val cited1 = withCitations(inline(block.text, colors.onSurface, colors.brand))
                            Text(
                                text = cited1.text,
                                inlineContent = cited1.contents,
                                fontFamily = AnthropicSerif,
                                fontSize = 17.sp,
                                lineHeight = 27.sp,
                                color = colors.onSurface,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    is MdBlock.Heading -> {
                        if (isTail && rise) {
                            RisingInlineText(
                                raw = block.text,
                                identity = RiseBlockKey(riseKey, blocks.size, 0),
                                build = { s -> withCitations(inline(s, colors.onSurface, colors.brand)) },
                                style = TextStyle(
                                    fontFamily = AnthropicSerifDisplay,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = when (block.level) {
                                        1 -> 23.sp
                                        2 -> 20.sp
                                        else -> 18.sp
                                    },
                                    lineHeight = 29.sp,
                                ),
                                color = colors.onSurface,
                                rise = streaming,
                            )
                        } else {
                            val cited2 = withCitations(inline(block.text, colors.onSurface, colors.brand))
                            Text(
                                text = cited2.text,
                                inlineContent = cited2.contents,
                                fontFamily = AnthropicSerifDisplay,
                                fontWeight = FontWeight.Medium,
                                fontSize = when (block.level) {
                                    1 -> 23.sp
                                    2 -> 20.sp
                                    else -> 18.sp
                                },
                                lineHeight = 29.sp,
                                color = colors.onSurface,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    is MdBlock.Bullets -> {
                        block.items.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = (item.level * 16).dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                // 官方项目符号：6.5dp 实心圆（一级）/ 空心圈（二级），
                                // 圆点左缘 x=29dp、文字左缘 x=40.5dp（1368px 截图实测）
                                Box(
                                    modifier = Modifier
                                        .padding(start = 9.dp, top = 7.5.dp)
                                        .size(6.5.dp)
                                        .clip(CircleShape)
                                        .then(
                                            if (item.level == 0) {
                                                Modifier.background(colors.onSurface)
                                            } else {
                                                Modifier.border(1.2.dp, colors.onSurface, CircleShape)
                                            }
                                        )
                                )
                                Spacer(Modifier.width(5.dp))
                                if (isTail && rise && item === block.items.lastOrNull()) {
                                    RisingInlineText(
                                        raw = item.text,
                                        identity = RiseBlockKey(riseKey, blocks.size, block.items.size),
                                        build = { s -> withCitations(inline(s, colors.onSurface, colors.brand)) },
                                        style = TextStyle(
                                            fontFamily = AnthropicSerif,
                                            fontSize = 17.sp,
                                            lineHeight = 27.sp,
                                        ),
                                        color = colors.onSurface,
                                        rise = streaming,
                                    )
                                } else {
                                    val cited3 = withCitations(inline(item.text, colors.onSurface, colors.brand))
                                    Text(
                                        text = cited3.text,
                                        inlineContent = cited3.contents,
                                        fontFamily = AnthropicSerif,
                                        fontSize = 17.sp,
                                        lineHeight = 27.sp,
                                        color = colors.onSurface,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    is MdBlock.Numbers -> {
                        block.items.forEachIndexed { i, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = (item.level * 16).dp),
                            ) {
                                Text(
                                    text = "${i + 1}.",
                                    fontFamily = AnthropicSans,
                                    fontSize = 17.sp,
                                    lineHeight = 27.sp,
                                    color = colors.tertiaryText,
                                    modifier = Modifier.padding(end = 10.dp),
                                )
                                if (isTail && rise && i == block.items.lastIndex) {
                                    RisingInlineText(
                                        raw = item.text,
                                        identity = RiseBlockKey(riseKey, blocks.size, block.items.size),
                                        build = { s -> withCitations(inline(s, colors.onSurface, colors.brand)) },
                                        style = TextStyle(
                                            fontFamily = AnthropicSerif,
                                            fontSize = 17.sp,
                                            lineHeight = 27.sp,
                                        ),
                                        color = colors.onSurface,
                                        rise = streaming,
                                    )
                                } else {
                                    val cited4 = withCitations(inline(item.text, colors.onSurface, colors.brand))
                                    Text(
                                        text = cited4.text,
                                        inlineContent = cited4.contents,
                                        fontFamily = AnthropicSerif,
                                        fontSize = 17.sp,
                                        lineHeight = 27.sp,
                                        color = colors.onSurface,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    is MdBlock.Tasks -> {
                        block.items.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = (item.level * 16).dp, bottom = 6.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                CheckBox(checked = item.checked, tint = colors.brand, borderColor = colors.tertiaryText)
                                Spacer(Modifier.width(10.dp))
                                val cited5 = withCitations(inline(item.text, colors.onSurface, colors.brand))
                                Text(
                                                                    text = cited5.text,
                                inlineContent = cited5.contents,
                                    fontFamily = AnthropicSerif,
                                    fontSize = 17.sp,
                                    lineHeight = 27.sp,
                                    color = if (item.checked) colors.tertiaryText else colors.onSurface,
                                    textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    is MdBlock.Quote -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .background(colors.line),
                            )
                            Spacer(Modifier.width(12.dp))
                            if (isTail && rise) {
                                RisingInlineText(
                                    raw = block.text,
                                    identity = RiseBlockKey(riseKey, blocks.size, 0),
                                    build = { s -> withCitations(inline(s, colors.secondaryText, colors.brand)) },
                                    style = TextStyle(
                                        fontFamily = AnthropicSerif,
                                        fontSize = 16.sp,
                                        lineHeight = 25.sp,
                                    ),
                                    color = colors.secondaryText,
                                    rise = streaming,
                                    modifier = Modifier.padding(vertical = 2.dp),
                                )
                            } else {
                                val cited6 = withCitations(inline(block.text, colors.secondaryText, colors.brand))
                                Text(
                                    text = cited6.text,
                                    inlineContent = cited6.contents,
                                    fontFamily = AnthropicSerif,
                                    fontSize = 16.sp,
                                    lineHeight = 25.sp,
                                    color = colors.secondaryText,
                                    modifier = Modifier.padding(vertical = 2.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    MdBlock.Divider -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                                .height(1.dp)
                                .background(colors.line),
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    is MdBlock.Code -> {
                        if (isArtifact(block)) {
                            ArtifactCard(
                                block = block,
                                clipboard = clipboard,
                                onRun = onRunArtifact?.let { run ->
                                    { run(block.lang, block.code, artifactTitle(block)) }
                                },
                                // 只有「最后一块且整条消息还在生成」才算未完成：
                                // 前面的 artifact 早就写完了，运行按钮该在。
                                streaming = streaming && isTail,
                            )
                        } else Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceVariant)
                                .padding(14.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = block.lang.ifBlank { "code" },
                                    fontFamily = JetBrainsMono,
                                    fontSize = 11.sp,
                                    color = colors.tertiaryText,
                                )
                                Text(
                                    text = "\u590d\u5236",
                                    fontFamily = AnthropicSans,
                                    fontSize = 11.sp,
                                    color = colors.brand,
                                    modifier = Modifier.clickable {
                                        clipboard.setText(AnnotatedString(block.code))
                                    },
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            // 代码块不参与浮升：高输出内容原样直出（示例动画只作用在正文文字上）
                            Text(
                                text = AnnotatedString(block.code),
                                fontFamily = JetBrainsMono,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = colors.onSurface,
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                softWrap = false,
                            )
                        }
                        if (!isArtifact(block)) Spacer(Modifier.height(10.dp))
                    }

                    is MdBlock.Table -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, colors.line, RoundedCornerShape(10.dp)),
                        ) {
                            Row(modifier = Modifier.background(colors.surfaceVariant)) {
                                block.headers.forEach { h ->
                                    val cited7 = withCitations(inline(h, colors.onSurface, colors.brand))
                                    Text(
                                                                            text = cited7.text,
                                    inlineContent = cited7.contents,
                                        fontFamily = AnthropicSans,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.onSurface,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 10.dp, vertical = 9.dp),
                                    )
                                }
                            }
                            block.rows.forEach { row ->
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(colors.line),
                                )
                                Row {
                                    row.forEach { cell ->
                                        val cited8 = withCitations(inline(cell, colors.onSurface, colors.brand))
                                        Text(
                                                                                    text = cited8.text,
                                        inlineContent = cited8.contents,
                                            fontFamily = AnthropicSans,
                                            fontSize = 13.sp,
                                            lineHeight = 19.sp,
                                            color = colors.onSurface,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 10.dp, vertical = 9.dp),
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

private fun isArtifact(block: MdBlock.Code) = com.nairocy.claude.data.Artifacts.isArtifact(block.lang, block.code)

private fun artifactTitle(block: MdBlock.Code) = com.nairocy.claude.data.Artifacts.titleOf(block.lang, block.code)

/**
 * Artifact 卡片：紫底 + 图标块 + 标题 + Artifact。默认折叠，点卡片展开代码。
 * 视觉取自原版对话页（#796DC0 / #EFEDFF）。
 *
 * 头部与展开区各有一个运行按钮（圆角三角）：html / svg 交给 WebView 渲染。
 * 复制用官方堆叠方形图标（cds_icon_copy），不再用「复制」文字。
 */
@Composable
internal fun ArtifactCard(
    block: MdBlock.Code,
    clipboard: ClipboardManager,
    onRun: (() -> Unit)? = null,
    /** 截图测试用：直接渲染展开态。 */
    initiallyExpanded: Boolean = false,
    /** 还在生成：运行按钮换成加载动画，卡片扫过一层渐变色。 */
    streaming: Boolean = false,
) {
    val colors = ClaudeTheme.colors
    // ⚠️ 不要写成 remember(block.code)：流式时每个 token 都会改 code，key 一变
    // 就把展开状态重置成折叠 —— 用户点了「展开」立刻被收回，看起来像根本展不开。
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val runAction = onRun?.takeIf {
        com.nairocy.claude.data.Artifacts.canPreview(block.lang, block.code)
    }
    // 生成中：卡片背景做一道横向扫过的渐变高光（官方那种「正在写」的感觉）
    val shimmer = if (streaming) {
        val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "artifactShimmer")
        val x by transition.animateFloat(
            initialValue = -1f,
            targetValue = 2f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(
                    durationMillis = 1_500,
                    easing = androidx.compose.animation.core.LinearEasing,
                ),
            ),
            label = "shimmerX",
        )
        x
    } else Float.NaN
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.artifactSurface)
            .then(
                if (streaming) Modifier.drawWithContent {
                    drawContent()
                    // 一道 45° 斜向高光横扫（宽度 = 卡片 60%，颜色取 artifact 主色 12%）
                    val bandW = size.width * 0.6f
                    val start = shimmer * size.width
                    drawRect(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            0f to Color.Transparent,
                            0.5f to colors.artifactIcon.copy(alpha = 0.16f),
                            1f to Color.Transparent,
                            startX = start,
                            endX = start + bandW,
                        ),
                    )
                } else Modifier
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (colors.isDark) colors.card else Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.cds_icon_artifacts),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(colors.artifactIcon),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artifactTitle(block),
                    fontFamily = AnthropicSans,
                    fontSize = 14.sp,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Artifact",
                    fontFamily = AnthropicSans,
                    fontSize = 12.sp,
                    color = colors.tertiaryText,
                )
            }
            if (runAction != null && !streaming) {
                RunGlyphButton(onClick = runAction, size = 30.dp)
                Spacer(Modifier.width(8.dp))
            } else if (streaming) {
                // 还没写完：不显示运行按钮（点开也是半截代码），给一枚加载转圈
                ArtifactLoadingGlyph(size = 30.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = if (expanded) "收起" else "展开",
                fontFamily = AnthropicSans,
                fontSize = 12.sp,
                color = colors.artifactIcon,
            )
        }

        if (expanded) {
            androidx.compose.animation.AnimatedVisibility(
                visible = true,
                enter = androidx.compose.animation.expandVertically(
                    androidx.compose.animation.core.tween(
                        com.nairocy.claude.ui.motion.Motion.SMALL_MS,
                        easing = com.nairocy.claude.ui.motion.Motion.Decel,
                    )
                ) + androidx.compose.animation.fadeIn(
                    androidx.compose.animation.core.tween(
                        com.nairocy.claude.ui.motion.Motion.SMALL_MS,
                        easing = com.nairocy.claude.ui.motion.Motion.Standard,
                    )
                ),
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.card)
                    .padding(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (runAction != null && !streaming) {
                        RunGlyphButton(onClick = runAction, size = 26.dp)
                        Spacer(Modifier.width(10.dp))
                    } else if (streaming) {
                        ArtifactLoadingGlyph(size = 22.dp)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        text = block.lang.ifBlank { "code" },
                        fontFamily = JetBrainsMono,
                        fontSize = 11.sp,
                        color = colors.tertiaryText,
                        modifier = Modifier.weight(1f),
                    )
                    CopyGlyphButton(onClick = { clipboard.setText(AnnotatedString(block.code)) })
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = block.code,
                    fontFamily = JetBrainsMono,
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    color = colors.onSurface,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    softWrap = false,
                )
            }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

/** 运行按钮：偏圆角方底 + 圆角实心三角（矢量绘制，不用字体字符）。 */
@Composable
fun ArtifactLoadingGlyph(size: androidx.compose.ui.unit.Dp = 28.dp) {
    val colors = ClaudeTheme.colors
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "artifactLoading")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                durationMillis = 900,
                easing = androidx.compose.animation.core.LinearEasing,
            ),
        ),
        label = "artifactLoadingAngle",
    )
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(colors.artifactIcon.copy(alpha = if (colors.isDark) 0.26f else 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(size * 0.5f)) {
            val stroke = 1.8.dp.toPx()
            val inset = stroke / 2
            // 底环：很淡；前景弧：转圈（-90° 起，扫 260°）
            drawArc(
                color = colors.artifactIcon.copy(alpha = 0.22f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = stroke,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                ),
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(
                    this.size.width - stroke,
                    this.size.height - stroke,
                ),
            )
            drawArc(
                color = colors.artifactIcon,
                startAngle = angle - 90f,
                sweepAngle = 260f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = stroke,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                ),
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(
                    this.size.width - stroke,
                    this.size.height - stroke,
                ),
            )
        }
    }
}

@Composable
fun RunGlyphButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 28.dp,
) {
    val colors = ClaudeTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(colors.artifactIcon.copy(alpha = if (colors.isDark) 0.26f else 0.14f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(size * 0.48f)) {
            val w = this.size.width
            val h = this.size.height
            val pts = listOf(
                androidx.compose.ui.geometry.Offset(w * 0.24f, h * 0.07f),
                androidx.compose.ui.geometry.Offset(w * 0.95f, h * 0.50f),
                androidx.compose.ui.geometry.Offset(w * 0.24f, h * 0.93f),
            )
            val radius = w * 0.16f
            fun unit(from: androidx.compose.ui.geometry.Offset, to: androidx.compose.ui.geometry.Offset): androidx.compose.ui.geometry.Offset {
                val dx = to.x - from.x
                val dy = to.y - from.y
                val len = kotlin.math.hypot(dx, dy)
                return if (len == 0f) androidx.compose.ui.geometry.Offset.Zero
                else androidx.compose.ui.geometry.Offset(dx / len, dy / len)
            }
            val path = androidx.compose.ui.graphics.Path()
            pts.forEachIndexed { i, cur ->
                val prev = pts[(i + pts.size - 1) % pts.size]
                val next = pts[(i + 1) % pts.size]
                val a = cur + unit(cur, prev) * radius
                val b = cur + unit(cur, next) * radius
                if (i == 0) path.moveTo(a.x, a.y) else path.lineTo(a.x, a.y)
                path.quadraticBezierTo(cur.x, cur.y, b.x, b.y)
            }
            path.close()
            drawPath(path = path, color = colors.artifactIcon)
        }
    }
}

/** Artifact 复制：官方堆叠方形图标（与消息操作条同一枚）。 */
@Composable
fun CopyGlyphButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = ClaudeTheme.colors.artifactIcon,
) {
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(RoundedCornerShape(7.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.cds_icon_copy),
            contentDescription = "复制",
            modifier = Modifier.size(16.dp),
            colorFilter = ColorFilter.tint(tint),
        )
    }
}

@Composable
private fun CheckBox(checked: Boolean, tint: Color, borderColor: Color) {
    Box(
        modifier = Modifier
            .padding(top = 3.dp)
            .size(16.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (checked) tint else Color.Transparent)
            .border(1.5.dp, if (checked) tint else borderColor, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(10.dp)) {
                val w = size.width
                val h = size.height
                val stroke = 1.6.dp.toPx()
                drawLine(
                    color = Color.White,
                    start = androidx.compose.ui.geometry.Offset(w * 0.12f, h * 0.52f),
                    end = androidx.compose.ui.geometry.Offset(w * 0.42f, h * 0.82f),
                    strokeWidth = stroke,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
                drawLine(
                    color = Color.White,
                    start = androidx.compose.ui.geometry.Offset(w * 0.42f, h * 0.82f),
                    end = androidx.compose.ui.geometry.Offset(w * 0.88f, h * 0.18f),
                    strokeWidth = stroke,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
            }
        }
    }
}

sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullets(val items: List<ListItem>) : MdBlock
    data class Numbers(val items: List<ListItem>) : MdBlock
    data class Tasks(val items: List<TaskItem>) : MdBlock
    data class Quote(val text: String) : MdBlock
    data object Divider : MdBlock
    data class Code(val lang: String, val code: String) : MdBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MdBlock
}

data class ListItem(val text: String, val level: Int = 0)
data class TaskItem(val text: String, val checked: Boolean, val level: Int = 0)

private fun parseRow(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

private fun isTableSeparator(line: String): Boolean {
    val t = line.trim()
    if (!t.contains("-")) return false
    return t.replace("|", "").replace("-", "").replace(":", "").trim().isEmpty()
}

private val DIVIDER = Regex("^\\s*(-{3,}|\\*{3,}|_{3,})\\s*$")
private val TASK = Regex("^\\s*[-*+]\\s+\\[([ xX])]\\s+(.*)$")
private val BULLET = Regex("^\\s*[-*+]\\s+(.*)$")
private val NUMBER = Regex("^\\s*\\d+[.)]\\s+(.*)$")

private fun indentLevel(line: String): Int = (line.length - line.trimStart().length) / 2

@Composable
private fun rememberParseMarkdown(text: String): List<MdBlock> {
    // 解析放后台线程：流式时每 50ms 换一次 text，长代码块在主线程解析会把 UI 卡住。
    // 首次值同步算（保证第一帧就有内容），之后异步替换，不闪空。
    val state = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(parseMarkdown(text))
    }
    androidx.compose.runtime.LaunchedEffect(text) {
        state.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            parseMarkdown(text)
        }
    }
    return state.value
}


/** 引用小标签：[1] → 圆角小气泡（Claude 橙，替代旧版的搜索蓝）。 */
private val CITE_REGEX = Regex("""\[(\d{1,2})\]""")

/**
 * 已经内联解析好的文本 + 行内占位符（引用气泡）。
 */
internal class InlineOut(
    val text: AnnotatedString,
    val contents: Map<String, InlineTextContent>,
)

private val CITE_ORANGE = Color(0xFFD97757)

private fun withCitations(src: AnnotatedString, cite: Color = CITE_ORANGE): InlineOut {
    if (!CITE_REGEX.containsMatchIn(src.text)) return InlineOut(src, emptyMap())
    val out = AnnotatedString.Builder()
    val map = mutableMapOf<String, InlineTextContent>()
    var i = 0
    for (m in CITE_REGEX.findAll(src.text)) {
        if (m.range.first > i) out.append(src.subSequence(i, m.range.first))
        val n = m.groupValues[1]
        val id = "cite$n"
        map.getOrPut(id) {
            InlineTextContent(
                androidx.compose.ui.text.Placeholder(
                    width = 16.sp,
                    height = 16.sp,
                    placeholderVerticalAlign = androidx.compose.ui.text.PlaceholderVerticalAlign.Center,
                )
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(15.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(cite.copy(alpha = 0.16f)),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    androidx.compose.material3.Text(
                        text = n,
                        fontSize = 9.sp,
                        lineHeight = 9.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = cite,
                    )
                }
            }
        }
        out.appendInlineContent(id, n)
        i = m.range.last + 1
    }
    if (i < src.length) out.append(src.subSequence(i, src.length))
    return InlineOut(out.toAnnotatedString(), map)
}

fun parseMarkdown(src: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val lines = src.replace("\r\n", "\n").split("\n")
    val para = StringBuilder()
    val bullets = mutableListOf<ListItem>()
    val numbers = mutableListOf<ListItem>()
    val tasks = mutableListOf<TaskItem>()
    val quote = StringBuilder()

    fun flushPara() {
        if (para.isNotBlank()) out += MdBlock.Paragraph(para.toString().trim())
        para.clear()
    }

    fun flushQuote() {
        if (quote.isNotBlank()) out += MdBlock.Quote(quote.toString().trim())
        quote.clear()
    }

    fun flushLists() {
        if (bullets.isNotEmpty()) { out += MdBlock.Bullets(bullets.toList()); bullets.clear() }
        if (numbers.isNotEmpty()) { out += MdBlock.Numbers(numbers.toList()); numbers.clear() }
        if (tasks.isNotEmpty()) { out += MdBlock.Tasks(tasks.toList()); tasks.clear() }
    }

    fun flushAll() { flushPara(); flushLists(); flushQuote() }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()

        if (trimmed.startsWith("```")) {
            flushAll()
            val lang = trimmed.removePrefix("```").trim()
            val buf = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                buf.appendLine(lines[i]); i++
            }
            out += MdBlock.Code(lang, buf.toString().trimEnd())
            i++
            continue
        }

        if (trimmed.startsWith("|") && i + 1 < lines.size && isTableSeparator(lines[i + 1])) {
            flushAll()
            val headers = parseRow(trimmed)
            i += 2
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                rows += parseRow(lines[i].trimStart())
                i++
            }
            out += MdBlock.Table(headers, rows)
            continue
        }

        if (DIVIDER.matches(line)) {
            flushAll()
            out += MdBlock.Divider
            i++
            continue
        }

        val taskMatch = TASK.find(line)
        val bulletMatch = if (taskMatch == null) BULLET.find(line) else null
        val numberMatch = if (taskMatch == null && bulletMatch == null) NUMBER.find(line) else null

        when {
            trimmed.isBlank() -> flushAll()

            trimmed.startsWith(">") -> {
                flushPara(); flushLists()
                quote.appendLine(trimmed.removePrefix(">").trimStart())
            }

            trimmed.startsWith("#") -> {
                flushAll()
                val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 3)
                out += MdBlock.Heading(level, trimmed.drop(level).trim())
            }

            taskMatch != null -> {
                flushPara(); flushQuote()
                if (bullets.isNotEmpty()) { out += MdBlock.Bullets(bullets.toList()); bullets.clear() }
                if (numbers.isNotEmpty()) { out += MdBlock.Numbers(numbers.toList()); numbers.clear() }
                tasks += TaskItem(
                    text = taskMatch.groupValues[2],
                    checked = taskMatch.groupValues[1].lowercase() == "x",
                    level = indentLevel(line),
                )
            }

            bulletMatch != null -> {
                flushPara(); flushQuote()
                if (tasks.isNotEmpty()) { out += MdBlock.Tasks(tasks.toList()); tasks.clear() }
                if (numbers.isNotEmpty()) { out += MdBlock.Numbers(numbers.toList()); numbers.clear() }
                bullets += ListItem(bulletMatch.groupValues[1], indentLevel(line))
            }

            numberMatch != null -> {
                flushPara(); flushQuote()
                if (tasks.isNotEmpty()) { out += MdBlock.Tasks(tasks.toList()); tasks.clear() }
                if (bullets.isNotEmpty()) { out += MdBlock.Bullets(bullets.toList()); bullets.clear() }
                numbers += ListItem(numberMatch.groupValues[1], indentLevel(line))
            }

            else -> { flushLists(); flushQuote(); para.appendLine(line) }
        }
        i++
    }
    flushAll()
    return out
}

/** 行内解析：**粗体**、*斜体*、`代码`、~~删除线~~、[文字](链接)。 */
fun inline(src: String, base: Color, accent: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < src.length) {
        val rest = src.substring(i)
        when {
            rest.startsWith("**") -> {
                val end = rest.indexOf("**", 2)
                if (end > 0) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(rest.substring(2, end)) }
                    i += end + 2
                } else if (rest.length > 2) {
                    // 未闭合（流式还没写完）：丢掉标记，正文照常显示，别把 ** 当字面量闪出来
                    append(rest.substring(2).trimEnd('*')); i = src.length
                } else { append(src[i]); i++ }
            }

            rest.startsWith("~~") -> {
                val end = rest.indexOf("~~", 2)
                if (end > 0) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(rest.substring(2, end))
                    }
                    i += end + 2
                } else if (rest.length > 2) {
                    append(rest.substring(2).trimEnd('~')); i = src.length
                } else { append(src[i]); i++ }
            }

            rest.startsWith("`") -> {
                val end = rest.indexOf('`', 1)
                if (end > 0) {
                    withStyle(SpanStyle(fontFamily = JetBrainsMono, fontSize = 14.sp, color = accent)) {
                        append(rest.substring(1, end))
                    }
                    i += end + 1
                } else if (rest.length > 1) {
                    // 行内代码还没闭合：先按正文显示，不要露出反引号
                    append(rest.substring(1)); i = src.length
                } else { append(src[i]); i++ }
            }

            rest.startsWith("[") -> {
                val close = rest.indexOf(']')
                val open = if (close > 0) rest.indexOf('(', close) else -1
                val endUrl = if (open > 0) rest.indexOf(')', open) else -1
                if (close > 0 && open == close + 1 && endUrl > open) {
                    val label = rest.substring(1, close)
                    val url = rest.substring(open + 1, endUrl)
                    runCatching {
                        withLink(
                            LinkAnnotation.Url(
                                url = url,
                                styles = TextLinkStyles(
                                    style = SpanStyle(color = accent, textDecoration = TextDecoration.Underline),
                                ),
                            ),
                        ) { append(label) }
                    }.onFailure { append(label) }
                    i += endUrl + 1
                } else { append(src[i]); i++ }
            }

            rest.startsWith("*") -> {
                val end = rest.indexOf('*', 1)
                if (end > 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(rest.substring(1, end))
                    }
                    i += end + 1
                } else { append(src[i]); i++ }
            }

            else -> { append(src[i]); i++ }
        }
    }
}
