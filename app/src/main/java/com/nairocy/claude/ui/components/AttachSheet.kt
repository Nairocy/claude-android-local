package com.nairocy.claude.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nairocy.claude.R
import com.nairocy.claude.ui.theme.AnthropicSans
import com.nairocy.claude.ui.theme.ClaudeTheme

/**
 * 「添加到对话」底部面板（对齐官方 04_attach_sheet 截图）：
 * 三格白色卡片（相机 / 照片 / 文件，图标落在浅灰圆底上）
 * + 「添加到项目」独立卡片
 * + 「网页搜索 / 记忆」合并卡片（行间细分隔线）。
 * 官方还有「连接器」行，本项目不接连接器，不摆这一行（不做假按钮）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onCamera: () -> Unit = {},
    onPhoto: () -> Unit,
    onFile: () -> Unit,
    projectLabel: String = "无",
    onAddToProject: () -> Unit = {},
    /** 新对话（首页）还没有会话可归入项目，这时不摆这一行 */
    showProjectRow: Boolean = true,
    webSearch: Boolean = false,
    onWebSearchChange: (Boolean) -> Unit = {},
    memory: Boolean = false,
    onMemoryChange: (Boolean) -> Unit = {},
) {
    if (!visible) return
    val colors = ClaudeTheme.colors
    val iconCircle = if (colors.isDark) Color(0xFF2C2C2A) else Color(0xFFE7E7E7)
    val tileShape = RoundedCornerShape(16.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        scrimColor = Color.Black.copy(alpha = 0.32f),
        dragHandle = { SheetHandle(if (colors.isDark) Color(0xFF4A4A47) else Color(0xFFD8D6D1)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            // 标题行：左 X / 中间标题
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .padding(horizontal = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(42.dp)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "关闭",
                        tint = colors.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = "添加到对话",
                    fontFamily = AnthropicSans,
                    fontSize = 17.sp,
                    color = colors.onSurface,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            Spacer(Modifier.height(16.dp))

            // 三格：白色卡片 + 浅灰圆底图标（官方素材 cds_icon_*）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                AttachTile(R.drawable.cds_icon_camera, "相机", colors.card, iconCircle, tileShape, Modifier.weight(1f), onCamera)
                AttachTile(R.drawable.cds_icon_image, "照片", colors.card, iconCircle, tileShape, Modifier.weight(1f), onPhoto)
                AttachTile(R.drawable.cds_icon_file, "文件", colors.card, iconCircle, tileShape, Modifier.weight(1f), onFile)
            }

            Spacer(Modifier.height(13.dp))

            // 「添加到项目」独立卡片
            if (showProjectRow) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 13.dp)
                        .clip(tileShape)
                        .background(colors.card),
                ) {
                    SheetRow(
                        icon = R.drawable.cds_icon_archive,
                        label = "添加到项目",
                        sublabel = projectLabel,
                        iconCircle = iconCircle,
                        onClick = onAddToProject,
                    )
                }
                Spacer(Modifier.height(13.dp))
            }

            // 「网页搜索 / 记忆」合并卡片
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp)
                    .clip(tileShape)
                    .background(colors.card),
            ) {
                SheetSwitchRow(
                    icon = R.drawable.cds_icon_globe,
                    label = "网页搜索",
                    checked = webSearch,
                    iconCircle = iconCircle,
                    onChange = onWebSearchChange,
                )
                SheetDivider(colors.line)
                SheetSwitchRow(
                    icon = R.drawable.cds_icon_memory,
                    label = "记忆",
                    checked = memory,
                    iconCircle = iconCircle,
                    onChange = onMemoryChange,
                )
            }
        }
    }
}

@Composable
private fun SheetHandle(color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
    }
}

@Composable
private fun AttachTile(
    icon: Int,
    label: String,
    bg: Color,
    iconCircle: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = ClaudeTheme.colors
    Column(
        modifier = modifier
            .height(112.dp)
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(47.dp)
                .clip(CircleShape)
                .background(iconCircle),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                colorFilter = ColorFilter.tint(colors.onSurface),
            )
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = label,
            fontFamily = AnthropicSans,
            fontSize = 13.sp,
            color = colors.onSurface,
        )
    }
}

/** 右侧带「值 + ›」的行（值作为副标题放在标题下面，和官方一致）。 */
@Composable
private fun SheetRow(
    icon: Int,
    label: String,
    sublabel: String? = null,
    iconCircle: Color,
    onClick: () -> Unit = {},
) {
    val colors = ClaudeTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 17.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, iconCircle)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.onSurface)
            if (sublabel != null) {
                Text(
                    text = sublabel,
                    fontFamily = AnthropicSans,
                    fontSize = 12.sp,
                    color = colors.tertiaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Image(
            painter = painterResource(R.drawable.cds_icon_caret_right),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            colorFilter = ColorFilter.tint(colors.tertiaryText),
        )
    }
}

@Composable
private fun SheetSwitchRow(
    icon: Int,
    label: String,
    checked: Boolean,
    iconCircle: Color,
    onChange: (Boolean) -> Unit,
) {
    val colors = ClaudeTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 17.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, iconCircle)
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            fontFamily = AnthropicSans,
            fontSize = 15.sp,
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF2A78D6),
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFD8D6D1),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

/** 行左侧 42dp 浅灰圆底 + 20dp 图标（官方行图标配方）。 */
@Composable
private fun RowIcon(icon: Int, circle: Color) {
    val colors = ClaudeTheme.colors
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(circle),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            colorFilter = ColorFilter.tint(colors.onSurface),
        )
    }
}

@Composable
private fun SheetDivider(color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 71.dp, end = 17.dp)
            .height(1.dp)
            .background(color),
    )
}
