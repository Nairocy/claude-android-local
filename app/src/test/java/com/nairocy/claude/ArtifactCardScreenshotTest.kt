package com.nairocy.claude

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.nairocy.claude.ui.components.ArtifactCard
import com.nairocy.claude.ui.components.MdBlock
import com.nairocy.claude.ui.theme.ClaudeTheme
import org.junit.Rule
import org.junit.Test

/** Artifact 卡片：折叠 / 展开两种状态，浅深各一套（运行按钮 + 复制图标）。 */
class ArtifactCardScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        theme = "android:Theme.Material.Light.NoActionBar",
        showSystemUi = false,
    )

    private val code = """<!doctype html>
<html>
<head><title>Constructivist Poster</title></head>
<body>
  <div class="grid"><h1>BUILD</h1><p>Form follows function.</p></div>
</body>
</html>"""

    @Test
    fun artifactCardFoldedLight() {
        paparazzi.snapshot(name = "artifact_card_folded_light") {
            ClaudeTheme(darkTheme = false) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ClaudeTheme.colors.surface)
                        .padding(16.dp)
                ) {
                    ArtifactCard(
                        block = MdBlock.Code("html", code),
                        clipboard = LocalClipboardManager.current,
                        onRun = {},
                    )
                }
            }
        }
    }

    @Test
    fun artifactCardExpandedLight() {
        paparazzi.snapshot(name = "artifact_card_expanded_light") {
            ClaudeTheme(darkTheme = false) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ClaudeTheme.colors.surface)
                        .padding(16.dp)
                ) {
                    ArtifactCard(
                        block = MdBlock.Code("html", code),
                        clipboard = LocalClipboardManager.current,
                        onRun = {},
                        initiallyExpanded = true,
                    )
                }
            }
        }
    }

    @Test
    fun artifactCardStreamingLight() {
        paparazzi.snapshot(name = "artifact_card_streaming_light") {
            ClaudeTheme(darkTheme = false) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ClaudeTheme.colors.surface)
                        .padding(16.dp)
                ) {
                    ArtifactCard(
                        block = MdBlock.Code("html", code),
                        clipboard = LocalClipboardManager.current,
                        onRun = {},
                        streaming = true,
                    )
                }
            }
        }
    }

    @Test
    fun artifactCardExpandedDark() {
        paparazzi.snapshot(name = "artifact_card_expanded_dark") {
            ClaudeTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ClaudeTheme.colors.surface)
                        .padding(16.dp)
                ) {
                    ArtifactCard(
                        block = MdBlock.Code("html", code),
                        clipboard = LocalClipboardManager.current,
                        onRun = {},
                        initiallyExpanded = true,
                    )
                }
            }
        }
    }
}
