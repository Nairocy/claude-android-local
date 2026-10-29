package com.nairocy.claude.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nairocy.claude.R
import kotlinx.coroutines.delay

/**
 * 星芒动画：官方是 8 帧 × 125ms 的帧序列（thinking / writing / shimmer 各一套），
 * 资源从 APK 原样搬来，这里按原速播放。
 */
enum class SparkKind { THINKING, WRITING, SHIMMER }

private fun frameRes(kind: SparkKind, i: Int): Int = when (kind) {
    SparkKind.THINKING -> when (i) {
        1 -> R.drawable.claude_spark_thinking1
        2 -> R.drawable.claude_spark_thinking2
        3 -> R.drawable.claude_spark_thinking3
        4 -> R.drawable.claude_spark_thinking4
        5 -> R.drawable.claude_spark_thinking5
        6 -> R.drawable.claude_spark_thinking6
        7 -> R.drawable.claude_spark_thinking7
        else -> R.drawable.claude_spark_thinking8
    }
    SparkKind.WRITING -> when (i) {
        1 -> R.drawable.claude_spark_writing1
        2 -> R.drawable.claude_spark_writing2
        3 -> R.drawable.claude_spark_writing3
        4 -> R.drawable.claude_spark_writing4
        5 -> R.drawable.claude_spark_writing5
        6 -> R.drawable.claude_spark_writing6
        7 -> R.drawable.claude_spark_writing7
        else -> R.drawable.claude_spark_writing8
    }
    SparkKind.SHIMMER -> when (i) {
        1 -> R.drawable.claude_spark_shimmer1
        2 -> R.drawable.claude_spark_shimmer2
        3 -> R.drawable.claude_spark_shimmer3
        4 -> R.drawable.claude_spark_shimmer4
        5 -> R.drawable.claude_spark_shimmer5
        6 -> R.drawable.claude_spark_shimmer6
        else -> R.drawable.claude_spark_shimmer7
    }
}

private fun frameCount(kind: SparkKind) = when (kind) {
    SparkKind.THINKING, SparkKind.WRITING -> 8
    SparkKind.SHIMMER -> 7
}

@Composable
fun SparkAnimation(
    kind: SparkKind,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier,
    /** 每帧时长（ms）。默认 125（官方节奏）；Code 页要求慢一点，传 220。 */
    frameMs: Long = 125L,
) {
    var frame by remember { mutableIntStateOf(1) }
    LaunchedEffect(kind) {
        val n = frameCount(kind)
        while (true) {
            delay(frameMs)
            frame = frame % n + 1
        }
    }
    Image(
        painter = painterResource(frameRes(kind, frame)),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

/**
 * 三点呼吸指示器：Claude 的「思考中」标记。
 * 官方形态是「中间大、两边小」↔「中间小、两边大」整体反相呼吸（不是三点依次起伏）。
 */
@Composable
fun DotsIndicator(
    color: Color,
    modifier: Modifier = Modifier,
    dotSize: Dp = 7.dp,
    gap: Dp = 4.dp,
) {
    val transition = rememberInfiniteTransition(label = "dots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 620, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dotsPhase",
    )
    // phase=0 → 中间最大、两侧最小；phase=1 反之
    val mid = 1f - 0.45f * phase
    val side = 0.55f + 0.45f * phase
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(side, mid, side).forEachIndexed { i, scale ->
            Box(
                modifier = Modifier.size(dotSize),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(dotSize * scale)
                        .background(color, CircleShape),
                )
            }
        }
    }
}

/**
 * 思考中轮换文案：随机出、不连续重复，每 3–5 秒换一条，交叉淡入淡出。
 * 工具执行期间由 [ThinkingRow.overrideText] 覆盖成「搜索以太网... / 抓取页面...」。
 */
private val THINKING_PHRASES = listOf(
    "Claude正在思索...",
    "构建进行中...",
    "推理你的需求...",
    "我正在微调框架...",
    "我将润色文本...",
    "让我解析信息...",
)

/**
 * 思考指示行：`[橙点动画] 思考文案 ›`。
 *
 * @param overrideText 非空时固定显示这句（搜索/抓取中），不参与轮换。
 */
@Composable
fun ThinkingRow(
    modifier: Modifier = Modifier,
    overrideText: String? = null,
    animated: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val colors = com.nairocy.claude.ui.theme.ClaudeTheme.colors
    var phrase by remember { mutableStateOf(THINKING_PHRASES.random()) }
    LaunchedEffect(overrideText) {
        if (overrideText != null) return@LaunchedEffect
        while (true) {
            delay(kotlin.random.Random.nextLong(3_000, 5_001))
            // 换一条不同的话，避免随机到同一条看起来像没动
            phrase = THINKING_PHRASES.filterNot { it == phrase }.random()
        }
    }
    val shown = overrideText ?: phrase

    Row(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (animated) {
            DotsIndicator(color = Color(0xFFD97757))
        }
        androidx.compose.animation.Crossfade(
            targetState = shown,
            animationSpec = tween(durationMillis = 320),
            label = "thinkingText",
        ) { text ->
            Text(
                text = text,
                fontFamily = com.nairocy.claude.ui.theme.AnthropicSans,
                fontSize = 14.sp,
                color = colors.tertiaryText,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                // 固定最小宽度：长短句切换时文字不左右跳
                modifier = Modifier.widthIn(min = 140.dp),
            )
        }
    }
}

/**
 * 消息操作条：复制 / 分享 / 朗读 / 赞 / 踩 / 重生成。
 * 只做有真实行为的项，没实现的不摆（避免假按钮）。
 */
@Composable
fun MessageActions(
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onLike: () -> Unit = {},
    onDislike: () -> Unit = {},
    modifier: Modifier = Modifier,
    /**
     * 赞/踩的**装饰态**：1 = 赞，-1 = 踩，0 = 都没选。
     * 只决定图标是不是砖红实心，不上报服务端（用户 2026-10-29 要求「装饰向」）。
     */
    vote: Int = 0,
    onVote: (Int) -> Unit = {},
) {
    val colors = com.nairocy.claude.ui.theme.ClaudeTheme.colors
    val base = colors.tertiaryText
    val liked = vote == 1
    val disliked = vote == -1
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionIcon(onCopy, base) { ActionGlyph(R.drawable.cds_icon_copy, "复制", it) }
        VoteIcon(
            icon = R.drawable.cds_icon_thumb_up,
            label = "赞",
            selected = liked,
            onClick = {
                onVote(if (liked) 0 else 1)
                if (!liked) onLike()
            },
        )
        VoteIcon(
            icon = R.drawable.cds_icon_thumb_down,
            label = "踩",
            selected = disliked,
            onClick = {
                onVote(if (disliked) 0 else -1)
                if (!disliked) onDislike()
            },
        )
        ActionIcon(onRegenerate, base) {
            Image(
                painter = painterResource(R.drawable.cds_icon_arrow_clockwise),
                contentDescription = "重新生成",
                modifier = Modifier.size(16.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(it),
            )
        }
    }
}

/**
 * 赞 / 踩：选中后图标**砖红填充**（颜色 180ms 过渡 + 轻微放大），再点一次取消。
 * 两个互斥（由调用方的 vote 单值保证）。
 */
@Composable
private fun VoteIcon(
    @androidx.annotation.DrawableRes icon: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = com.nairocy.claude.ui.theme.ClaudeTheme.colors
    val tint by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) colors.brand else colors.tertiaryText,
        animationSpec = androidx.compose.animation.core.tween(180),
        label = "voteTint",
    )
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = com.nairocy.claude.ui.motion.Motion.pressSpring(),
        label = "voteScale",
    )
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable {
                com.nairocy.claude.data.Haptics.play(ctx, com.nairocy.claude.data.Haptics.Kind.TAP)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = label,
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint),
        )
    }
}

@Composable
private fun ActionIcon(onClick: () -> Unit, tint: Color, content: @Composable (Color) -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content(tint) }
}

@Composable
private fun ActionGlyph(@androidx.annotation.DrawableRes res: Int, label: String, tint: Color) {
    Image(
        painter = painterResource(res),
        contentDescription = label,
        modifier = Modifier.size(16.dp),
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint),
    )
}
