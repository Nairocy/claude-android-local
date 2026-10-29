package com.nairocy.claude.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nairocy.claude.ui.theme.AnthropicSans
import com.nairocy.claude.ui.theme.ClaudeTheme

/**
 * 长按菜单的通用外壳：宽度跟着内容走（不铺满屏幕），圆角 14dp。
 * 用户明确要求「注意控制弹窗大小」，所以上限 200dp、最小 150dp。
 */
@Composable
fun ContextMenuCard(
    minWidth: Int = 118,
    maxWidth: Int = 176,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = ClaudeTheme.colors
    Column(
        modifier = Modifier
            // 行是 fillMaxWidth：直接 widthIn(max) 会永远撑到上限，所以先按内容最大固有宽度，
            // 再用 widthIn 夹住上下限 —— 这样菜单才是「贴合文字」的窄条。
            .width(androidx.compose.foundation.layout.IntrinsicSize.Max)
            .widthIn(min = minWidth.dp, max = maxWidth.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .padding(vertical = 6.dp),
        content = content,
    )
}

/** 菜单项：图标 + 文字，danger 用错误色（删除）。 */
@Composable
fun ContextMenuRow(
    icon: Int,
    label: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit = {},
) {
    val colors = ClaudeTheme.colors
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val iconTint = when {
        danger -> colors.error
        !enabled -> colors.tertiaryText.copy(alpha = 0.45f)
        else -> colors.tertiaryText
    }
    val textColor = when {
        danger -> colors.error
        !enabled -> colors.tertiaryText.copy(alpha = 0.55f)
        else -> colors.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (enabled) {
                    Modifier.clickable {
                        com.nairocy.claude.data.Haptics.tap(ctx)
                        onClick()
                    }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = label,
            fontFamily = AnthropicSans,
            fontSize = 14.sp,
            color = textColor,
        )
    }
}

/**
 * 贴着锚点显示的定位器：Popup(alignment=…) 是按窗口算的，会跑到屏幕角落，
 * 这里按锚点 bounds 算，默认贴右下、越界自动夹回屏幕内。
 */
class AnchoredPopupPosition(
    private val gapPx: Int = 0,
    private val alignRight: Boolean = true,
) : androidx.compose.ui.window.PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: androidx.compose.ui.unit.IntSize,
    ): androidx.compose.ui.unit.IntOffset {
        val x = if (alignRight) anchorBounds.right - popupContentSize.width else anchorBounds.left
        val y = anchorBounds.bottom + gapPx
        return androidx.compose.ui.unit.IntOffset(
            x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}

/** 菜单里的分隔线。 */
@Composable
fun ContextMenuDivider() {
    val colors = ClaudeTheme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.fillMaxWidth().height(1.dp).background(colors.line)) {}
    }
}
