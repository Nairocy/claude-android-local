package com.nairocy.claude.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nairocy.claude.BuildConfig
import com.nairocy.claude.R
import com.nairocy.claude.data.ModelConfig
import com.nairocy.claude.data.SettingsStore
import com.nairocy.claude.net.OpenAiClient
import com.nairocy.claude.ui.theme.AnthropicSans
import com.nairocy.claude.ui.theme.ClaudeTheme
import kotlinx.coroutines.launch

/**
 * API 设置：本地版唯一的模型入口。
 *
 * 版式对齐设置页（卡片 15dp 圆角、行高 56dp、行间 2dp 页面底色、17sp 标题），
 * 只放本地需要的东西：接口地址 / API Key / 模型 / 系统提示词 / 连通性测试。
 * 配置全部落在本机 `settings.json`，不经过任何中转服务。
 */
@Composable
fun ApiSettingsScreen(onBack: () -> Unit) {
    val colors = ClaudeTheme.colors
    val ctx = LocalContext.current
    val store = remember { SettingsStore(ctx) }
    val scope = rememberCoroutineScope()

    var cfg by remember { mutableStateOf(store.load()) }
    var editing by remember { mutableStateOf<String?>(null) }
    var testState by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    fun save(next: ModelConfig) {
        cfg = next
        store.save(next)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .systemBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(49.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_left),
                    contentDescription = "返回",
                    tint = colors.onSurface,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = "API 设置",
                fontFamily = AnthropicSans,
                fontSize = 17.sp,
                color = colors.onSurface,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ConfigCard {
                    ConfigRow(
                        label = "接口地址",
                        value = cfg.baseUrl.ifBlank { "未设置" },
                        expanded = editing == "baseUrl",
                        onClick = { editing = toggle(editing, "baseUrl") },
                    ) {
                        FieldEditor(
                            initial = cfg.baseUrl,
                            placeholder = "https://api.deepseek.com/v1",
                            onCommit = { save(cfg.copy(baseUrl = it.trim())); editing = null },
                        )
                    }
                    RowGap()
                    ConfigRow(
                        label = "API Key",
                        value = if (cfg.apiKey.isBlank()) "未设置" else maskKey(cfg.apiKey),
                        expanded = editing == "apiKey",
                        onClick = { editing = toggle(editing, "apiKey") },
                    ) {
                        FieldEditor(
                            initial = cfg.apiKey,
                            placeholder = "sk-...",
                            onCommit = { save(cfg.copy(apiKey = it.trim())); editing = null },
                        )
                    }
                    RowGap()
                    ConfigRow(
                        label = "模型",
                        value = cfg.model.ifBlank { "未设置" },
                        expanded = editing == "model",
                        onClick = { editing = toggle(editing, "model") },
                    ) {
                        FieldEditor(
                            initial = cfg.model,
                            placeholder = "deepseek-chat / gpt-4o-mini …",
                            onCommit = { save(cfg.copy(model = it.trim())); editing = null },
                        )
                    }
                    RowGap()
                    ConfigRow(
                        label = "系统提示词",
                        value = if (cfg.systemPrompt.isBlank()) "未设置" else "已设置",
                        expanded = editing == "sys",
                        onClick = { editing = toggle(editing, "sys") },
                    ) {
                        FieldEditor(
                            initial = cfg.systemPrompt,
                            placeholder = "可选。例如：回答保持简洁",
                            multiline = true,
                            onCommit = { save(cfg.copy(systemPrompt = it.trim())); editing = null },
                        )
                    }
                }
            }

            item {
                ConfigCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clickable(enabled = !testing) {
                                testing = true
                                testState = "测试中…"
                                scope.launch {
                                    // 延迟到点击时创建：OkHttp 在 JVM 渲染环境里初始化会崩
                                    val r = OpenAiClient().test(cfg)
                                    testState = r.getOrElse { "失败：" + (it.message ?: "未知错误") }
                                    testing = false
                                }
                            }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "连通性测试",
                            fontFamily = AnthropicSans,
                            fontSize = 15.sp,
                            color = colors.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (testing) "测试中…" else "点一下试试",
                            fontFamily = AnthropicSans,
                            fontSize = 13.sp,
                            color = colors.tertiaryText,
                        )
                    }
                    if (testState != null) {
                        Text(
                            text = testState.orEmpty(),
                            fontFamily = AnthropicSans,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = if (testState.orEmpty().startsWith("失败")) colors.brand else colors.tertiaryText,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                        )
                    }
                }
            }

            item {
                Text(
                    text = "本地版直连你填写的接口：兼容 OpenAI 的 /v1/chat/completions（SSE 流式），" +
                        "不经过任何中转，也不需要账号。配置只存在这台设备上。",
                    fontFamily = AnthropicSans,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = colors.tertiaryText,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            item {
                Text(
                    text = "版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
                    fontFamily = AnthropicSans,
                    fontSize = 12.sp,
                    color = colors.tertiaryText,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp),
                )
            }
        }
    }
}

private fun toggle(current: String?, target: String): String? = if (current == target) null else target

private fun maskKey(key: String): String {
    val trimmed = key.trim()
    return when {
        trimmed.length <= 8 -> "•".repeat(trimmed.length)
        else -> trimmed.take(4) + "…" + trimmed.takeLast(4)
    }
}

/** 卡片：15dp 圆角，行间用 2dp 页面底色隔开（与设置页同一套口径）。 */
@Composable
private fun ConfigCard(content: @Composable () -> Unit) {
    val colors = ClaudeTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(colors.card),
    ) { content() }
}

/** 行间分隔：2dp 页面底色（不是细线）。 */
@Composable
private fun RowGap() {
    val colors = ClaudeTheme.colors
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(colors.surfaceVariant)
    )
}

@Composable
private fun ConfigRow(
    label: String,
    value: String,
    expanded: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = ClaudeTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontFamily = AnthropicSans,
                fontSize = 15.sp,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(12.dp))
            Text(
                text = value,
                fontFamily = AnthropicSans,
                fontSize = 13.sp,
                color = colors.tertiaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.2f, fill = false),
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = colors.tertiaryText,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(16.dp),
            )
        }
        if (expanded) {
            Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                content()
            }
        }
    }
}

/** 行内编辑器：浅灰底输入框 + 右侧「保存」。 */
@Composable
private fun FieldEditor(
    initial: String,
    placeholder: String,
    multiline: Boolean = false,
    onCommit: (String) -> Unit,
) {
    val colors = ClaudeTheme.colors
    var text by remember { mutableStateOf(initial) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .then(if (multiline) Modifier.heightIn(min = 96.dp) else Modifier)
        ) {
            if (text.isEmpty()) {
                Text(
                    text = placeholder,
                    fontFamily = AnthropicSans,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    color = colors.tertiaryText,
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(
                    fontFamily = AnthropicSans,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    color = colors.onSurface,
                ),
                cursorBrush = SolidColor(colors.brand),
                singleLine = !multiline,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = if (multiline) ImeAction.Default else ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.size(8.dp))
        Text(
            text = "保存",
            fontFamily = AnthropicSans,
            fontSize = 14.sp,
            color = colors.brand,
            modifier = Modifier.clickable { onCommit(text) },
        )
    }
}
