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
import com.nairocy.claude.ui.settings.ProfileScreen
import com.nairocy.claude.ui.theme.ClaudeTheme
import org.junit.Rule
import org.junit.Test

/**
 * 账号相关页面出图：与官方截图同为 1368×3192px、520dpi（3.25px/dp），
 * 顶部补 48dp 状态栏占位（官方截图含状态栏，量 dp 时就是按这个基准）。
 */
class AccountScreenshotTest {

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

    @Composable
    private fun Frame(dark: Boolean = false, content: @Composable () -> Unit) {
        ClaudeTheme(darkTheme = dark) {
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
    fun profileLight() {
        paparazzi.snapshot(name = "profile_light") {
            Frame {
                ProfileScreen(
                    email = "demo@example.com",
                    initial = "N",
                    avatarFile = null,
                    fullName = "你",
                    preferredName = "你",
                    preferences = "在给出详细回答前先提出澄清性问题",
                    hasCustomAvatar = false,
                    onBack = {},
                    onPickAvatar = {},
                    onResetAvatar = {},
                    onSaveProfile = { _, _ -> },
                    onSavePreferences = {},
                    onDeleteAccount = {},
                )
            }
        }
    }

    @Test
    fun profileDark() {
        paparazzi.snapshot(name = "profile_dark") {
            Frame(dark = true) {
                ProfileScreen(
                    email = "demo@example.com",
                    initial = "N",
                    avatarFile = null,
                    fullName = "你",
                    preferredName = "你",
                    preferences = "在给出详细回答前先提出澄清性问题",
                    hasCustomAvatar = true,
                    onBack = {},
                    onPickAvatar = {},
                    onResetAvatar = {},
                    onSaveProfile = { _, _ -> },
                    onSavePreferences = {},
                    onDeleteAccount = {},
                )
            }
        }
    }
}
