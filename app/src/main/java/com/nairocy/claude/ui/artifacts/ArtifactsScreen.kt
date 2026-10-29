package com.nairocy.claude.ui.artifacts

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nairocy.claude.R
import com.nairocy.claude.data.ArtifactItem
import com.nairocy.claude.ui.theme.AnthropicSans
import com.nairocy.claude.ui.theme.ClaudeTheme

/** Artifacts 列表页：汇总所有会话里可当「成品」的代码块。 */
@Composable
fun ArtifactsScreen(
    artifacts: List<ArtifactItem>,
    onMenuClick: () -> Unit,
    onOpen: (ArtifactItem) -> Unit,
) {
    val colors = ClaudeTheme.colors
    var query by remember { mutableStateOf("") }

    val filtered = remember(artifacts, query) {
        val q = query.trim()
        if (q.isEmpty()) artifacts
        else artifacts.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.convTitle.contains(q, ignoreCase = true) ||
                it.code.contains(q, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .systemBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(44.dp)
                    .clickable(onClick = onMenuClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_menu),
                    contentDescription = "菜单",
                    tint = colors.onSurface,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = "Artifacts",
                fontFamily = AnthropicSans,
                fontSize = 17.sp,
                color = colors.onSurface,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // 搜索
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
                    Text("搜索 Artifacts", fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.tertiaryText)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.onSurface),
                    cursorBrush = SolidColor(colors.brand),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (query.isBlank()) "还没有 Artifacts" else "没有找到匹配的 Artifacts",
                        fontFamily = AnthropicSans,
                        fontSize = 14.sp,
                        color = colors.tertiaryText,
                    )
                    if (query.isBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "在对话里生成 HTML / SVG 代码块后，会自动出现在这里",
                            fontFamily = AnthropicSans,
                            fontSize = 12.sp,
                            color = colors.tertiaryText.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.card)
                            .clickable { onOpen(item) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(colors.artifactSurface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(R.drawable.cds_icon_artifacts),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                colorFilter = ColorFilter.tint(colors.artifactIcon),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                fontFamily = AnthropicSans,
                                fontSize = 16.sp,
                                color = colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "仅您可见 · 编辑于 ${relativeTime(item.updatedAt)}",
                                fontFamily = AnthropicSans,
                                fontSize = 12.sp,
                                color = colors.tertiaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun relativeTime(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    val min = 60_000L
    val hour = 60 * min
    val day = 24 * hour
    return when {
        diff < min -> "刚刚"
        diff < hour -> "${diff / min} 分钟前"
        diff < day -> "${diff / hour} 小时前"
        diff < 7 * day -> "${diff / day} 天前"
        else -> {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = ts }
            "${cal.get(java.util.Calendar.MONTH) + 1} 月 ${cal.get(java.util.Calendar.DAY_OF_MONTH)} 日"
        }
    }
}
