package com.nairocy.claude

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.nairocy.claude.ui.home.HomeScreen
import com.nairocy.claude.ui.home.Greeting
import com.nairocy.claude.ui.theme.ClaudeTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** 首页问候语按时段的四个变体（分档规则见 GreetingTest）。 */
class HomeGreetingScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        theme = "android:Theme.Material.Light.NoActionBar",
        showSystemUi = false,
    )

    private val day = LocalDate.of(2026, 10, 28)

    @Test
    fun morning() {
        paparazzi.snapshot(name = "home_greeting_morning") {
            ClaudeTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize().background(ClaudeTheme.colors.surface)) {
                    HomeScreen(onMenuClick = {}, greeting = Greeting.forHour(8, "你", day))
                }
            }
        }
    }

    @Test
    fun night() {
        paparazzi.snapshot(name = "home_greeting_night") {
            ClaudeTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize().background(ClaudeTheme.colors.surface)) {
                    HomeScreen(onMenuClick = {}, greeting = Greeting.forHour(19, "你", day))
                }
            }
        }
    }
}
