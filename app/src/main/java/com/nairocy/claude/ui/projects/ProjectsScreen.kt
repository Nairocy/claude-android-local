package com.nairocy.claude.ui.projects

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nairocy.claude.R
import com.nairocy.claude.data.Conversation
import com.nairocy.claude.data.Project
import com.nairocy.claude.ui.theme.AnthropicSans
import com.nairocy.claude.ui.theme.ClaudeTheme

/** 项目列表页（本机分组）。 */
@Composable
fun ProjectsScreen(
    projects: List<Project>,
    onMenuClick: () -> Unit,
    onOpen: (Project) -> Unit,
    onCreate: (String) -> Unit,
) {
    val colors = ClaudeTheme.colors
    var createOpen by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().background(colors.surface)) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
                Box(
                    modifier = Modifier.align(Alignment.CenterStart).size(44.dp).clickable(onClick = onMenuClick),
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
                    text = "项目",
                    fontFamily = AnthropicSans,
                    fontSize = 17.sp,
                    color = colors.onSurface,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            if (projects.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 44.dp),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_project),
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            colorFilter = ColorFilter.tint(colors.tertiaryText),
                        )
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = "创建项目，围绕某个主题或一组对话做整理。",
                            fontFamily = AnthropicSans,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            color = colors.tertiaryText,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(projects, key = { it.id }) { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.card)
                                .clickable { onOpen(p) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_project),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                colorFilter = ColorFilter.tint(colors.tertiaryText),
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = p.name,
                                    fontFamily = AnthropicSans,
                                    fontSize = 16.sp,
                                    color = colors.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = "${p.conversationIds.size} 个对话",
                                    fontFamily = AnthropicSans,
                                    fontSize = 12.sp,
                                    color = colors.tertiaryText,
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.onSurface)
                .clickable { createOpen = true }
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("＋", fontFamily = AnthropicSans, fontSize = 14.sp, color = colors.surface)
            Spacer(Modifier.width(6.dp))
            Text("新建项目", fontFamily = AnthropicSans, fontSize = 14.sp, color = colors.surface)
        }
    }

    if (createOpen) {
        AlertDialog(
            onDismissRequest = { createOpen = false },
            containerColor = colors.card,
            titleContentColor = colors.onSurface,
            title = { Text("新建项目", fontFamily = AnthropicSans, fontSize = 17.sp) },
            text = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (name.isEmpty()) {
                        Text("项目名称", fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.tertiaryText)
                    }
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.onSurface),
                        cursorBrush = SolidColor(colors.brand),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        onCreate(name)
                        name = ""
                        createOpen = false
                    }
                }) {
                    Text("创建", fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.brand)
                }
            },
            dismissButton = {
                TextButton(onClick = { createOpen = false }) {
                    Text("取消", fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.tertiaryText)
                }
            },
        )
    }
}

/** 项目详情：项目内的对话 + 添加/移除。 */
@Composable
fun ProjectDetailScreen(
    project: Project,
    conversations: List<Conversation>,
    onBack: () -> Unit,
    onMenuClick: () -> Unit = {},
    onOpenConversation: (Conversation) -> Unit,
    onAdd: (List<String>) -> Unit,
    onRemove: (String) -> Unit,
) {
    val colors = ClaudeTheme.colors
    var addOpen by remember { mutableStateOf(false) }
    val inside = conversations.filter { it.id in project.conversationIds }
    val outside = conversations.filterNot { it.id in project.conversationIds }

    Column(Modifier.fillMaxSize().background(colors.surface).systemBarsPadding()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
            Box(
                modifier = Modifier.align(Alignment.CenterStart).size(44.dp).clickable(onClick = onMenuClick),
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
                text = project.name,
                fontFamily = AnthropicSans,
                fontSize = 17.sp,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 60.dp),
            )
            Text(
                text = "添加",
                fontFamily = AnthropicSans,
                fontSize = 14.sp,
                color = colors.brand,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clickable { addOpen = true }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }

        if (inside.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "这个项目还没有对话\n点右上角「添加」把对话放进来",
                    fontFamily = AnthropicSans,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = colors.tertiaryText,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(inside, key = { it.id }) { conv ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenConversation(conv) }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.cds_icon_chat),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            colorFilter = ColorFilter.tint(colors.onSurface),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = conv.title,
                            fontFamily = AnthropicSans,
                            fontSize = 16.sp,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "移除",
                            fontFamily = AnthropicSans,
                            fontSize = 12.sp,
                            color = colors.tertiaryText,
                            modifier = Modifier
                                .clickable { onRemove(conv.id) }
                                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                        )
                    }
                }
            }
        }
    }

    if (addOpen) {
        AlertDialog(
            onDismissRequest = { addOpen = false },
            containerColor = colors.card,
            titleContentColor = colors.onSurface,
            title = { Text("添加对话", fontFamily = AnthropicSans, fontSize = 17.sp) },
            text = {
                if (outside.isEmpty()) {
                    Text(
                        "没有可添加的对话了",
                        fontFamily = AnthropicSans,
                        fontSize = 14.sp,
                        color = colors.tertiaryText,
                    )
                } else {
                    LazyColumn(modifier = Modifier.widthIn(max = 320.dp)) {
                        items(outside, key = { it.id }) { conv ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        onAdd(listOf(conv.id))
                                        addOpen = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = conv.title,
                                    fontFamily = AnthropicSans,
                                    fontSize = 15.sp,
                                    color = colors.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { addOpen = false }) {
                    Text("关闭", fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.brand)
                }
            },
        )
    }
}
