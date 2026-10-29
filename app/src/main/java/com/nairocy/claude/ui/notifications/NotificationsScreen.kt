package com.nairocy.claude.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nairocy.claude.R
import com.nairocy.claude.data.Prefs
import com.nairocy.claude.ui.components.BackTopBar
import com.nairocy.claude.ui.components.CardSeparator
import com.nairocy.claude.ui.components.GroupCard
import com.nairocy.claude.ui.components.ToggleRow
import com.nairocy.claude.ui.theme.ClaudeTheme

/**
 * 通知页（设置 → 通知），按官方截图：
 *
 * 卡片左右 12dp、顶栏下 19dp、行高 63.7（1 行副标题）/ 84.6dp（2 行），
 * 行内 12dp + 24dp 图标 + 10dp → 文字 x=46dp；开关 42×24、离卡片右缘 16dp。
 *
 * 开关：`Prefs.notifyReplies` 控制「生成中常驻进度 + 回复完成」两条通知
 * （见 `data/ChatNotifications.kt`）；系统通知权限没给时整条链路静默，权限页可跳系统开关。
 */
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val colors = ClaudeTheme.colors
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var replies by remember { mutableStateOf(prefs.notifyReplies) }
    var product by remember { mutableStateOf(prefs.notifyProduct) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .systemBarsPadding()
    ) {
        BackTopBar(title = "通知", onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 19.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                GroupCard {
                    ToggleRow(
                        title = "回复",
                        subtitlePlain = "Claude 回复完成时通知您。",
                        icon = R.drawable.cds_icon_reply,
                        checked = replies,
                        onCheckedChange = {
                            replies = it
                            prefs.notifyReplies = it
                        },
                    )
                    CardSeparator()
                    ToggleRow(
                        title = "产品更新",
                        subtitlePlain = "接收有关新功能、使用技巧和不定期促销活动的通知",
                        icon = R.drawable.cds_icon_megaphone,
                        checked = product,
                        onCheckedChange = {
                            product = it
                            prefs.notifyProduct = it
                        },
                    )
                }
            }
        }
    }
}
