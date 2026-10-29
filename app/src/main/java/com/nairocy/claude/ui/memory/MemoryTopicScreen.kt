package com.nairocy.claude.ui.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nairocy.claude.R
import com.nairocy.claude.data.AccountStore
import com.nairocy.claude.data.MemoryStore
import com.nairocy.claude.data.MemoryTopic
import com.nairocy.claude.net.OpenAiClient
import com.nairocy.claude.ui.components.BackTopBar
import com.nairocy.claude.ui.components.MarkdownText
import com.nairocy.claude.ui.theme.AnthropicSans
import com.nairocy.claude.ui.theme.AnthropicSerif
import com.nairocy.claude.ui.theme.ClaudeTheme
import kotlinx.coroutines.launch

/** 单条记忆详情：正文 + 删除 + 「告诉 Claude 要修改什么」。 */
@Composable
fun MemoryTopicScreen(
    topicId: String,
    onBack: () -> Unit,
    /** 仅截图测试用：绕过本地存储。 */
    demoTopic: MemoryTopic? = null,
) {
    val colors = ClaudeTheme.colors
    val ctx = LocalContext.current
    val store = remember { MemoryStore(ctx) }
    val cfg = remember { com.nairocy.claude.data.SettingsStore(ctx).load() }
    val scope = rememberCoroutineScope()

    var topic by remember {
        mutableStateOf(demoTopic ?: store.loadAll().firstOrNull { it.id == topicId })
    }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val current = topic ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .systemBarsPadding()
    ) {
        BackTopBar(
            title = current.title,
            onBack = onBack,
            titleFont = AnthropicSerif,
            titleSize = 17,
            trailing = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD03B3B))
                        .clickable { confirmDelete = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_trash),
                        contentDescription = "删除",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            MarkdownText(text = current.content)
        }

        if (error != null) {
            Text(
                text = "修改失败：" + error,
                fontFamily = AnthropicSans,
                fontSize = 12.sp,
                color = colors.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        MemoryComposer(
            hint = if (busy) "正在修改…" else "告诉 Claude 要修改什么",
            text = draft,
            onTextChange = { draft = it },
            busy = busy,
            modifier = Modifier.imePadding(),
            onSend = {
                val instruction = draft.trim()
                if (instruction.isNotEmpty() && !busy) {
                    busy = true
                    error = null
                    scope.launch {
                        val old = current.content
                        val result = OpenAiClient().complete(
                            cfg,
                            system = "你在维护用户的长期记忆文件。根据修改要求改写内容，" +
                                "只输出改写后的记忆正文，不要解释、不要代码块。",
                            user = "当前记忆：\n" + old + "\n\n修改要求：" + instruction,
                        )
                        busy = false
                        result.fold(
                            onSuccess = { newText ->
                                val updated = current.copy(content = newText.trim())
                                store.update(updated)
                                topic = updated
                                draft = ""
                            },
                            onFailure = { error = it.message ?: "未知错误" },
                        )
                    }
                }
            },
        )
    }

    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = colors.card,
            titleContentColor = colors.onSurface,
            title = { Text("删除这条记忆？", fontFamily = AnthropicSans, fontSize = 17.sp) },
            text = {
                Text(
                    "「" + current.title + "」会被永久删除。",
                    fontFamily = AnthropicSans,
                    fontSize = 14.sp,
                    color = colors.secondaryText,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    store.delete(current.id)
                    confirmDelete = false
                    onBack()
                }) {
                    Text("删除", fontFamily = AnthropicSans, fontSize = 15.sp, color = Color(0xFFD03B3B))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) {
                    Text("取消", fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.onSurface)
                }
            },
        )
    }
}
