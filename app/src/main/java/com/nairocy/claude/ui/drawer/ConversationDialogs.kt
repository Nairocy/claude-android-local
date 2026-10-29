package com.nairocy.claude.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nairocy.claude.data.Project
import com.nairocy.claude.ui.theme.AnthropicSans
import com.nairocy.claude.ui.theme.ClaudeTheme

/** 抽屉长按菜单 → 重命名。 */
@Composable
fun RenameConversationDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val colors = ClaudeTheme.colors
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        titleContentColor = colors.onSurface,
        title = { Text("重命名对话", fontFamily = AnthropicSans, fontSize = 17.sp) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
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
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }) {
                Text("保存", fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.brand)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.tertiaryText)
            }
        },
    )
}

/** 抽屉长按菜单 → 删除。 */
@Composable
fun DeleteConversationDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = ClaudeTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        titleContentColor = colors.onSurface,
        title = { Text("删除对话", fontFamily = AnthropicSans, fontSize = 17.sp) },
        text = {
            Text(
                "「$title」将被永久删除。",
                fontFamily = AnthropicSans,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = colors.secondaryText,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.tertiaryText)
            }
        },
    )
}

/** 抽屉长按菜单 → 添加到项目。 */
@Composable
fun AddConversationToProjectDialog(
    projects: List<Project>,
    onDismiss: () -> Unit,
    onPick: (Project) -> Unit,
) {
    val colors = ClaudeTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        titleContentColor = colors.onSurface,
        title = { Text("添加到项目", fontFamily = AnthropicSans, fontSize = 17.sp) },
        text = {
            if (projects.isEmpty()) {
                Text(
                    "还没有项目，先去「项目」里新建一个。",
                    fontFamily = AnthropicSans,
                    fontSize = 14.sp,
                    color = colors.secondaryText,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    projects.forEach { p ->
                        Text(
                            text = p.name,
                            fontFamily = AnthropicSans,
                            fontSize = 15.sp,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(p) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.brand)
            }
        },
    )
}
