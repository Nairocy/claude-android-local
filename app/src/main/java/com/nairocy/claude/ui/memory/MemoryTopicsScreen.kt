package com.nairocy.claude.ui.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nairocy.claude.R
import com.nairocy.claude.data.MemoryStore
import com.nairocy.claude.data.MemoryTopic
import com.nairocy.claude.ui.components.BackTopBar
import com.nairocy.claude.ui.components.CardSeparator
import com.nairocy.claude.ui.components.GroupCard
import com.nairocy.claude.ui.theme.AnthropicSans
import com.nairocy.claude.ui.theme.AnthropicSerif
import com.nairocy.claude.ui.theme.ClaudeTheme
import java.util.concurrent.TimeUnit

/** 记忆文件（Topics 列表）+ 底部新建输入框。 */
@Composable
fun MemoryTopicsScreen(
    onBack: () -> Unit,
    onOpenTopic: (String) -> Unit,
    /** 仅截图测试用：绕过本地存储，直接展示这些主题。 */
    demoTopicList: List<MemoryTopic>? = null,
) {
    val colors = ClaudeTheme.colors
    val ctx = LocalContext.current
    val store = remember { MemoryStore(ctx) }
    var topics by remember { mutableStateOf(demoTopicList ?: store.loadAll()) }
    var draft by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .systemBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BackTopBar(title = "记忆文件", onBack = onBack)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp, end = 12.dp, top = 20.dp, bottom = 150.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = "Topics",
                        fontFamily = AnthropicSans,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        color = colors.secondaryText,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                if (topics.isNotEmpty()) {
                    item {
                        GroupCard {
                            topics.forEachIndexed { index, topic ->
                                if (index > 0) CardSeparator()
                                TopicRow(topic) { onOpenTopic(topic.id) }
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            MemoryComposer(
                hint = "告诉 Claude 要记住什么",
                text = draft,
                onTextChange = { draft = it },
                busy = false,
                modifier = Modifier.imePadding(),
                onSend = {
                    val body = draft.trim()
                    if (body.isNotEmpty()) {
                        store.create(titleOf(body), body)
                        topics = store.loadAll()
                        draft = ""
                    }
                },
            )
        }
    }
}

@Composable
private fun TopicRow(topic: MemoryTopic, onClick: () -> Unit) {
    val colors = ClaudeTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = topic.title,
                fontFamily = AnthropicSerif,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                color = colors.onSurface,
            )
            Text(
                text = updatedLabel(topic.updatedAt),
                fontFamily = AnthropicSans,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = colors.tertiaryText,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}

/**
 * 记忆页共用输入框：白色大圆角卡片 + 占位文案 + 右下角发送圆钮。
 * 官方实测（1368px 宽 / 3.25px per dp）：卡片 99dp 高、圆角 22dp、左右 16dp、
 * 占位 17sp、按钮 35dp、卡片内边距 13/8/18/8。官方有麦克风，本项目不做语音，只留发送。
 */
@Composable
fun MemoryComposer(
    hint: String,
    text: String,
    onTextChange: (String) -> Unit,
    busy: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ClaudeTheme.colors
    val enabled = text.isNotBlank() && !busy
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 9.dp)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.10f),
            )
            .clip(RoundedCornerShape(22.dp))
            .background(colors.card)
            .padding(start = 13.dp, end = 8.dp, top = 18.dp, bottom = 8.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 22.dp)
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = hint,
                        fontFamily = AnthropicSans,
                        fontSize = 17.sp,
                        lineHeight = 23.sp,
                        color = colors.tertiaryText,
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = TextStyle(
                        fontFamily = AnthropicSans,
                        fontSize = 17.sp,
                        lineHeight = 23.sp,
                        color = colors.onSurface,
                    ),
                    cursorBrush = SolidColor(colors.brand),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(15.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(35.dp)
                        .clip(CircleShape)
                        .background(if (enabled) colors.brand else Color(0xFFE7BFB1))
                        .clickable(enabled = enabled) {
                            focusManager.clearFocus(force = true)
                            keyboard?.hide()
                            onSend()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_send_arrow),
                        contentDescription = "发送",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
    }
}

/** 列表标题：取正文第一行前 24 个字。 */
internal fun titleOf(body: String): String {
    val first = body.lineSequence().firstOrNull()?.trim().orEmpty()
    val clean = first.ifBlank { body.trim() }
    return if (clean.length <= 24) clean else clean.take(24)
}

/** 官方文案：「58分钟前 更新」。 */
internal fun updatedLabel(ts: Long): String {
    val diff = (System.currentTimeMillis() - ts).coerceAtLeast(0)
    val rel = when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "刚刚"
        diff < TimeUnit.HOURS.toMillis(1) -> "${diff / TimeUnit.MINUTES.toMillis(1)}分钟前"
        diff < TimeUnit.DAYS.toMillis(1) -> "${diff / TimeUnit.HOURS.toMillis(1)}小时前"
        diff < TimeUnit.DAYS.toMillis(7) -> "${diff / TimeUnit.DAYS.toMillis(1)}天前"
        else -> {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = ts }
            "${cal.get(java.util.Calendar.MONTH) + 1}月${cal.get(java.util.Calendar.DAY_OF_MONTH)}日"
        }
    }
    return "$rel 更新"
}
