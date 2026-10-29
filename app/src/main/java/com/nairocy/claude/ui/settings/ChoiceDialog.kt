package com.nairocy.claude.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nairocy.claude.R
import com.nairocy.claude.ui.theme.AnthropicSans
import com.nairocy.claude.ui.theme.ClaudeTheme

/** 一个选项：id 落盘用，label 显示，icon 左侧 24dp 图标。 */
data class ChoiceOption(
    val id: String,
    val label: String,
    @DrawableRes val icon: Int,
)

/**
 * 设置项的单选弹窗（颜色模式 / 字体样式共用），按官方截图实测：
 *
 * 卡片宽 298dp、圆角 15dp、底色 = 页面 surface（#F9F9F7），离屏幕左右各 61dp；
 * 标题 16sp Medium、左/上 20/18dp；标题下 9dp；每行 56dp、左右 20dp；
 * 图标 24dp + 16dp + 文字 16sp + 右侧 20dp 对勾；选中项（图标/文字/对勾）统一 #184F95。
 */
@Composable
fun ChoiceDialog(
    title: String,
    options: List<ChoiceOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ChoiceDialogCard(
            title = title,
            options = options,
            selectedId = selectedId,
            onSelect = onSelect,
        )
    }
}

/** 弹窗卡片本体（Dialog 包一层；Paparazzi 里可直接单渲染）。 */
@Composable
internal fun ChoiceDialogCard(
    title: String,
    options: List<ChoiceOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    val colors = ClaudeTheme.colors
    val hapticCtx = androidx.compose.ui.platform.LocalContext.current
    val accent = if (colors.isDark) Color(0xFFA8C7FA) else Color(0xFF184F95)
    Column(
        modifier = Modifier
            .width(298.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (colors.isDark) colors.card else colors.surface)
            .padding(top = 18.dp, bottom = 8.dp)
    ) {
        Text(
            text = title,
            fontFamily = AnthropicSans,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 21.sp,
            color = colors.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(9.dp))
        options.forEach { option ->
            val selected = option.id == selectedId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable {
                        com.nairocy.claude.data.Haptics.tap(hapticCtx)
                        onSelect(option.id)
                    }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(option.icon),
                    contentDescription = null,
                    tint = if (selected) accent else colors.onSurface,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = option.label,
                    fontFamily = AnthropicSans,
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    color = if (selected) accent else colors.onSurface,
                )
                Spacer(Modifier.weight(1f))
                if (selected) {
                    Icon(
                        painter = painterResource(R.drawable.cds_icon_check),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
