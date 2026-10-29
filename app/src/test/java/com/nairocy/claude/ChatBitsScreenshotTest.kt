package com.nairocy.claude

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.nairocy.claude.ui.components.MarkdownText
import com.nairocy.claude.ui.components.ThinkingRow
import com.nairocy.claude.ui.theme.ClaudeTheme
import org.junit.Rule
import org.junit.Test

/** 思考文案行 + 引用气泡（Claude 橙）的静态截图。 */
class ChatBitsScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        theme = "android:Theme.Material.Light.NoActionBar",
        showSystemUi = false,
    )

    @Test
    fun thinkingRowLight() {
        paparazzi.snapshot(name = "thinking_row_light") {
            ClaudeTheme(darkTheme = false) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ClaudeTheme.colors.surface)
                        .padding(16.dp)
                ) {
                    ThinkingRow(overrideText = "Claude正在思索...")
                }
            }
        }
    }

    @Test
    fun thinkingRowDark() {
        paparazzi.snapshot(name = "thinking_row_dark") {
            ClaudeTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ClaudeTheme.colors.surface)
                        .padding(16.dp)
                ) {
                    ThinkingRow(overrideText = "抓取页面...")
                }
            }
        }
    }

    /** 赞/踩选中态：砖红填充 + 轻微放大（用户 2026-10-29 要求「装饰向」）。 */
    @Test
    fun messageActionsVoted() {
        paparazzi.snapshot(name = "message_actions_voted_light") {
            ClaudeTheme(darkTheme = false) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ClaudeTheme.colors.surface)
                        .padding(16.dp)
                ) {
                    com.nairocy.claude.ui.components.MessageActions(
                        onCopy = {},
                        onRegenerate = {},
                        vote = 1,
                    )
                }
            }
        }
    }

    @Test
    fun citationsOrange() {
        paparazzi.snapshot(name = "citations_light") {
            ClaudeTheme(darkTheme = false) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ClaudeTheme.colors.surface)
                        .padding(16.dp)
                ) {
                    MarkdownText(
                        text = "DeepSeek 官方文档写明最大输出 384K [1]，抓取接口返回 Markdown 正文 [2]，" +
                            "两条来源都来自同一份指南 [3]。"
                    )
                }
            }
        }
    }
}
