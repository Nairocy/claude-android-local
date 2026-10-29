package com.nairocy.claude.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nairocy.claude.R
import com.nairocy.claude.ui.theme.AnthropicSans
import com.nairocy.claude.ui.theme.ClaudeTheme

/** 二级页顶栏：左 ← 返回、中间标题、右侧可选动作。官方实测高 49dp。 */
@Composable
fun BackTopBar(
    title: String,
    onBack: () -> Unit,
    titleFont: FontFamily = AnthropicSans,
    titleSize: Int = 17,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = ClaudeTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(49.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 2.dp)
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_back_arrow),
                contentDescription = "返回",
                tint = colors.onSurface,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = title,
            fontFamily = titleFont,
            fontWeight = FontWeight.Medium,
            fontSize = titleSize.sp,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 64.dp),
        )
        if (trailing != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
                contentAlignment = Alignment.Center,
            ) { trailing() }
        }
    }
}

/** 设置类分组的白卡片。 */
@Composable
fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = ClaudeTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(colors.card),
        content = content,
    )
}

/** 行间分隔：官方是 2dp 页面底色。 */
@Composable
fun CardSeparator() {
    val colors = ClaudeTheme.colors
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(colors.surface)
    )
}

/** 分组小标题（记忆 / 工具访问权限）：官方截图 14sp/18sp、次要文字色。 */
@Composable
fun SectionHeader(text: String) {
    val colors = ClaudeTheme.colors
    Text(
        text = text,
        fontFamily = AnthropicSans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        color = colors.secondaryText,
        modifier = Modifier.padding(top = 2.dp, bottom = 9.dp, start = 12.dp),
    )
}

/** 官方风格开关：42×24 轨道 + 18dp 白滑块；禁用态轨道按 38% 透明度变浅。 */
@Composable
fun ClaudeSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true,
) {
    val colors = ClaudeTheme.colors
    val track = when {
        checked && enabled -> Color(0xFF4176CF)
        checked -> Color(0xFF4176CF).copy(alpha = 0.38f)
        else -> colors.line
    }
    Box(
        modifier = Modifier
            .width(42.dp)
            .height(24.dp)
            .clip(CircleShape)
            .background(track)
            .then(
                if (enabled && onCheckedChange != null) {
                    Modifier.clickable { onCheckedChange(!checked) }
                } else {
                    Modifier
                }
            ),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(3.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/** 「测试版」小标签：官方 143×61px ≈ 44×19dp，#F3F3F3 底、11sp。 */
@Composable
private fun TestBadge(text: String) {
    val colors = ClaudeTheme.colors
    val bg = if (colors.isDark) Color(0xFF2E2E2B) else Color(0xFFF3F3F3)
    val fg = if (colors.isDark) colors.secondaryText else Color(0xFF52514E)
    Box(
        modifier = Modifier
            .height(19.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontFamily = AnthropicSans,
            fontSize = 11.sp,
            lineHeight = 11.sp,
            color = fg,
        )
    }
}

/**
 * 带开关的设置行。官方「功能」页：有图标时文字 x=46dp，没图标时 x=12dp；
 * 行高内容撑开（上下各 10dp），副标题 14sp/20sp；disabled 行整行变灰、开关半透明且不可点。
 */
@Composable
fun ToggleRow(
    title: String,
    subtitle: AnnotatedString? = null,
    subtitlePlain: String? = null,
    icon: Int? = null,
    badge: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = ClaudeTheme.colors
    val fg = if (enabled) colors.onSurface else colors.tertiaryText
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (enabled) Modifier.clickable { onCheckedChange(!checked) } else Modifier
            )
            .padding(
                start = 12.dp,
                end = 16.dp,
                top = 9.5.dp,
                bottom = 9.5.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontFamily = AnthropicSans,
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    color = fg,
                )
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    TestBadge(badge)
                }
            }
            val sub = subtitle ?: subtitlePlain?.let { AnnotatedString(it) }
            if (sub != null) {
                Text(
                    text = sub,
                    fontFamily = AnthropicSans,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = colors.tertiaryText,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        ClaudeSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** 只带文字的导航行（记忆文件），右侧 caret。 */
@Composable
fun ChevronRow(
    title: String,
    subtitle: String? = null,
    icon: Int? = null,
    onClick: () -> Unit,
) {
    val colors = ClaudeTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = colors.onSurface,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = AnthropicSans,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                color = colors.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontFamily = AnthropicSans,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = colors.tertiaryText,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Icon(
            painter = painterResource(R.drawable.cds_icon_caret_right),
            contentDescription = null,
            tint = colors.tertiaryText,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** 单选行（工具访问权限）：选中时标题/副标题/对勾一起变蓝。 */
@Composable
fun CheckRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = ClaudeTheme.colors
    // 夜间用浅蓝，深蓝 #284E90 在深色卡片上看不清
    val accent = if (colors.isDark) Color(0xFFA8C7FA) else Color(0xFF284E90)
    val fg = if (selected) accent else colors.onSurface
    val sub = if (selected) accent else colors.tertiaryText
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = AnthropicSans,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                color = fg,
            )
            Text(
                text = subtitle,
                fontFamily = AnthropicSans,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = sub,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
