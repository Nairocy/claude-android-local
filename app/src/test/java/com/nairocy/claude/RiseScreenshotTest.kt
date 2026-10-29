package com.nairocy.claude

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.nairocy.claude.ui.components.InlineOut
import com.nairocy.claude.ui.components.RiseBlockKey
import com.nairocy.claude.ui.components.RisingInlineText
import com.nairocy.claude.ui.components.inline
import com.nairocy.claude.ui.theme.AnthropicSerif
import com.nairocy.claude.ui.theme.ClaudeTheme
import org.junit.Rule
import org.junit.Test

/**
 * 浮升文字的静态证据：时钟冻结在某一刻，最后几个字应该正在上升（下沉 + 半透明 + 模糊），
 * 前面的字已经定格。用来确认「拆成独立图层后的排版与实际 Text 一致」，不跑真机也能看。
 */
class RiseScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        theme = "android:Theme.Material.Light.NoActionBar",
        showSystemUi = false,
    )

    private val sample =
        "慢，并不是效率的反面，而是注意力的一种形式。当文字以恰到好处的节奏出现时，" +
            "读者的目光会自然地跟随它前进，每一个词都有机会被真正看见，而不是被匆忙地扫过。" +
            "好的流式输出，更像是一位朋友在你对面斟酌着开口。"

    private val MD_SAMPLE =
        "这里是**重点结论**，还有 `inline code` 与**混排**的内容一起浮升，" +
            "最后一句故意写到一半不要句号"

    @Test
    fun riseMidFlightLight() = shot("rise_midflight_light", dark = false)

    @Test
    fun riseMidFlightDark() = shot("rise_midflight_dark", dark = true)

    private fun shot(name: String, dark: Boolean, tailAgeMs: Long = 120, raw: String = sample) {
        paparazzi.snapshot(name = name) {
            ClaudeTheme(darkTheme = dark) {
                val colors = ClaudeTheme.colors
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.surface)
                        .padding(16.dp)
                ) {
                    RisingInlineText(
                        raw = raw,
                        identity = RiseBlockKey("shot", 1, 0),
                        build = { s -> InlineOut(inline(s, colors.onSurface, colors.brand), emptyMap()) },
                        style = TextStyle(
                            fontFamily = AnthropicSerif,
                            fontSize = 17.sp,
                            lineHeight = 27.sp,
                        ),
                        color = colors.onSurface,
                        rise = true,
                        // 冻结时钟：最后 40 个字左右按示例节奏往回排
                        testClock = 10_000_000_000L,
                        testTailAgeMs = tailAgeMs,
                    )
                }
            }
        }
    }

    @Test
    fun riseSettled() = shot("rise_settled_light", dark = false, tailAgeMs = 5_000)

    /** 行内 markdown（粗体/行内代码）+ 写到一半的段落：验证渲染后文本的 token 定位。 */
    @Test
    fun riseMarkdownPartialLight() = shot(
        "rise_markdown_partial_light",
        dark = false,
        tailAgeMs = 120,
        raw = MD_SAMPLE,
    )

    /** 行内 markdown 全部定格：应当与普通 Text 逐像素一致（对照组见 plainMarkdown）。 */
    @Test
    fun riseMarkdownSettled() = shot(
        "rise_markdown_settled_light",
        dark = false,
        tailAgeMs = 5_000,
        raw = MD_SAMPLE,
    )

    @Test
    fun plainMarkdown() = paparazzi.snapshot(name = "rise_plain_markdown_light") {
        ClaudeTheme(darkTheme = false) {
            val colors = ClaudeTheme.colors
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.surface)
                    .padding(16.dp)
            ) {
                androidx.compose.material3.Text(
                    text = inline(MD_SAMPLE, colors.onSurface, colors.brand),
                    fontFamily = AnthropicSerif,
                    fontSize = 17.sp,
                    lineHeight = 27.sp,
                    color = colors.onSurface,
                )
            }
        }
    }

    /** 对照组：同一段文字用普通 Text 画，和 rise_settled_light 比像素，验证拆图层不改排版。 */
    @Test
    fun plainText() = paparazzi.snapshot(name = "rise_plain_light") {
        ClaudeTheme(darkTheme = false) {
            val colors = ClaudeTheme.colors
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.surface)
                    .padding(16.dp)
            ) {
                androidx.compose.material3.Text(
                    text = AnnotatedString(sample),
                    fontFamily = AnthropicSerif,
                    fontSize = 17.sp,
                    lineHeight = 27.sp,
                    color = colors.onSurface,
                )
            }
        }
    }
}
