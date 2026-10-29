package com.nairocy.claude.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nairocy.claude.ui.theme.AnthropicSans
import com.nairocy.claude.ui.theme.ClaudeTheme

private val DangerRed = Color(0xFFD03B3B)
private val CancelBorder = Color(0xFFDDDDDD)

/**
 * 退出登录确认框，按官方截图实测：
 *
 * 卡片左右 16dp（388dp 宽）、圆角 12dp、纯白底、内边距 24dp；
 * 标题 19sp/600、说明 15sp muted；按钮 40dp 高、胶囊形、等宽、间距 8dp；
 * 取消 = 1dp #DDDDDD 描边，退出登录 = #D03B3B 填充白字。
 */
@Composable
fun LogoutDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = ClaudeTheme.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (colors.isDark) colors.card else Color.White)
                .padding(24.dp)
        ) {
            Text(
                text = "退出登录",
                fontFamily = AnthropicSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 19.sp,
                lineHeight = 24.sp,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "您确定要退出登录吗？",
                fontFamily = AnthropicSans,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                color = colors.mutedText,
            )
            Spacer(Modifier.height(17.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DialogButton(
                    label = "取消",
                    danger = false,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                DialogButton(
                    label = "退出登录",
                    danger = true,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DialogButton(
    label: String,
    danger: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ClaudeTheme.colors
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(if (danger) DangerRed else Color.Transparent)
            .then(
                if (danger) {
                    Modifier
                } else {
                    Modifier.border(
                        1.dp,
                        if (colors.isDark) colors.line else CancelBorder,
                        CircleShape,
                    )
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = AnthropicSans,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            color = if (danger) Color.White else colors.onSurface,
        )
    }
}
