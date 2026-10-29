package com.nairocy.claude.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nairocy.claude.R
import com.nairocy.claude.data.Attachment
import com.nairocy.claude.ui.motion.pressScale
import androidx.compose.animation.togetherWith
import com.nairocy.claude.ui.theme.AnthropicSans
import com.nairocy.claude.ui.theme.ClaudeTheme
import java.io.File

/**
 * Claude 输入条：全圆角、白底、1px 细边、左右三个圆形按钮。
 * 键盘弹起（获得焦点）后变成两行：上面输入、下面「+ / 模型 / 发送」。
 */
@Composable
fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "与 Claude 对话...",
    images: List<String> = emptyList(),
    attachments: List<Attachment> = emptyList(),
    imageDir: File? = null,
    onRemoveAttachment: (String) -> Unit = {},
    onPickImage: () -> Unit = {},
    onRemoveImage: (String) -> Unit = {},
    onMicClick: () -> Unit = {},
    /** 模型正在输出：主按钮换成「停止」，点了调 onStop。 */
    streaming: Boolean = false,
    onStop: () -> Unit = {},
    /** 无痕对话：外框换虚线（官方无痕页就是虚线胶囊）。 */
    dashedBorder: Boolean = false,
    /** 正在编辑历史消息：胶囊变圆角矩形，顶部多一条「正在编辑消息」 */
    editing: Boolean = false,
    onCancelEdit: () -> Unit = {},
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    /** 左下角的模型胶囊文字，如「Sonnet 5.5 高」 */
    modelLabel: String = "",
    onModelClick: () -> Unit = {},
    /** 输入法是否弹起：只有键盘在的时候才变两行（收起键盘即使还有焦点也回去） */
    keyboardVisible: Boolean = false,
    /**
     * 是否显示左侧「+」附件键。Code 页没有附件，传 false ——
     * 顶部动作行与底部动作行**两处**都受它控制，否则会有一边还挂着按钮。
     */
    showPlus: Boolean = true,
) {
    val colors = ClaudeTheme.colors
    val hasText = value.isNotBlank()
    var focused by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(editing) {
        if (editing) focusRequester?.requestFocus()
    }

    Column(modifier = modifier) {

        val expanded = keyboardVisible
        // 有附件时输入框一定是个大圆角矩形（胶囊形放不下 93dp 的卡片）
        val hasAttachments = images.isNotEmpty() || attachments.isNotEmpty()
        // 胶囊 ↔ 圆角卡片：半径做连续过渡（28dp 就是 56dp 胶囊的半高），不要硬切
        val radius by androidx.compose.animation.core.animateDpAsState(
            targetValue = if (editing || expanded || hasAttachments) 24.dp else 28.dp,
            animationSpec = androidx.compose.animation.core.tween(
                com.nairocy.claude.ui.motion.Motion.SMALL_MS,
                easing = com.nairocy.claude.ui.motion.Motion.Standard,
            ),
            label = "composerRadius",
        )
        val shape = RoundedCornerShape(radius)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    // 键盘弹起时是白卡片 + 投影，不是贴在背景上的胶囊
                    if (expanded && !dashedBorder) Modifier.shadow(10.dp, shape) else Modifier
                )
                .clip(shape)
                .background(colors.card)
                .then(
                    if (dashedBorder) Modifier.drawBehind {
                        // 虚线胶囊：段/间隔各 4.5dp，描边 1dp；内缩半个描边避免被 clip 切掉一半
                        val sw = 1.dp.toPx()
                        val r = if (editing || hasAttachments) 24.dp.toPx() else (size.height - sw) / 2
                        drawRoundRect(
                            color = colors.line,
                            topLeft = Offset(sw / 2, sw / 2),
                            size = Size(size.width - sw, size.height - sw),
                            cornerRadius = CornerRadius(r),
                            style = Stroke(
                                width = sw,
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(4.5.dp.toPx(), 4.5.dp.toPx()), 0f
                                ),
                            ),
                        )
                    } else Modifier.border(1.dp, colors.line, shape)
                ),
        ) {
            // 官方样式：附件在输入框内部最上方（不是浮在框外面）
            if (images.isNotEmpty() || attachments.isNotEmpty()) {
                AttachmentStrip(
                    images = images,
                    attachments = attachments,
                    imageDir = imageDir,
                    onRemoveImage = onRemoveImage,
                    onRemoveAttachment = onRemoveAttachment,
                )
            }
            if (editing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_edit),
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        colorFilter = ColorFilter.tint(colors.tertiaryText),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = "正在编辑消息",
                        fontFamily = AnthropicSans,
                        fontSize = 12.sp,
                        color = colors.tertiaryText,
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onCancelEdit),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = "取消编辑",
                            modifier = Modifier.size(11.dp),
                            colorFilter = ColorFilter.tint(colors.tertiaryText),
                        )
                    }
                }
            }

            val sendButton: @Composable () -> Unit = {
                // 发送键 ↔ 停止键：淡入淡出 + 轻微缩放，不做硬切
                androidx.compose.animation.AnimatedContent(
                    targetState = streaming,
                    transitionSpec = {
                        (
                            androidx.compose.animation.fadeIn(
                                androidx.compose.animation.core.tween(160, easing = com.nairocy.claude.ui.motion.Motion.Standard)
                            ) + androidx.compose.animation.scaleIn(
                                initialScale = 0.82f,
                                animationSpec = androidx.compose.animation.core.tween(
                                    200,
                                    easing = com.nairocy.claude.ui.motion.Motion.Decel,
                                ),
                            )
                            ) togetherWith (
                            androidx.compose.animation.fadeOut(
                                androidx.compose.animation.core.tween(120)
                            ) + androidx.compose.animation.scaleOut(
                                targetScale = 0.82f,
                                animationSpec = androidx.compose.animation.core.tween(140),
                            )
                            )
                    },
                    label = "sendMorph",
                ) { isStreaming ->
                    if (isStreaming) {
                        CircleIconButton(
                            iconRes = null,
                            onClick = onStop,
                            bg = colors.brand,
                            tint = colors.surface,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(colors.surface),
                            )
                        }
                    } else {
                        val canSend = hasText || images.isNotEmpty() || attachments.isNotEmpty()
                        // 可用/禁用之间连颜色也过渡，别闪一下
                        val sendBg by androidx.compose.animation.animateColorAsState(
                            targetValue = if (canSend) colors.brand else colors.surfaceVariant,
                            animationSpec = androidx.compose.animation.core.tween(180),
                            label = "sendBg",
                        )
                        val sendTint by androidx.compose.animation.animateColorAsState(
                            targetValue = if (canSend) colors.surface else colors.tertiaryText,
                            animationSpec = androidx.compose.animation.core.tween(180),
                            label = "sendTint",
                        )
                        CircleIconButton(
                            iconRes = R.drawable.ic_send_arrow,
                            onClick = onSend,
                            bg = sendBg,
                            tint = sendTint,
                            enabled = canSend,
                            hapticKind = com.nairocy.claude.data.Haptics.Kind.SEND,
                        )
                    }
                }
            }

            val plusButton: @Composable () -> Unit = {
                CircleIconButton(
                    iconRes = null,
                    onClick = onPickImage,
                    bg = if (colors.isDark) colors.surfaceVariant else Color(0xFFF4F4F3),
                    tint = colors.onSurface,
                ) { PlusIcon(colors.onSurface) }
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    fontFamily = AnthropicSans,
                    fontSize = 16.sp,
                    color = colors.onSurface,
                ),
                cursorBrush = SolidColor(colors.brand),
                maxLines = 5,
                // 回车 = 换行（用户要求）：只有点发送键才发送，不再把回车当发送
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (focusRequester != null) Modifier.focusRequester(focusRequester)
                        else Modifier
                    )
                    .onFocusChanged { state -> focused = state.isFocused },
                decorationBox = { inner ->
                    // 展开/收起由**同一个进度 p** 驱动：侧边按钮同步「收窄 + 淡出 + 缩小」，
                    // 底部动作行同步「增高 + 淡入 + 上浮」。任何时刻只有一个运动来源，
                    // 不会出现「高度先塌陷、按钮再消失」的两段式观感（上一版就是这个问题）。
                    val p by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (expanded) 1f else 0f,
                        animationSpec = androidx.compose.animation.core.tween(
                            com.nairocy.claude.ui.motion.Motion.SMALL_MS,
                            easing = if (expanded) com.nairocy.claude.ui.motion.Motion.Decel
                            else com.nairocy.claude.ui.motion.Motion.Accel,
                        ),
                        label = "composerExpand",
                    )
                    Column {
                        // 两态共用同一个 BasicTextField（不复制输入框，光标/输入法连接不会重建）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = if (editing) 46.dp else 56.dp)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(if (showPlus) 40.dp * (1f - p) else 0.dp)
                                    .graphicsLayer {
                                        alpha = if (showPlus) 1f - p else 0f
                                        scaleX = 1f - p
                                        scaleY = 1f - p
                                    },
                            ) { if (showPlus) plusButton() }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = if (showPlus) 8.dp * p else 0.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (value.isEmpty()) {
                                    Text(
                                        text = placeholder,
                                        fontFamily = AnthropicSans,
                                        fontSize = 16.sp,
                                        color = colors.tertiaryText,
                                    )
                                }
                                inner()
                            }
                            Box(
                                modifier = Modifier
                                    .width(40.dp * (1f - p))
                                    .graphicsLayer {
                                        alpha = 1f - p
                                        scaleX = 1f - p
                                        scaleY = 1f - p
                                    },
                            ) { sendButton() }
                        }
                        // 底部动作行：随 p 增高（内容用 requiredHeight 撑住，不被压扁），整体淡入上浮
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp * p)
                                .clipToBounds(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .requiredHeight(50.dp)
                                    .padding(horizontal = 8.dp)
                                    .graphicsLayer {
                                        alpha = p
                                        translationY = (1f - p) * 14.dp.toPx()
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (showPlus) plusButton()
                                if (modelLabel.isNotBlank()) {
                                    ModelChip(modelLabel, onClick = onModelClick)
                                }
                                Spacer(Modifier.weight(1f))
                                sendButton()
                            }
                        }
                    }
                },
            )
        }
    }
}

/** 输入框左下角的模型胶囊，比如「Sonnet 5.5 高」。 */
@Composable
private fun ModelChip(label: String, onClick: () -> Unit) {
    val colors = ClaudeTheme.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (colors.isDark) colors.surfaceVariant else Color(0xFFF2F1EE))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = AnthropicSans,
            fontSize = 14.sp,
            color = colors.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun CircleIconButton(
    iconRes: Int?,
    onClick: () -> Unit,
    bg: Color,
    tint: Color,
    size: Int = 40,
    enabled: Boolean = true,
    /** 触感强度：普通按钮用 TAP，发送键用 SEND（避免两次叠加）。 */
    hapticKind: com.nairocy.claude.data.Haptics.Kind = com.nairocy.claude.data.Haptics.Kind.TAP,
    content: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Box(
        modifier = Modifier
            .size(size.dp)
            .pressScale(interaction)
            .clip(CircleShape)
            .background(bg)
            .then(
                if (enabled) {
                    Modifier.clickable(interactionSource = interaction, indication = null) {
                        com.nairocy.claude.data.Haptics.play(ctx, hapticKind)
                        onClick()
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (content != null) {
            content()
        } else if (iconRes != null) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                colorFilter = ColorFilter.tint(tint),
            )
        }
    }
}

/** 纯加号：原版附件按钮就是一个「+」，不是带气泡的组合图标。 */
@Composable
private fun PlusIcon(tint: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val stroke = 1.7.dp.toPx()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.width * 0.36f
        drawLine(tint, Offset(cx - r, cy), Offset(cx + r, cy), stroke, StrokeCap.Round)
        drawLine(tint, Offset(cx, cy - r), Offset(cx, cy + r), stroke, StrokeCap.Round)
    }
}

/* ---------------- 输入框内的附件条（照抄官方） ---------------- */

/**
 * 输入框内的附件条：
 * - 文件卡 **93×93dp 正方形**、白底、1dp 细边、12dp 圆角；左上类型胶囊、底部文件名（最多两行）；
 * - 图片缩略图同样是 **93dp 正方形**（用户拍板：两者统一 93，不用官方的 66）；
 * - 右上角统一压一枚 20dp 黑色圆 + 白色手绘 ×（约 1/4 出界）；
 * - 横向排列，间距 15dp，距输入框左缘 8dp、顶缘 8dp。
 */
@Composable
private fun AttachmentStrip(
    images: List<String>,
    attachments: List<Attachment>,
    imageDir: File?,
    onRemoveImage: (String) -> Unit,
    onRemoveAttachment: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 2.dp)
            // 93dp 卡片一行放不下 4 张（4×93 + 3×15 ≈ 417dp），多了要能横向滑，别把卡片挤出屏幕
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        images.forEach { name ->
            ImageAttachmentCard(
                name = name,
                imageDir = imageDir,
                onRemove = { onRemoveImage(name) },
            )
        }
        attachments.forEach { att ->
            FileAttachmentCard(
                att = att,
                onRemove = { onRemoveAttachment(att.file) },
            )
        }
    }
}

@Composable
private fun FileAttachmentCard(att: Attachment, onRemove: () -> Unit) {
    val colors = ClaudeTheme.colors
    // 官方卡片是明显可见的细灰边（截图实测 #BBB）；colors.line(#E7E6E1) 太淡
    val outline = if (colors.isDark) Color(0xFF3A3A38) else Color(0xFFC9C7C1)
    // 正方形：93×93（用户要的尺寸，图片缩略图同尺寸）
    Box(modifier = Modifier.size(93.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.card)
                .border(1.dp, outline, RoundedCornerShape(12.dp))
                .padding(horizontal = 9.dp, vertical = 8.dp),
        ) {
            FileTypeChip(extensionOf(att.name))
            Spacer(Modifier.weight(1f))
            Text(
                text = att.name.substringBeforeLast('.').ifBlank { att.name },
                fontFamily = AnthropicSans,
                fontSize = 11.5.sp,
                lineHeight = 14.sp,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AttachmentDeleteBadge(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 5.dp, y = (-5).dp),
        )
    }
}

@Composable
private fun ImageAttachmentCard(name: String, imageDir: File?, onRemove: () -> Unit) {
    val colors = ClaudeTheme.colors
    // 图片缩略图同样是 93dp 正方形（用户要求与文件卡统一）
    Box(modifier = Modifier.size(93.dp)) {
        val bmp = remember(name, imageDir) {
            imageDir?.let { dir ->
                BitmapFactory.decodeFile(File(dir, name).absolutePath)?.asImageBitmap()
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceVariant),
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        AttachmentDeleteBadge(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 5.dp, y = (-5).dp),
        )
    }
}

/** 文件类型胶囊：白底 + 1dp 细边 + 全圆角，文字 9sp 灰色。 */
@Composable
private fun FileTypeChip(ext: String) {
    val colors = ClaudeTheme.colors
    Box(
        modifier = Modifier
            .border(1.dp, colors.line, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 1.5.dp),
    ) {
        Text(
            text = ext.uppercase(),
            fontFamily = AnthropicSans,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            letterSpacing = 0.3.sp,
            color = colors.secondaryText,
        )
    }
}

/** 删除按钮：黑色实心圆 + 白色 ×（手绘矢量，12dp 内径、圆头 1.6dp）。 */
@Composable
private fun AttachmentDeleteBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(Color(0xFF131313))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(9.dp)) {
            val stroke = 1.7.dp.toPx()
            drawLine(
                color = Color.White,
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color.White,
                start = Offset(size.width, 0f),
                end = Offset(0f, size.height),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun extensionOf(name: String): String =
    name.substringAfterLast('.', "").ifBlank { "file" }.take(4)
