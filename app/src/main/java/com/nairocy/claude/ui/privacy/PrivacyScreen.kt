package com.nairocy.claude.ui.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nairocy.claude.data.Prefs
import com.nairocy.claude.ui.components.BackTopBar
import com.nairocy.claude.ui.components.ClaudeSwitch
import com.nairocy.claude.ui.theme.AnthropicSans
import com.nairocy.claude.ui.theme.ClaudeTheme

private const val PRIVACY_CENTER = "https://privacy.claude.com/zh-CN/"
private const val PRIVACY_POLICY = "https://www.anthropic.com/legal/privacy"
private const val PRIVACY_LEARN =
    "https://privacy.claude.com/en/articles/12109829-how-do-i-change-my-model-improvement-privacy-settings"

private val LinkBlueLight = Color(0xFF184F95)
private val LinkBlueDark = Color(0xFFA8C7FA)
private val DividerGray = Color(0xFFD8D8D6)

/**
 * 隐私（设置 → 隐私），按官方截图：
 *
 * 左右 24dp；`数据隐私` 17sp/600；正文 15sp/21sp（含硬换行）：
 * 隐私中心 / 隐私政策 / 了解更多 都是 #184F95 下划线链接；
 * 分隔线 1dp #D8D8D6（左右 16dp）；行标题 16sp/600 + 右侧 42×24 开关，副标题 15sp/21sp。
 */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val colors = ClaudeTheme.colors
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var train by remember { mutableStateOf(prefs.trainOnChats) }
    val linkColor = if (colors.isDark) LinkBlueDark else LinkBlueLight

    val linkStyle = TextLinkStyles(
        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
    )
    val para1 = buildAnnotatedString { append("Anthropic 坚持透明的数据处理方式") }
    val para2 = buildAnnotatedString {
        append("保护您的数据安全是我们的优先事项。 了解使用 Anthropic 产品时您的信息如何受到保护，并访问我们的")
        withLink(LinkAnnotation.Url(PRIVACY_CENTER, linkStyle)) { append("隐私中心") }
        append("和")
        withLink(LinkAnnotation.Url(PRIVACY_POLICY, linkStyle)) { append("隐私政策") }
        append("了解更多详情。")
    }
    val subtitle = buildAnnotatedString {
        append("允许使用您的对话和编程会话来训练和改进\nAnthropic AI 模型。 ")
        withLink(LinkAnnotation.Url(PRIVACY_LEARN, linkStyle)) { append("了解更多") }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .systemBarsPadding()
    ) {
        BackTopBar(title = "隐私", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 15.dp, bottom = 24.dp)
        ) {
            Text(
                text = "数据隐私",
                fontFamily = AnthropicSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(7.5.dp))
            Text(
                text = para1,
                fontFamily = AnthropicSans,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(4.5.dp))
            Text(
                text = para2,
                fontFamily = AnthropicSans,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(15.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(if (colors.isDark) colors.line else DividerGray)
            )
            Spacer(Modifier.height(16.5.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "帮助改进我们的 AI 模型",
                    fontFamily = AnthropicSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                ClaudeSwitch(
                    checked = train,
                    onCheckedChange = {
                        train = it
                        prefs.trainOnChats = it
                    },
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontFamily = AnthropicSans,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = colors.onSurface,
            )
        }
    }
}
