package com.nairocy.claude

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.nairocy.claude.data.CodeDiffLine
import com.nairocy.claude.data.CodeMessage
import com.nairocy.claude.data.CodePart
import com.nairocy.claude.data.CodeToolRun
import com.nairocy.claude.data.Role
import com.nairocy.claude.ui.code.CodeEmptyState
import com.nairocy.claude.ui.code.CodeMessageList
import com.nairocy.claude.ui.theme.ClaudeTheme
import org.junit.Rule
import org.junit.Test

/**
 * Code 页消息流（工具卡 + diff）的静态截图。
 *
 * fixture 照真实工具输出写：diff 的 + 行是「新文件行号」、- 行是「旧文件行号」，
 * 文本是模型真会说的那种一两句话（不是营销文案）。
 */
class CodeScreenScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        theme = "android:Theme.Material.Light.NoActionBar",
        showSystemUi = false,
    )

    private val userMsg = CodeMessage(
        role = Role.USER,
        parts = listOf(CodePart("text", "把背景换成深色，加一个页脚")),
        ts = 1_000L,
    )

    private val toolRuns = listOf(
        CodeToolRun(
            id = "1",
            name = "grep",
            target = "background|footer",
            ok = true,
            output = "/index.html:8:  <link rel=\"stylesheet\" href=\"style.css\">\n" +
                "/style.css:5:  background: #FAF9F5;",

        ),
    )

    private val editRun = CodeToolRun(
        id = "2",
        name = "edit",
        target = "/style.css",
        ok = true,
        diff = listOf(
            CodeDiffLine("-", 1, "body {"),
            CodeDiffLine("-", 2, "  background: #FAF9F5;"),
            CodeDiffLine("+", 1, "body {"),
            CodeDiffLine("+", 2, "  background: #1A1A19;"),
            CodeDiffLine("+", 3, "  color: #F9F9F7;"),
            CodeDiffLine(" ", 4, "  min-height: 100vh;"),
        ),
    )

    private val writeRun = CodeToolRun(
        id = "3",
        name = "write",
        target = "/footer.html",
        ok = true,
        diff = listOf(
            CodeDiffLine("+", 1, "<footer class=\"foot\">"),
            CodeDiffLine("+", 2, "  <p>用 Claude 在手机里写的第一页</p>"),
            CodeDiffLine("+", 3, "</footer>"),
        ),
    )

    private val assistantMsg = CodeMessage(
        role = Role.ASSISTANT,
        parts = listOf(
            CodePart("text", "先看一眼现在的样式，然后换成深色并补一个页脚。"),
            CodePart("tool", tool = toolRuns[0]),
            CodePart("tool", tool = editRun),
            CodePart("tool", tool = writeRun),
            CodePart("text", "已经改好了：背景换成深色、文字提亮，页脚单独放在 `footer.html` 里。"),
        ),
        ts = 2_000L,
    )

    @Test
    fun codeToolsLight() {
        paparazzi.snapshot(name = "code_tools_light") {
            ClaudeTheme(darkTheme = false) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ClaudeTheme.colors.surface)
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    CodeMessageList(
                        messages = listOf(userMsg, assistantMsg),
                        currentId = "s1",
                        animateIn = false,
                    )
                }
            }
        }
    }

    @Test
    fun codeToolsDark() {
        paparazzi.snapshot(name = "code_tools_dark") {
            ClaudeTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ClaudeTheme.colors.surface)
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    CodeMessageList(
                        messages = listOf(userMsg, assistantMsg),
                        currentId = "s1",
                        animateIn = false,
                    )
                }
            }
        }
    }

    /** 工具执行中：最后一个助手消息只有工具卡 + 思考行（这一帧最像用户实际盯着看的画面）。 */
    @Test
    fun codeLockedLight() {
        paparazzi.snapshot(name = "code_locked_light") {
            ClaudeTheme(darkTheme = false) {
                com.nairocy.claude.ui.code.CodeLockedScreen(
                    onMenuClick = {},
                    onUpgrade = {},
                    onBackToChats = {},
                )
            }
        }
    }

    @Test
    fun codeLockedDark() {
        paparazzi.snapshot(name = "code_locked_dark") {
            ClaudeTheme(darkTheme = true) {
                com.nairocy.claude.ui.code.CodeLockedScreen(
                    onMenuClick = {},
                    onUpgrade = {},
                    onBackToChats = {},
                )
            }
        }
    }

    @Test
    fun codeEmptyLight() {
        paparazzi.snapshot(name = "code_empty_light") {
            ClaudeTheme(darkTheme = false) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ClaudeTheme.colors.surface)
                ) { CodeEmptyState() }
            }
        }
    }

    @Test
    fun codeEmptyDark() {
        paparazzi.snapshot(name = "code_empty_dark") {
            ClaudeTheme(darkTheme = true) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ClaudeTheme.colors.surface)
                ) { CodeEmptyState() }
            }
        }
    }

    @Test
    fun codeThinkingRowLight() {
        paparazzi.snapshot(name = "code_thinking_light") {
            ClaudeTheme(darkTheme = false) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ClaudeTheme.colors.surface)
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    com.nairocy.claude.ui.code.CodeThinkingRow()
                }
            }
        }
    }

    @Test
    fun codeThinkingRowDark() {
        paparazzi.snapshot(name = "code_thinking_dark") {
            ClaudeTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ClaudeTheme.colors.surface)
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    com.nairocy.claude.ui.code.CodeThinkingRow(overrideText = "写入 /style.css…")
                }
            }
        }
    }

    @Test
    fun codeRunningLight() {
        val running = CodeMessage(
            role = Role.ASSISTANT,
            parts = listOf(
                CodePart("text", "我来改这个页面。"),
                CodePart(
                    "tool",
                    tool = CodeToolRun(
                        id = "9",
                        name = "read",
                        target = "/index.html",
                        ok = true,
                        running = true,
                        output = "1\t<!doctype html>\n2\t<html lang=\"zh-CN\">",
                    ),
                ),
            ),
            ts = 3_000L,
        )
        paparazzi.snapshot(name = "code_running_light") {
            ClaudeTheme(darkTheme = false) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ClaudeTheme.colors.surface)
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    CodeMessageList(
                        messages = listOf(userMsg, running),
                        streaming = true,
                        toolHint = "读取 /index.html…",
                        currentId = "s2",
                        animateIn = false,
                    )
                }
            }
        }
    }
}
