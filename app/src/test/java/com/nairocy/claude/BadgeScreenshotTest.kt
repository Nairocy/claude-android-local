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
import com.nairocy.claude.data.Plan
import com.nairocy.claude.ui.settings.SettingsScreen
import com.nairocy.claude.ui.theme.ClaudeTheme
import org.junit.Rule
import org.junit.Test

/**
 * 设置页账号行订阅徽标三态：Free / Pro / Max × 浅色 / 深色 = 6 张。
 *
 * 尺寸与 AccountScreenshotTest 对齐（1368×3192、520dpi、补 48dp 状态栏占位），
 * 徽标位置就能和官方截图逐像素对；页面其余部分由 ScreenshotTest 的 settings_* 覆盖。
 */
class BadgeScreenshotTest {

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
    private fun Frame(dark: Boolean, plan: Plan, showBadge: Boolean = true) {
        ClaudeTheme(darkTheme = dark) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ClaudeTheme.colors.surface)
            ) {
                Spacer(Modifier.height(48.dp))
                SettingsScreen(
                    onMenuClick = {},
                    email = "demo@example.com",
                    plan = plan,
                    showBadge = showBadge,
                )
            }
        }
    }

    @Test
    fun badgeFreeLight() {
        paparazzi.snapshot(name = "badge_free_light") { Frame(dark = false, plan = Plan.FREE) }
    }

    @Test
    fun badgeProLight() {
        paparazzi.snapshot(name = "badge_pro_light") { Frame(dark = false, plan = Plan.PRO) }
    }

    @Test
    fun badgeMaxLight() {
        paparazzi.snapshot(name = "badge_max_light") { Frame(dark = false, plan = Plan.MAX) }
    }

    @Test
    fun badgeFreeDark() {
        paparazzi.snapshot(name = "badge_free_dark") { Frame(dark = true, plan = Plan.FREE) }
    }

    @Test
    fun badgeProDark() {
        paparazzi.snapshot(name = "badge_pro_dark") { Frame(dark = true, plan = Plan.PRO) }
    }

    @Test
    fun badgeMaxDark() {
        paparazzi.snapshot(name = "badge_max_dark") { Frame(dark = true, plan = Plan.MAX) }
    }

    /**
     * 首次拉取权益未完成时必须**不画**徽标 —— 否则会先闪一下「Free」再变「Pro」。
     * 这条用 plan = MAX 但 showBadge = false：渲染结果里不该有任何胶囊。
     */
    @Test
    fun badgeHiddenUntilLoaded() {
        paparazzi.snapshot(name = "badge_hidden_light") {
            Frame(dark = false, plan = Plan.MAX, showBadge = false)
        }
    }
}
