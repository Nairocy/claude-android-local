package com.nairocy.claude.ui.permissions

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nairocy.claude.R
import com.nairocy.claude.ui.components.BackTopBar
import com.nairocy.claude.ui.components.CardSeparator
import com.nairocy.claude.ui.components.GroupCard
import com.nairocy.claude.ui.theme.AnthropicSans
import com.nairocy.claude.ui.theme.ClaudeTheme

/**
 * 权限页（设置 → 权限），按官方截图 1:1：
 *
 * 卡片左右 12dp、顶栏下 19dp、行高 84dp（上下 9.5dp + 标题 21 + 2 + 两行副标题 40）；
 * 行内 12dp 内边距 + 24dp 图标 + 10dp 间隔 → 文字 x=58dp；
 * 右侧「设置」14sp + 6dp + 14dp 外链图标（墨迹 10.5dp），离卡片右缘 16dp。
 *
 * 点整行打开本应用的系统设置页（权限在那里开关）。
 */
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val colors = ClaudeTheme.colors
    val ctx = LocalContext.current

    val openSystemSettings = {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", ctx.packageName, null),
        )
        runCatching { ctx.startActivity(intent) }
        Unit
    }

    // 通知有独立的系统页面（Android 8+）：直接跳到本应用的通知开关，比落到应用详情页少点两下
    val openNotificationSettings = {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        runCatching { ctx.startActivity(intent) }.onFailure { openSystemSettings() }
        Unit
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .systemBarsPadding()
    ) {
        BackTopBar(title = "权限", onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 19.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                GroupCard {
                    PermissionRow(
                        icon = R.drawable.cds_icon_location,
                        title = "位置",
                        subtitle = "若要允许访问您的位置信息，请在系统设置中开启该权限。",
                        onClick = openSystemSettings,
                    )
                    CardSeparator()
                    PermissionRow(
                        icon = R.drawable.cds_icon_calendar,
                        title = "日历",
                        subtitle = "若要允许访问您的日历，请在系统设置中开启该权限。",
                        onClick = openSystemSettings,
                    )
                    CardSeparator()
                    // 通知（Android 13+ 是运行时权限）：设置→通知→「回复」要用它，
                    // 跳的是本应用的通知专属页面，不用在应用详情页里再翻一层。
                    // 图标用设置页同一枚官方铃铛 ic_set_bell（用户 2026-10-29 要求，别换回自绘版）。
                    PermissionRow(
                        icon = R.drawable.ic_set_bell,
                        title = "通知",
                        subtitle = "若要接收 Claude 的回复完成通知，请在系统设置中开启该权限。",
                        onClick = openNotificationSettings,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val colors = ClaudeTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 16.dp, top = 9.5.dp, bottom = 9.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = colors.onSurface,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = AnthropicSans,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                color = colors.onSurface,
            )
            Text(
                text = subtitle,
                fontFamily = AnthropicSans,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = colors.tertiaryText,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "设置",
                fontFamily = AnthropicSans,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = colors.tertiaryText,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                painter = painterResource(R.drawable.ic_ext_link),
                contentDescription = null,
                tint = colors.tertiaryText,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
