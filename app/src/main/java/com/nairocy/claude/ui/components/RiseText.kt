package com.nairocy.claude.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 流式「浮升文字」—— 按 `D:\\Download\\流式对话 · 浮升文字.html` 示例的内容与速率重做。
 *
 * 示例 CSS 原值（示例里 speed 固定 .5，下面括号是乘以 .5 后的实测值）：
 * ```
 * .w { animation: rise 1.5s cubic-bezier(.2,.7,.2,1) forwards }
 * @keyframes rise{
 *   0%  { opacity:0; transform:translateY(16px); color:var(--faint); filter:blur(2.5px) }
 *   45% { opacity:.9; color:var(--mute) }
 *   100%{ opacity:1; transform:translateY(0);    color:var(--ink);   filter:blur(0) }
 * }
 * 出字间隔 (80 + rand*50) * .5 = 40–65ms
 * 逗号/顿号/冒号后 +170*.5 = 85ms；句末 +420*.5 = 210ms；段末 +650*.5 = 325ms
 * ```
 * 中文按「字」浮升（示例按词，中文没有空格，按字节奏更连续）；英文仍按单词。
 *
 * 与示例的两处故意偏离：
 *  - 颜色渐变用透明度近似（0 → .9 → 1）；逐字改色会让每个词每帧重新排版，
 *    在浅色底上「墨色 × alpha」与示例的 faint→mute→ink 视觉几乎一致。
 *  - 后端吐字比示例快时按积压量整体提速（最多落后约 1.5 秒），不做无限堆积。
 */
internal object RiseSpec {
    /** 单个 token 的浮升时长：示例 1.5s。 */
    const val DURATION_MS = 1500f

    /** 示例 cubic-bezier(.2,.7,.2,1)。 */
    val EASING = CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f)

    /** 起始下沉 16px（示例正文字号 16.5px，本 app 17sp，直接按 dp 用）。 */
    const val RISE_DP = 16f

    /** 起始模糊 2.5px。 */
    const val BLUR_DP = 2.5f

    /** 出字基础间隔 40–65ms（示例 80–130 × .5）。 */
    const val BASE_MS = 40L
    const val JITTER_MS = 25

    /** 标点停顿（示例 170/420/650 × .5）。 */
    const val PAUSE_COMMA_MS = 85L
    const val PAUSE_STOP_MS = 210L
    const val PAUSE_PARA_MS = 325L

    /** 积压超过这么多 token 才提速，否则保持示例节奏。 */
    const val LAG_TOKENS = 8

    /** 提速下限，保证长回复的尾巴不会拖太久。 */
    const val MIN_STEP_MS = 6L

    /** 同时在飞的 token 上限（1.5s ÷ 40ms ≈ 38，留点余量）。 */
    const val WINDOW_TOKENS = 48
}

/** 尾块身份：消息 + 块数 + 块内条目。变了就说明「开始写新的一段」，浮升从头来。 */
internal data class RiseBlockKey(val message: Any?, val blockCount: Int, val item: Int) {
    fun storeKey(): String = "$message#$blockCount#$item"
}

/**
 * 已显示的字符数按块存下来：LazyColumn 把这条消息回收再重组时不能从头再浮升一遍
 * （回滚上去看历史再回来，文字不该「重播」）。
 */
internal class RiseRevealStore {
    private val shown = mutableMapOf<String, Int>()
    operator fun get(key: String): Int? = shown[key]
    fun put(key: String, value: Int) {
        shown[key] = value
    }
    fun remove(key: String) {
        shown.remove(key)
    }
}

/**
 * 进程级共享的浮升进度。
 *
 * 以前这个 store 是 `remember { }` 在 ChatScreen 里的：一离开对话页（切设置、回桌面导致
 * Activity 重建）就没了，回来时最后一块从第 0 个字开始重放 —— 长回复要几十秒才追平，
 * 看起来就像「刚才的回答丢了」。
 */
internal val AppRiseRevealStore = RiseRevealStore()

internal val LocalRiseRevealStore = androidx.compose.runtime.compositionLocalOf<RiseRevealStore?> { null }

private const val NANOS_PER_MS = 1_000_000L

/** 渲染后文本里每个 token 的出现时刻（key = token 结束偏移）。 */
private class RiseBooking {
    val appear = HashMap<Int, Long>()
    var prevLen = 0
    var started = false

    fun settleAll(ends: IntArray, at: Long) {
        appear.clear()
        for (e in ends) appear[e] = at
    }

    fun advance(ends: IntArray, newLen: Int, at: Long) {
        for (e in ends) if (e > prevLen && e <= newLen) appear[e] = at
        prevLen = newLen
    }

    /** 截图专用：按示例节奏把出现时刻从最后一个往前回溯。 */
    fun seed(ends: IntArray, src: String, now: Long, tailAgeMs: Long) {
        var t = now - tailAgeMs * NANOS_PER_MS
        var seed = ends.size
        for (i in ends.indices.reversed()) {
            appear[ends[i]] = t
            val prev = if (i > 0) src[ends[i] - 1] else ' '
            t -= riseDelayMs(prev, 0, seed--) * NANOS_PER_MS
        }
        prevLen = src.length
    }
}

/**
 * 尾块渲染：已定格的文字由 Canvas 一次性画出，最后 [RiseSpec.WINDOW_TOKENS] 个 token
 * 各自作为一层做浮升（位移/透明度在绘制阶段算，模糊分成 4 档，不逐帧重组）。
 *
 * 为什么不让 `Text` 自己画：Compose 没法给 AnnotatedString 里的某几个字做位移和模糊，
 * 只能拆成独立图层；为了让拆开后的文字和整段排版**逐像素一致**，先用自己的
 * TextMeasurer 按同一约束排一遍，再按行把 token 的坐标取出来。
 */
@Composable
internal fun RisingInlineText(
    raw: String,
    identity: RiseBlockKey,
    build: (String) -> InlineOut,
    style: TextStyle,
    color: Color,
    /** 这条消息还在流式：true 才逐字浮升；false 直接整段显示（历史消息）。 */
    rise: Boolean,
    modifier: Modifier = Modifier,
    /** 截图测试专用：冻结时钟，把已有 token 按示例节奏回溯成「已经出了」。 */
    testClock: Long? = null,
    /** 截图测试专用：最后一个 token 是多久之前出现的（120 = 正在浮升，5000 = 全部定格）。 */
    testTailAgeMs: Long = 120,
) {
    // ⚠️ 必须和 material3.Text 一样先 merge LocalTextStyle：否则 platformStyle
    // （includeFontPadding）等默认值不一致，自己量的排版会和 Text 差 1px。
    val localStyle = LocalTextStyle.current
    val solid = remember(style, color, localStyle) { localStyle.merge(style).copy(color = color) }

    // 0 = 流式中；1 = 流结束了但最后一个词还在飞；2 = 定格（走普通 Text）
    var phase by remember(identity) { mutableIntStateOf(if (rise || testClock != null) 0 else 2) }
    val store = LocalRiseRevealStore.current
    val storeKey = remember(identity) { identity.storeKey() }
    // 这个块之前显示过（页面切走又回来）：store 里有进度。
    // 回来时直接显示「已经收到的全部内容」，只让之后新到的字继续浮升；
    // 否则要从第 0 个字重放，长回复要几十秒才追平，观感就是「结果丢了」。
    val restored = if (rise && testClock == null) store?.get(storeKey) else null
    var shown by remember(identity) {
        mutableIntStateOf(
            when {
                testClock != null -> raw.length
                restored != null -> raw.length
                rise -> 0
                else -> Int.MAX_VALUE
            }
        )
    }
    val clockState = remember(identity) { mutableLongStateOf(testClock ?: 0L) }
    val rawNow by rememberUpdatedState(raw)
    val riseNow by rememberUpdatedState(rise)
    // 功能页「快速输出」开关：关（默认）＝ 固定速率；开 ＝ 积压时自动提速。
    // Prefs 不是 Compose 状态，所以每次重组都重读一次（SharedPreferences 是内存读，几乎零成本），
    // 再经 rememberUpdatedState 交给逐帧循环，切开关立即生效。
    val appCtx = androidx.compose.ui.platform.LocalContext.current
    val prefsNow = remember(appCtx) { com.nairocy.claude.data.Prefs(appCtx) }
    val fastNow by rememberUpdatedState(prefsNow.fastOutput)

    val shownSafe = shown.coerceIn(0, raw.length)
    val visible = if (shownSafe >= raw.length) raw else raw.substring(0, shownSafe)
    val out = remember(visible) { build(visible) }

    LaunchedEffect(identity) {
        if (testClock != null) return@LaunchedEffect
        if (!riseNow) return@LaunchedEffect

        var ends = IntArray(0)
        var endsFor = -1
        var cursor = 0
        // store 里已经有这个块 = 刚从别的页面 / Activity 重建回来：内容已经整段显示了，
        // 游标要直接落到末尾，别再从头一个字一个字补播。
        var jumped = false
        var dueAt = 0L
        var seed = 1
        var lastAt = 0L
        while (true) {
            val now = withFrameNanos { it }
            clockState.longValue = now
            val text = rawNow
            if (text.length != endsFor) {
                ends = riseTokenEnds(text)
                endsFor = text.length
                if (!jumped && restored != null) {
                    jumped = true
                    cursor = ends.size
                    lastAt = now
                }
            }
            if (cursor < ends.size && now >= dueAt) {
                val end = ends[cursor]
                shown = end
                store?.put(storeKey, end)
                cursor++
                lastAt = now
                dueAt = now + riseDelayMs(text[end - 1], ends.size - cursor, seed++, fastNow) * NANOS_PER_MS
                continue
            }
            // 追平且流已结束：再等最后一个字飞完才定格
            if (cursor >= ends.size && !riseNow) {
                if (now - lastAt >= RiseSpec.DURATION_MS.toLong() * NANOS_PER_MS) {
                    phase = 2
                    store?.remove(storeKey)
                    break
                }
            }
        }
    }

    when {
        // 定格：可选中 / 可长按，与普通消息完全一致
        phase == 2 -> Text(text = out.text, inlineContent = out.contents, style = solid, modifier = modifier)

        // 有引用气泡占位：占位符要由 Text 自己排，浮升不参与（只保留逐字上屏）
        out.contents.isNotEmpty() -> Text(
            text = out.text,
            inlineContent = out.contents,
            style = solid,
            modifier = modifier,
        )

        else -> RiseCanvas(
            text = out.text,
            identity = identity,
            solid = solid,
            clock = clockState,
            testClock = testClock,
            testTailAgeMs = testTailAgeMs,
            modifier = modifier,
        )
    }
}

@Composable
private fun RiseCanvas(
    /** 当前已经放出来的文本（渲染后的，markdown 标记已去掉） */
    text: AnnotatedString,
    identity: RiseBlockKey,
    solid: TextStyle,
    clock: State<Long>,
    testClock: Long?,
    testTailAgeMs: Long,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val maxWidth = constraints.maxWidth
        val measurer = rememberTextMeasurer(cacheSize = 8)

        // ⚠️ token 必须在**渲染后**的文本上切：markdown 标记（** ` ~~）会被 inline() 吃掉，
        // 拿原始 md 的下标去定位渲染文本会整体错位甚至越界。
        val ends = remember(text) { riseTokenEnds(text.text) }
        val booking = remember(identity) { RiseBooking() }
        val settleAge = RiseSpec.DURATION_MS.toLong() * 2 * NANOS_PER_MS

        if (testClock != null) {
            if (!booking.started) {
                booking.seed(ends, text.text, testClock, testTailAgeMs)
                booking.started = true
            }
        } else {
            // 记账用 System.nanoTime()（与 withFrameNanos 同源）。不要在 composition 里读 clock，
            // 否则整块每帧重组一次。
            val now = System.nanoTime()
            val len = text.length
            when {
                !booking.started -> {
                    booking.started = true
                    // 首帧就已经有内容 = 列表回收后重建 / 回滚上来，别整段重播
                    if (ends.isNotEmpty()) booking.settleAll(ends, now - settleAge)
                    booking.prevLen = len
                }

                len < booking.prevLen -> {
                    // 行内链接之类写完会「变短」（[文字](url) 合成标签）：旧偏移作废，全部当作定格
                    booking.settleAll(ends, now - settleAge)
                    booking.prevLen = len
                }

                len > booking.prevLen -> booking.advance(ends, len, now)
            }
        }

        val from = (ends.size - RiseSpec.WINDOW_TOKENS).coerceAtLeast(0)
        val prefixEnd = if (from == 0) 0 else ends[from - 1]

        // 整段按同一约束排一次：行断行、token 坐标都以它为准
        val full = remember(text, maxWidth, solid) {
            measurer.measure(text, solid, constraints = Constraints(maxWidth = maxWidth))
        }
        // 已定格的文字（整段前缀）单独排：内容前缀的断行与整段一致，只是少了尾巴
        val prefix = remember(text, prefixEnd, maxWidth, solid) {
            if (prefixEnd <= 0) null else measurer.measure(
                text.subSequence(0, prefixEnd),
                solid,
                constraints = Constraints(maxWidth = maxWidth),
            )
        }
        val heightDp = with(LocalDensity.current) { full.size.height.toDp() }

        Box(Modifier.fillMaxWidth().height(heightDp)) {
            Canvas(Modifier.fillMaxWidth().height(heightDp)) {
                prefix?.let { drawText(it) }
            }
            if (from < ends.size) {
                val cache = remember { mutableMapOf<Long, TextLayoutResult>() }
                for (k in from until ends.size) {
                    val s = if (k == 0) 0 else ends[k - 1]
                    val e = ends[k]
                    val at = booking.appear[e] ?: continue
                    val l0 = full.getLineForOffset(s)
                    val l1 = full.getLineForOffset((e - 1).coerceAtLeast(s))
                    for (line in l0..l1) {
                        val a = maxOf(s, full.getLineStart(line))
                        val b = minOf(e, full.getLineEnd(line, false))
                        if (b <= a) continue
                        val key = (a.toLong() shl 32) or b.toLong()
                        if (cache.size > 512) cache.clear()
                        val seg = cache.getOrPut(key) {
                            measurer.measure(text.subSequence(a, b), solid, constraints = Constraints())
                        }
                        RiseWord(
                            layout = seg,
                            x = full.getHorizontalPosition(a, true),
                            y = full.getLineBaseline(line) - seg.firstBaseline,
                            appearAt = at,
                            clock = clock,
                        )
                    }
                }
            }
        }
    }
}

/** 单个 token（可能被换行断成多段）的浮升层：位移与透明度在绘制阶段算，模糊 4 档。 */
@Composable
private fun RiseWord(
    layout: TextLayoutResult,
    x: Float,
    y: Float,
    appearAt: Long,
    clock: State<Long>,
) {
    val density = LocalDensity.current
    val bucket by remember(appearAt) {
        derivedStateOf { riseBlurBucket(riseEased(clock.value, appearAt)) }
    }
    val w = with(density) { (layout.size.width + 4).toDp() }
    val h = with(density) { (layout.size.height + 4).toDp() }
    Canvas(
        modifier = Modifier
            .offset { IntOffset((x - 2f).roundToInt(), (y - 2f).roundToInt()) }
            .size(w, h)
            .then(
                if (bucket == 0) Modifier
                else Modifier.blur(riseBlurDp(bucket), BlurredEdgeTreatment.Unbounded)
            )
            .graphicsLayer {
                val p = riseEased(clock.value, appearAt)
                alpha = riseAlpha(p)
                translationY = (1f - p) * RiseSpec.RISE_DP.dp.toPx()
            },
    ) {
        drawText(layout, topLeft = Offset(2f, 2f))
    }
}

// ---------------------------------------------------------------- 纯函数（可单测）

/**
 * 出字节奏：返回「这个 token 出现后，隔多久出下一个」。
 * @param last 刚出的 token 的最后一个字符（标点停顿靠它判断）
 * @param pending 还有多少 token 积压在缓冲里（越多越快，保证不落后网络太远）
 */
internal fun riseDelayMs(last: Char, pending: Int, seed: Int, fast: Boolean = false): Long {
    var ms = RiseSpec.BASE_MS + ((seed * 37) % (RiseSpec.JITTER_MS + 1)).toLong()
    ms += when (last) {
        '，', '、', ',', '：', ':' -> RiseSpec.PAUSE_COMMA_MS
        '。', '！', '？', '；', '.', '!', '?', ';', '…' -> RiseSpec.PAUSE_STOP_MS
        '\n' -> RiseSpec.PAUSE_PARA_MS
        else -> 0L
    }
    // 快速输出（功能页开关）：积压越多放得越快，pending 8 → 原速，pending 40 → 1/5 速。
    // 关闭时严格按示例的固定速率，不管积压多少。
    if (!fast) return ms.coerceAtLeast(RiseSpec.MIN_STEP_MS)
    val factor = if (pending <= RiseSpec.LAG_TOKENS) 1f
    else RiseSpec.LAG_TOKENS.toFloat() / pending.toFloat()
    return (ms * factor).toLong().coerceAtLeast(RiseSpec.MIN_STEP_MS)
}

/**
 * 切 token：中文/日文每字一个 token；拉丁字母数字按单词；标点各自成 token；
 * 空白并入前一个 token（示例里空白是不参与动画的纯文本节点）。
 * 返回每个 token 的结束偏移（升序）。
 */
internal fun riseTokenEnds(text: String): IntArray {
    if (text.isEmpty()) return IntArray(0)
    val out = ArrayList<Int>(text.length / 2 + 4)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            isCjkChar(c) -> {
                out.add(i + 1); i++
            }

            isWordChar(c) -> {
                var j = i + 1
                while (j < text.length && isWordChar(text[j])) j++
                out.add(j); i = j
            }

            c.isWhitespace() -> {
                if (out.isEmpty()) out.add(i + 1) else out[out.size - 1] = i + 1
                i++
            }

            else -> {
                out.add(i + 1); i++
            }
        }
    }
    return out.toIntArray()
}

private fun isCjkChar(c: Char): Boolean =
    c.code in 0x4E00..0x9FFF || c.code in 0x3400..0x4DBF || c.code in 0xF900..0xFAFF ||
        c.code in 0x3040..0x30FF || c.code in 0x3000..0x303F || c.code in 0xFF00..0xFF60

private fun isWordChar(c: Char): Boolean =
    !isCjkChar(c) && (c.isLetterOrDigit() || c == '_' || c == '\'' || c == '’' || c == '-')

/** 线性进度 → 示例的缓动曲线。 */
internal fun riseEased(nowNanos: Long, appearAt: Long): Float {
    val linear = ((nowNanos - appearAt) / (RiseSpec.DURATION_MS * NANOS_PER_MS)).coerceIn(0f, 1f)
    return RiseSpec.EASING.transform(linear)
}

/** 示例 keyframe：0% → 0，45% → .9，100% → 1。 */
internal fun riseAlpha(p: Float): Float =
    if (p <= 0f) 0f
    else if (p < 0.45f) p / 0.45f * 0.9f
    else 0.9f + (p - 0.45f) / 0.55f * 0.1f

private val RISE_BLUR_STEPS = floatArrayOf(0f, 0.7f, 1.5f, RiseSpec.BLUR_DP)

/** 模糊分档（0–3）：只有头几百毫秒是真的糊的，避免逐帧重组。 */
internal fun riseBlurBucket(p: Float): Int {
    val r = RiseSpec.BLUR_DP * (1f - p)
    return when {
        r < 0.25f -> 0
        r < 1.0f -> 1
        r < 1.8f -> 2
        else -> 3
    }
}

private fun riseBlurDp(bucket: Int) = RISE_BLUR_STEPS[bucket.coerceIn(0, 3)].dp
