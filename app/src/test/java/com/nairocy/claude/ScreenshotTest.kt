package com.nairocy.claude

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nairocy.claude.data.Conversation
import com.nairocy.claude.ui.conversations.ConversationsScreen
import com.nairocy.claude.ui.drawer.AppDrawer
import com.nairocy.claude.ui.home.HomeScreen
import com.nairocy.claude.ui.settings.InfoMenu
import com.nairocy.claude.ui.settings.SettingsScreen
import com.nairocy.claude.ui.theme.ClaudeTheme
import org.junit.Rule
import org.junit.Test

/**
 * 纯 JVM 截图：不需要模拟器/真机，直接渲染 Compose 到 PNG。
 * 产物：app/src/test/snapshots/images/
 */
class ScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        theme = "android:Theme.Material.Light.NoActionBar",
        showSystemUi = false,
    )

    @Test
    fun homeLight() {
        paparazzi.snapshot(name = "home_light") {
            ClaudeTheme(darkTheme = false) {
                HomeScreen(onMenuClick = {}, onGhostClick = {})
            }
        }
    }

    @Test
    fun homeDark() {
        paparazzi.snapshot(name = "home_dark") {
            ClaudeTheme(darkTheme = true) {
                HomeScreen(onMenuClick = {}, onGhostClick = {})
            }
        }
    }

    @Test
    fun settingsLight() {
        paparazzi.snapshot(name = "settings_light") {
            ClaudeTheme(darkTheme = false) {
                SettingsScreen(onMenuClick = {})
            }
        }
    }

    @Test
    fun settingsDark() {
        paparazzi.snapshot(name = "settings_dark") {
            ClaudeTheme(darkTheme = true) {
                SettingsScreen(onMenuClick = {})
            }
        }
    }

    @Test
    fun settingsInfoMenuLight() {
        paparazzi.snapshot(name = "settings_info_light") {
            ClaudeTheme(darkTheme = false) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ClaudeTheme.colors.surface)
                ) {
                    InfoMenu(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 49.dp, end = 4.dp),
                        onEntry = {},
                    )
                }
            }
        }
    }

    @Test
    fun settingsMenuOpenLight() {
        paparazzi.snapshot(name = "settings_menu_open_light") {
            ClaudeTheme(darkTheme = false) {
                SettingsScreen(onMenuClick = {}, initiallyInfoOpen = true)
            }
        }
    }

    private val now = System.currentTimeMillis()
    private val demoConvs = listOf(
        Conversation(id = "1", title = "构成主义风格的自我介绍页面", updatedAt = now - 15 * 60_000),
        Conversation(id = "2", title = "账户封禁风险", updatedAt = now - 2 * 3_600_000),
        Conversation(id = "3", title = "Claude 安卓客户端前端加密与开源 UI", updatedAt = now - 26 * 3_600_000),
    )

    @Test
    fun conversationsLight() {
        paparazzi.snapshot(name = "conversations_light") {
            ClaudeTheme(darkTheme = false) {
                ConversationsScreen(
                    conversations = demoConvs,
                    onMenuClick = {}, onOpen = {}, onNewChat = {},
                )
            }
        }
    }

    @Test
    fun conversationsDark() {
        paparazzi.snapshot(name = "conversations_dark") {
            ClaudeTheme(darkTheme = true) {
                ConversationsScreen(
                    conversations = demoConvs,
                    onMenuClick = {}, onOpen = {}, onNewChat = {},
                )
            }
        }
    }

    @Test
    fun drawerLight() {
        paparazzi.snapshot(name = "drawer_light") {
            ClaudeTheme(darkTheme = false) {
                AppDrawer(
                    open = true,
                    conversations = demoConvs,
                    onClose = {}, onNewChat = {}, onOpenConversations = {}, onSelect = {},
                ) {
                    HomeScreen(onMenuClick = {}, onGhostClick = {})
                }
            }
        }
    }

    @Test
    fun thinkingIndicator() = paparazzi.snapshot(name = "thinking") {
        ClaudeTheme(darkTheme = false) {
            androidx.compose.material3.Surface(
                color = ClaudeTheme.colors.surface,
                modifier = Modifier.fillMaxSize(),
            ) {
                com.nairocy.claude.ui.components.ThinkingRow(
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
    }

    @Test
    fun messageActions() = paparazzi.snapshot(name = "message_actions") {
        ClaudeTheme(darkTheme = false) {
            androidx.compose.material3.Surface(
                color = ClaudeTheme.colors.surface,
                modifier = Modifier.fillMaxSize(),
            ) {
                com.nairocy.claude.ui.components.MessageActions(
                    onCopy = {},
                    onRegenerate = {},
                    onLike = {},
                    onDislike = {},
                    modifier = Modifier.padding(start = 20.dp, top = 20.dp),
                )
            }
        }
    }

    @Test
    fun markdownBlocks() {
        val sample = """
            ## 构成主义的一段回答

            这是**粗体强调**，这是 `行内代码`，还有普通文字。

            - 第一条要点
              - 嵌套的二级要点
            - 第二条要点

            - [x] 已完成的任务
            - [ ] 未完成的任务

            > 这是一段引用，左侧应该有竖线。

            ---

            ~~删除线~~ 和 [一个链接](https://example.com)。

            1. 有序第一
            2. 有序第二

            | 风格 | 主色 | 适合 |
            |---|---|---|
            | 方正主义 | #D97757 | 官网首页 |
            | 瑞士国际主义 | #E30613 | 机构介绍 |

            ```kotlin
            fun greet(name: String) = "你好，${'$'}name"
            ```

            ```html
            <!DOCTYPE html>
            <html><head><title>方正构成主义海报</title></head>
            <body style="background:#D97757"><h1>构成主义</h1></body></html>
            ```
        """.trimIndent()
        paparazzi.snapshot(name = "markdown") {
            ClaudeTheme(darkTheme = false) {
                androidx.compose.material3.Surface(
                    color = ClaudeTheme.colors.surface,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    com.nairocy.claude.ui.components.MarkdownText(
                        text = sample,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }
    }

    @Test
    fun splashLight() {
        paparazzi.snapshot(name = "splash_light") {
            ClaudeTheme(darkTheme = false) {
                com.nairocy.claude.ui.splash.ClaudeSplash(onFinish = {})
            }
        }
    }

    /** 深色启动页：底色 #151515、字标换浅色版、品牌行灰提亮。 */
    @Test
    fun splashDark() {
        paparazzi.snapshot(name = "splash_dark") {
            ClaudeTheme(darkTheme = true) {
                com.nairocy.claude.ui.splash.ClaudeSplash(dark = true, onFinish = {})
            }
        }
    }

    @Test
    fun splashIcons() {
        paparazzi.snapshot(name = "splash_icons") {
            ClaudeTheme(darkTheme = false) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.White)
                        .padding(20.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    listOf(
                        R.drawable.cds_icon_archive,
                        R.drawable.cds_icon_globe,
                        R.drawable.cds_icon_memory,
                        R.drawable.cds_icon_camera,
                        R.drawable.cds_icon_image,
                        R.drawable.cds_icon_file,
                    ).forEach { icon ->
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(icon),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                                androidx.compose.ui.graphics.Color(0xFF131313)
                            ),
                        )
                    }
                }
            }
        }
    }
}
