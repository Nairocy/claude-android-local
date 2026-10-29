package com.nairocy.claude

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.nairocy.claude.ui.chat.ChatMenuCard
import com.nairocy.claude.ui.theme.ClaudeTheme
import org.junit.Rule
import org.junit.Test

/**
 * 对话页顶栏 ⋯ 菜单的宽度口径截图。
 *
 * 用户 2026-10-29 要求：宽度由**按钮行**决定、不再跟着会话标题走。
 * 两张图（长标题 / 短标题）宽度必须一模一样，长标题在自己那行里省略号截断。
 */
class ChatMenuScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        theme = "android:Theme.Material.Light.NoActionBar",
        showSystemUi = false,
    )

    @Test
    fun longTitleLight() {
        paparazzi.snapshot(name = "chat_menu_long_light") {
            ClaudeTheme(darkTheme = false) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ClaudeTheme.colors.surface)
                        .padding(16.dp)
                ) {
                    ChatMenuCard(
                        title = "这是一个特别长的会话标题用来验证菜单宽度不再被标题撑开",
                        pinned = false,
                        onShare = {},
                        onRename = {},
                        onTogglePin = {},
                        onAddToProject = {},
                        onDelete = {},
                    )
                }
            }
        }
    }

    @Test
    fun shortTitleDark() {
        paparazzi.snapshot(name = "chat_menu_short_dark") {
            ClaudeTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ClaudeTheme.colors.surface)
                        .padding(16.dp)
                ) {
                    ChatMenuCard(
                        title = "塔防",
                        pinned = true,
                        onShare = {},
                        onRename = {},
                        onTogglePin = {},
                        onAddToProject = {},
                        onDelete = {},
                    )
                }
            }
        }
    }
}
