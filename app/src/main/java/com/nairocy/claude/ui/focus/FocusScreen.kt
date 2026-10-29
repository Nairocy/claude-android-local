package com.nairocy.claude.ui.focus

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nairocy.claude.R
import com.nairocy.claude.data.Prefs
import com.nairocy.claude.ui.components.BackTopBar
import com.nairocy.claude.ui.theme.AnthropicSans
import com.nairocy.claude.ui.theme.ClaudeTheme

private const val SHEET_HOURS = "hours"
private const val SHEET_MINUTES = "minutes"

private val HourOptions = listOf("-") + (1..12).map { "$it hr" }
private val MinuteOptions = listOf("-", "15 min", "30 min", "45 min")
private val Days = listOf("S", "M", "T", "W", "T", "F", "S")

private val PillBorder = Color(0xFFE7E6E1)
private val PillFill = Color(0xFFFCFCFB)
private val DividerColor = Color(0xFFEDEDEB)
private val DayDisc = Color(0xFFE1E1DF)
private val HandleColor = Color(0xFFE6E6E6)
private val SelectedBg = Color(0xFFF3F3F3)
private val SelectedBlueLight = Color(0xFF184F95)

/**
 * 时间与专注（设置 → 时间与专注），按官方 3 张截图实测：
 *
 * 页面：左右 16dp；顶栏下 22dp 是 15sp/600 的 "Time and focus"；
 * 标题 15sp、副标题 16sp/22sp（#898781）；两个下拉框 111×39dp、圆角 10dp、
 * 1dp #E7E6E1 边框、间距 12dp；分隔线 1dp #EDEDEB 左右 16dp；
 * 星期圆 32dp（间距 12dp）、底色 #E1E1DF、字母 13sp #898781。
 *
 * 下拉：底部弹层，白色、顶部圆角 15dp、把手 36×4dp；
 * 行高 40dp、文字 16sp/20dp；选中行底色 #F3F3F3（左右 4dp、圆角 10dp）+ 右侧对勾；
 * 遮罩 40% 黑；高度 = 36 + 行数×40 + 10。
 */
@Composable
fun FocusScreen(
    onBack: () -> Unit,
    /** 仅截图测试用：进页面就打开某个下拉（"hours" / "minutes"）。 */
    initiallyOpenSheet: String? = null,
) {
    val colors = ClaudeTheme.colors
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }

    var hours by remember { mutableStateOf(prefs.breakHours) }
    var minutes by remember { mutableStateOf(prefs.breakMinutes) }
    var quietDays by remember {
        mutableStateOf(prefs.quietDays.split(",").filter { it.isNotBlank() }.toSet())
    }
    var sheet by remember { mutableStateOf(initiallyOpenSheet) }

    // 弹层开着时，返回键先收弹层（否则会直接退到设置页）
    BackHandler(enabled = sheet != null) { sheet = null }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .systemBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BackTopBar(title = "时间与专注", onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 24.dp)
            ) {
                Text(
                    text = "Time and focus",
                    fontFamily = AnthropicSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(37.dp))

                Text(
                    text = "Break reminders",
                    fontFamily = AnthropicSans,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(6.5.dp))
                Text(
                    text = "Get a nudge to take a break from Claude. You can snooze or adjust anytime.",
                    fontFamily = AnthropicSans,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = colors.mutedText,
                )
                Spacer(Modifier.height(10.5.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DropdownPill(
                        value = hours,
                        onClick = { sheet = SHEET_HOURS },
                    )
                    DropdownPill(
                        value = minutes,
                        onClick = { sheet = SHEET_MINUTES },
                    )
                }
                Spacer(Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(if (colors.isDark) Color(0xFF2A2A29) else DividerColor)
                )
                Spacer(Modifier.height(20.5.dp))

                Text(
                    text = "Quiet hours",
                    fontFamily = AnthropicSans,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(6.5.dp))
                Text(
                    text = "Set time limits for Claude. You can dismiss or adjust anytime.",
                    fontFamily = AnthropicSans,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = colors.mutedText,
                )
                Spacer(Modifier.height(14.5.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Days.forEachIndexed { index, label ->
                        val on = index.toString() in quietDays
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        on -> colors.onSurface
                                        colors.isDark -> Color(0xFF2C2C2A)
                                        else -> DayDisc
                                    }
                                )
                                .clickable {
                                    val next = if (on) quietDays - index.toString() else quietDays + index.toString()
                                    quietDays = next
                                    prefs.quietDays = next.sorted().joinToString(",")
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                fontFamily = AnthropicSans,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = if (on) colors.surface else colors.mutedText,
                            )
                        }
                    }
                }
            }
        }

        if (sheet != null) {
            val options = if (sheet == SHEET_HOURS) HourOptions else MinuteOptions
            val selected = if (sheet == SHEET_HOURS) hours else minutes
            Box(modifier = Modifier.fillMaxSize()) {
                // 遮罩：官方 40% 黑
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.40f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { sheet = null }
                )
                OptionSheet(
                    title = null,
                    options = options,
                    selected = selected,
                    onPick = { value ->
                        if (sheet == SHEET_HOURS) {
                            hours = value
                            prefs.breakHours = value
                        } else {
                            minutes = value
                            prefs.breakMinutes = value
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun DropdownPill(value: String, onClick: () -> Unit) {
    val colors = ClaudeTheme.colors
    Row(
        modifier = Modifier
            .width(111.dp)
            .height(39.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (colors.isDark) colors.card else PillFill)
            .border(
                1.dp,
                if (colors.isDark) colors.line else PillBorder,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = value,
            fontFamily = AnthropicSans,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            color = colors.onSurface,
        )
        Spacer(Modifier.weight(1f))
        Icon(
            painter = painterResource(R.drawable.ic_chevron_down),
            contentDescription = null,
            tint = colors.mutedText,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * 底部选项弹层。高度随行数收缩：36（把手区）+ 行数×40 + 10。
 * 选中行是 #F3F3F3 圆角条（左右 4dp）+ 右侧蓝色对勾。
 */
@Composable
private fun OptionSheet(
    title: String?,
    options: List<String>,
    selected: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ClaudeTheme.colors
    val accent = if (colors.isDark) Color(0xFFA8C7FA) else SelectedBlueLight
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp))
            .background(if (colors.isDark) colors.card else Color.White)
            .padding(top = 14.dp, bottom = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(36.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(if (colors.isDark) Color(0xFF3A3A38) else HandleColor)
        )
        Spacer(Modifier.height(18.dp))
        title?.let {
            Text(
                text = it,
                fontFamily = AnthropicSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                modifier = Modifier.padding(start = 20.dp, bottom = 6.dp),
                color = colors.onSurface,
            )
        }
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 4.dp)
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (colors.isDark) Color(0xFF2E2E2B) else SelectedBg)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onPick(option) }
                        .padding(start = 16.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = option,
                        fontFamily = AnthropicSans,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.weight(1f))
                    if (isSelected) {
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
}
