package com.nairocy.claude

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.nairocy.claude.data.Attachment
import com.nairocy.claude.ui.components.Composer
import com.nairocy.claude.ui.theme.ClaudeTheme
import org.junit.Rule
import org.junit.Test

/** 输入框内附件条：照官方样式（文件卡 + 类型胶囊 + 黑色圆形删除按钮）。 */
class ComposerAttachScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        theme = "android:Theme.Material.Light.NoActionBar",
        showSystemUi = false,
    )

    private val files = listOf(
        Attachment(name = "crox-wordmark-black.svg", mime = "image/svg+xml", file = "a.svg"),
        Attachment(name = "ui.xml", mime = "text/xml", file = "b.xml"),
    )

    @Test
    fun composerExpandedLight() {
        paparazzi.snapshot(name = "composer_expanded_light") {
            ClaudeTheme(darkTheme = false) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ClaudeTheme.colors.surface)
                        .padding(18.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    // 键盘弹起态：输入在上、底部一行「+ / 模型 / 发送」
                    Composer(
                        value = "帮我看看这个报错",
                        onValueChange = {},
                        onSend = {},
                        keyboardVisible = true,
                        modelLabel = "Sonnet 5.5 高",
                    )
                }
            }
        }
    }

    @Test
    fun composerAttachmentsLight() {
        paparazzi.snapshot(name = "composer_attach_light") {
            ClaudeTheme(darkTheme = false) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ClaudeTheme.colors.surface)
                        .padding(18.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Composer(
                        value = "",
                        onValueChange = {},
                        onSend = {},
                        attachments = files,
                    )
                }
            }
        }
    }

    @Test
    fun composerAttachmentsDark() {
        paparazzi.snapshot(name = "composer_attach_dark") {
            ClaudeTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ClaudeTheme.colors.surface)
                        .padding(18.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Composer(
                        value = "",
                        onValueChange = {},
                        onSend = {},
                        attachments = files,
                    )
                }
            }
        }
    }
}
