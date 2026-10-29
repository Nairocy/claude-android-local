package com.nairocy.claude

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.resources.Density
import com.nairocy.claude.data.MemoryTopic
import com.nairocy.claude.ui.focus.FocusScreen
import com.nairocy.claude.ui.memory.MemoryTopicScreen
import com.nairocy.claude.ui.memory.MemoryTopicsScreen
import com.nairocy.claude.ui.notifications.NotificationsScreen
import com.nairocy.claude.ui.permissions.PermissionsScreen
import com.nairocy.claude.ui.privacy.PrivacyScreen
import com.nairocy.claude.ui.settings.SettingsScreen
import com.nairocy.claude.ui.theme.ClaudeTheme
import org.junit.Rule
import org.junit.Test

/**
 * 记忆页对照图：与官方截图同为 1368×3192px、520dpi（3.25px/dp、420.9×982.2dp）。
 * 顶部补 48dp 状态栏占位，与官方截图的 y 坐标对齐。
 */
class RefMemoryScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(
            screenWidth = 1368,
            screenHeight = 3192,
            xdpi = 520,
            ydpi = 520,
            density = Density.create(520),
        ),
        theme = "android:Theme.Material.Light.NoActionBar",
        showSystemUi = false,
    )

    private val now = System.currentTimeMillis()

    private val demoTopics = listOf(
        MemoryTopic(
            id = "demo",
            title = "Claude Usage",
            content = "How the user uses Claude (plan, app usage); currently notes they are on " +
                "the free plan and use the mobile app.\n\n" +
                "- uses Claude’s free plan and asked how long the free quota lasts in the app",
            updatedAt = now - 58 * 60_000,
        ),
    )

    @Composable
    private fun Frame(
        dark: Boolean = false,
        systemFont: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        ClaudeTheme(darkTheme = dark, systemFont = systemFont) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ClaudeTheme.colors.surface)
            ) {
                Spacer(Modifier.height(48.dp))
                content()
            }
        }
    }

    @Test
    fun memoryTopicsLight() {
        paparazzi.snapshot(name = "memory_topics_light") {
            Frame {
                MemoryTopicsScreen(onBack = {}, onOpenTopic = {}, demoTopicList = demoTopics)
            }
        }
    }

    @Test
    fun memoryTopicsDark() {
        paparazzi.snapshot(name = "memory_topics_dark") {
            Frame(dark = true) {
                MemoryTopicsScreen(onBack = {}, onOpenTopic = {}, demoTopicList = demoTopics)
            }
        }
    }

    @Test
    fun memoryTopicLight() {
        paparazzi.snapshot(name = "memory_topic_light") {
            Frame {
                MemoryTopicScreen(
                    topicId = "demo",
                    onBack = {},
                    demoTopic = demoTopics.first(),
                )
            }
        }
    }

    @Test
    fun permissionsLight() {
        paparazzi.snapshot(name = "permissions_light") {
            Frame {
                PermissionsScreen(onBack = {})
            }
        }
    }

    @Test
    fun permissionsDark() {
        paparazzi.snapshot(name = "permissions_dark") {
            Frame(dark = true) {
                PermissionsScreen(onBack = {})
            }
        }
    }

    @Test
    fun colorModeDialogLight() {
        paparazzi.snapshot(name = "color_mode_dialog_light") {
            Frame {
                SettingsScreen(onMenuClick = {}, initiallyColorModeOpen = true)
            }
        }
    }

    @Test
    fun fontModeDialogLight() {
        paparazzi.snapshot(name = "font_mode_dialog_light") {
            Frame {
                SettingsScreen(onMenuClick = {}, initiallyFontModeOpen = true)
            }
        }
    }

    @Test
    fun settingsSystemFontLight() {
        paparazzi.snapshot(name = "settings_system_font_light") {
            Frame(systemFont = true) {
                SettingsScreen(onMenuClick = {})
            }
        }
    }

    @Test
    fun settingsDefaultFontLight() {
        paparazzi.snapshot(name = "settings_default_font_light") {
            Frame {
                SettingsScreen(onMenuClick = {})
            }
        }
    }

    @Test
    fun notificationsLight() {
        paparazzi.snapshot(name = "notifications_light") {
            Frame {
                NotificationsScreen(onBack = {})
            }
        }
    }

    @Test
    fun notificationsDark() {
        paparazzi.snapshot(name = "notifications_dark") {
            Frame(dark = true) {
                NotificationsScreen(onBack = {})
            }
        }
    }

    @Test
    fun focusLight() {
        paparazzi.snapshot(name = "focus_light") {
            Frame {
                FocusScreen(onBack = {})
            }
        }
    }

    @Test
    fun focusHoursSheet() {
        paparazzi.snapshot(name = "focus_hours_sheet") {
            Frame {
                FocusScreen(onBack = {}, initiallyOpenSheet = "hours")
            }
        }
    }

    @Test
    fun focusMinutesSheet() {
        paparazzi.snapshot(name = "focus_minutes_sheet") {
            Frame {
                FocusScreen(onBack = {}, initiallyOpenSheet = "minutes")
            }
        }
    }

    @Test
    fun privacyLight() {
        paparazzi.snapshot(name = "privacy_light") {
            Frame {
                PrivacyScreen(onBack = {})
            }
        }
    }

    @Test
    fun privacyDark() {
        paparazzi.snapshot(name = "privacy_dark") {
            Frame(dark = true) {
                PrivacyScreen(onBack = {})
            }
        }
    }

    @Test
    fun logoutDialogLight() {
        paparazzi.snapshot(name = "logout_dialog_light") {
            Frame {
                SettingsScreen(onMenuClick = {}, initiallyLogoutOpen = true)
            }
        }
    }

    @Test
    fun focusDark() {
        paparazzi.snapshot(name = "focus_dark") {
            Frame(dark = true) {
                FocusScreen(onBack = {})
            }
        }
    }
}
