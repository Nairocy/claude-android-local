package com.nairocy.claude.ui.conversations

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nairocy.claude.R
import com.nairocy.claude.data.Conversation
import com.nairocy.claude.ui.theme.AnthropicSans
import com.nairocy.claude.ui.theme.ClaudeTheme
import java.util.concurrent.TimeUnit

/**
 * 「对话」列表页（对应原版：抽屉 → 对话）。
 * 顶栏汉堡 + 标题，下面是搜索框，列表项为「气泡图标 + 标题 + 相对时间」。
 */
@Composable
fun ConversationsScreen(
    conversations: List<Conversation>,
    onMenuClick: () -> Unit,
    onOpen: (Conversation) -> Unit,
    onNewChat: () -> Unit,
    onDelete: (List<String>) -> Unit = {},
) {
    val colors = ClaudeTheme.colors
    var query by remember { mutableStateOf("") }
    // 多选：点右上角清单图标进入，勾选后垃圾桶批量删
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val brick = androidx.compose.ui.graphics.Color(0xFFA6402C)

    val filtered = remember(conversations, query) {
        val q = query.trim()
        if (q.isEmpty()) conversations
        else conversations.filter { conv ->
            conv.title.contains(q, ignoreCase = true) ||
                conv.messages.any { it.content.contains(q, ignoreCase = true) }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.surface)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // 顶栏：左汉堡 / 中间标题
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            ) {
                IconTap(
                    onClick = {
                        if (selecting) {
                            selecting = false
                            selected = emptySet()
                        } else {
                            onMenuClick()
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Icon(
                        painter = painterResource(if (selecting) R.drawable.ic_close else R.drawable.ic_menu),
                        contentDescription = if (selecting) "取消多选" else "菜单",
                        tint = colors.onSurface,
                        modifier = Modifier.size(if (selecting) 20.dp else 24.dp),
                    )
                }
                Text(
                    text = if (selecting) "已选择 ${selected.size} 项" else "对话",
                    fontFamily = AnthropicSans,
                    fontSize = 17.sp,
                    color = colors.onSurface,
                    modifier = Modifier.align(Alignment.Center),
                )
                IconTap(
                    onClick = {
                        if (selecting) {
                            if (selected.isNotEmpty()) confirmDelete = true
                        } else {
                            selecting = true
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(
                        painter = painterResource(if (selecting) R.drawable.ic_trash else R.drawable.cds_icon_tasks),
                        contentDescription = if (selecting) "删除所选" else "多选",
                        tint = if (selecting) {
                            if (selected.isEmpty()) colors.tertiaryText.copy(alpha = 0.4f) else brick
                        } else colors.onSurface,
                        modifier = Modifier.size(if (selecting) 22.dp else 20.dp),
                    )
                }
            }

            // 搜索框
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceVariant)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.cds_icon_search),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    colorFilter = ColorFilter.tint(colors.tertiaryText),
                )
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = "搜索",
                            fontFamily = AnthropicSans,
                            fontSize = 15.sp,
                            color = colors.tertiaryText,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = AnthropicSans,
                            fontSize = 15.sp,
                            color = colors.onSurface,
                        ),
                        cursorBrush = SolidColor(colors.brand),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (query.isBlank()) "还没有对话" else "没有找到匹配的对话",
                        fontFamily = AnthropicSans,
                        fontSize = 14.sp,
                        color = colors.tertiaryText,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    items(filtered, key = { it.id }) { conv ->
                        val checked = conv.id in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .clickable {
                                    if (selecting) {
                                        selected = if (checked) selected - conv.id else selected + conv.id
                                    } else {
                                        onOpen(conv)
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            if (selecting) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 2.dp, end = 12.dp)
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (checked) brick else Color.Transparent)
                                        .border(
                                            width = 1.5.dp,
                                            color = if (checked) brick else colors.line,
                                            shape = RoundedCornerShape(6.dp),
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (checked) {
                                        Image(
                                            painter = painterResource(R.drawable.ic_check),
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp),
                                            colorFilter = ColorFilter.tint(Color.White),
                                        )
                                    }
                                }
                            }
                            Image(
                                painter = painterResource(R.drawable.cds_icon_chat),
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(20.dp),
                                colorFilter = ColorFilter.tint(colors.onSurface),
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = conv.title,
                                    fontFamily = AnthropicSans,
                                    fontSize = 17.sp,
                                    lineHeight = 23.sp,
                                    color = colors.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = relativeTime(conv.updatedAt),
                                    fontFamily = AnthropicSans,
                                    fontSize = 14.sp,
                                    color = colors.tertiaryText,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 右下角「新建对话」胶囊
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.onSurface)
                .clickable(onClick = onNewChat)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("＋", fontFamily = AnthropicSans, fontSize = 14.sp, color = colors.surface)
            Spacer(Modifier.width(6.dp))
            Text(
                "新建对话",
                fontFamily = AnthropicSans,
                fontSize = 14.sp,
                color = colors.surface,
            )
        }
    }

    BackHandler(enabled = selecting) {
        selecting = false
        selected = emptySet()
    }

    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = colors.card,
            titleContentColor = colors.onSurface,
            title = {
                Text("删除对话", fontFamily = AnthropicSans, fontSize = 17.sp)
            },
            text = {
                Text(
                    "将删除 ${selected.size} 个对话，删除后无法恢复。",
                    fontFamily = AnthropicSans,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = colors.secondaryText,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    onDelete(selected.toList())
                    confirmDelete = false
                    selecting = false
                    selected = emptySet()
                }) {
                    Text("删除", fontFamily = AnthropicSans, fontSize = 15.sp, color = brick)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) {
                    Text("取消", fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.tertiaryText)
                }
            },
        )
    }
}

@Composable
private fun IconTap(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** 原版风格：15分钟前 / 2小时前 / 3天前 / 10月21日。 */
private fun relativeTime(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    if (diff < TimeUnit.MINUTES.toMillis(1)) return "刚刚"
    if (diff < TimeUnit.HOURS.toMillis(1)) return "${diff / TimeUnit.MINUTES.toMillis(1)}分钟前"
    if (diff < TimeUnit.DAYS.toMillis(1)) return "${diff / TimeUnit.HOURS.toMillis(1)}小时前"
    if (diff < TimeUnit.DAYS.toMillis(7)) return "${diff / TimeUnit.DAYS.toMillis(1)}天前"
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = ts }
    return "${cal.get(java.util.Calendar.MONTH) + 1}月${cal.get(java.util.Calendar.DAY_OF_MONTH)}日"
}
