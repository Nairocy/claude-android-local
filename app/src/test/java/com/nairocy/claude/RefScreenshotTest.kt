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
import com.nairocy.claude.ui.features.FeaturesScreen
import com.nairocy.claude.ui.theme.ClaudeTheme
import org.junit.Rule
import org.junit.Test

/**
 * 与官方真机截图同尺寸的对照图：1368×3566px、520dpi（=3.25px/dp、420.9dp 宽）。
 *
 * 官方截图含 48dp 状态栏，而 Paparazzi 的 systemBarsPadding 为 0，
 * 所以测试里补一个 48dp 的占位，y 坐标就能逐像素对齐参考图。
 */
class RefScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(
            screenWidth = 1368,
            screenHeight = 3566,
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
    fun featuresLight() {
        paparazzi.snapshot(name = "features_light") {
            Frame {
                FeaturesScreen(onBack = {}, onOpenMemoryFiles = {})
            }
        }
    }

    @Test
    fun featuresDark() {
        paparazzi.snapshot(name = "features_dark") {
            Frame(dark = true) {
                FeaturesScreen(onBack = {}, onOpenMemoryFiles = {})
            }
        }
    }
}
