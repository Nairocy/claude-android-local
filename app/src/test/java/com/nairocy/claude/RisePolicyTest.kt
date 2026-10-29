package com.nairocy.claude

import com.nairocy.claude.ui.components.RiseSpec
import com.nairocy.claude.ui.components.riseAlpha
import com.nairocy.claude.ui.components.riseDelayMs
import com.nairocy.claude.ui.components.riseEased
import com.nairocy.claude.ui.components.riseTokenEnds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 浮升节奏（照 `流式对话 · 浮升文字.html` 的示例）：切词 + 出字间隔 + 透明度关键帧。
 * 这三条是纯函数，改参数后先跑这里，别上真机猜。
 */
class RisePolicyTest {

    private fun tokens(s: String): List<String> {
        val ends = riseTokenEnds(s)
        var prev = 0
        return ends.map { e -> s.substring(prev, e).also { prev = e } }
    }

    @Test
    fun chineseSplitsPerCharacter() {
        assertEquals(listOf("你", "好", "世", "界"), tokens("你好世界"))
    }

    @Test
    fun latinSplitsPerWordAndKeepsTrailingSpace() {
        assertEquals(listOf("hello ", "world"), tokens("hello world"))
        // 小数点会单独成 token（3 个字各浮升一次，视觉上无差别）
        assertEquals(listOf("Claude ", "5", ".", "5"), tokens("Claude 5.5"))
    }

    @Test
    fun punctuationIsItsOwnToken() {
        // 中文按「字」：标点自己一个 token，才能挂停顿
        assertEquals(listOf("你", "好", "，", "世", "界", "。"), tokens("你好，世界。"))
    }

    @Test
    fun tokensCoverWholeText() {
        val src = "用 `code` 试一下：**粗体** and English words!"
        val ends = riseTokenEnds(src)
        assertEquals(src.length, ends.last())
        var prev = 0
        for (e in ends) {
            assertTrue("token 必须非空", e > prev)
            prev = e
        }
    }

    @Test
    fun baseDelayMatchesExampleRate() {
        // 示例 (80 + rand*50) * .5 = 40–65ms
        for (seed in 1..40) {
            val d = riseDelayMs('a', 0, seed)
            assertTrue("base=$d", d in RiseSpec.BASE_MS..(RiseSpec.BASE_MS + RiseSpec.JITTER_MS))
        }
    }

    @Test
    fun punctuationAddsPauses() {
        val base = riseDelayMs('a', 0, 5)
        assertEquals(base + RiseSpec.PAUSE_COMMA_MS, riseDelayMs('，', 0, 5))
        assertEquals(base + RiseSpec.PAUSE_STOP_MS, riseDelayMs('。', 0, 5))
        assertEquals(base + RiseSpec.PAUSE_PARA_MS, riseDelayMs('\n', 0, 5))
    }

    @Test
    fun backlogSpeedsUpOnlyWhenFastOutputOn() {
        val slow = riseDelayMs('a', 0, 1)
        val fixed = riseDelayMs('a', 40, 1, fast = false)
        assertEquals("固定速率下积压也不提速", slow, fixed)
        val fast = riseDelayMs('a', 40, 1, fast = true)
        assertTrue("快速输出要明显更快：$slow → $fast", fast < slow / 3)
        assertTrue("再快也不能低于下限", riseDelayMs('。', 500, 1, fast = true) >= RiseSpec.MIN_STEP_MS)
    }

    @Test
    fun alphaKeyframesFollowExample() {
        assertEquals(0f, riseAlpha(0f), 0.001f)
        assertEquals(0.9f, riseAlpha(0.45f), 0.001f)
        assertEquals(1f, riseAlpha(1f), 0.001f)
        assertTrue(riseAlpha(0.2f) < 0.9f)
    }

    @Test
    fun easingReachesFullProgressAfterDuration() {
        val appear = 1_000_000_000L
        assertEquals(0f, riseEased(appear, appear), 0.001f)
        assertEquals(1f, riseEased(appear + RiseSpec.DURATION_MS.toLong() * 1_000_000L, appear), 0.001f)
    }
}
