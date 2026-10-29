package com.nairocy.claude

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.nairocy.claude.ui.settings.ApiSettingsScreen
import com.nairocy.claude.ui.theme.ClaudeTheme
import org.junit.Rule
import org.junit.Test

/** API 设置页（本地版新建）：浅色 / 深色两张门面图。 */
class ApiSettingsScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        theme = "android:Theme.Material.Light.NoActionBar",
        showSystemUi = false,
    )

    @Test
    fun apiSettingsLight() {
        paparazzi.snapshot(name = "api_settings_light") {
            ClaudeTheme(darkTheme = false) {
                ApiSettingsScreen(onBack = {})
            }
        }
    }

    @Test
    fun apiSettingsDark() {
        paparazzi.snapshot(name = "api_settings_dark") {
            ClaudeTheme(darkTheme = true) {
                ApiSettingsScreen(onBack = {})
            }
        }
    }
}
