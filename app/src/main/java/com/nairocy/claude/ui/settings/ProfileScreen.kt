package com.nairocy.claude.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nairocy.claude.R
import com.nairocy.claude.ui.components.BackTopBar
import com.nairocy.claude.ui.components.UserAvatar
import com.nairocy.claude.ui.theme.AnthropicSans
import com.nairocy.claude.ui.theme.ClaudeTheme
import java.io.File

/**
 * 个人资料（设置二级页，按官方截图量：1368px 宽 ÷ 3.25 = 421dp）。
 *
 * 实测：头像 95dp 圆、距顶栏 15dp；「编辑照片」胶囊 36dp；字段标签 15sp；
 * 输入框 56dp 高 / 左右 16dp / 圆角 12dp / 1dp #DDDDDD；按钮 40dp 全圆角；
 * 偏好块标题 17sp + 副标题 15sp #7B7974；文本域 128dp；「账户操作」距按钮 52dp。
 */
@Composable
fun ProfileScreen(
    email: String,
    initial: String,
    avatarFile: File?,
    fullName: String,
    preferredName: String,
    preferences: String,
    hasCustomAvatar: Boolean,
    saving: Boolean = false,
    error: String? = null,
    /** 打开浏览器失败时保留的一次性链接，给用户手动复制（见 HandoffLinkCard）。 */
    handoffUrl: String? = null,
    onBack: () -> Unit,
    onPickAvatar: () -> Unit,
    onResetAvatar: () -> Unit,
    onSaveProfile: (displayName: String, preferredName: String) -> Unit,
    onSavePreferences: (text: String) -> Unit,
    onDeleteAccount: () -> Unit,
) {
    val colors = ClaudeTheme.colors

    var name by remember(email) { mutableStateOf(fullName) }
    var nick by remember(email) { mutableStateOf(preferredName) }
    var prefs by remember(email) { mutableStateOf(preferences) }

    val profileDirty = name.trim() != fullName || nick.trim() != preferredName
    val prefsDirty = prefs.trim() != preferences

    Box(modifier = Modifier.fillMaxSize().background(colors.surface)) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            BackTopBar(title = "个人资料", onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(15.dp))

                UserAvatar(
                    size = 95.dp,
                    initial = initial,
                    file = avatarFile,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PillButton(text = "编辑照片", onClick = onPickAvatar)
                    if (hasCustomAvatar) {
                        Spacer(Modifier.width(8.dp))
                        PillButton(text = "恢复默认", onClick = onResetAvatar)
                    }
                }

                Spacer(Modifier.height(25.dp))

                FieldLabel("全名")
                Spacer(Modifier.height(4.dp))
                ProfileField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "你的全名",
                    height = 56.dp,
                )

                Spacer(Modifier.height(17.dp))

                FieldLabel("我们该怎么称呼您？")
                Spacer(Modifier.height(4.dp))
                ProfileField(
                    value = nick,
                    onValueChange = { nick = it },
                    placeholder = "怎么称呼你",
                    height = 56.dp,
                )

                Spacer(Modifier.height(16.dp))

                SaveButton(
                    text = "更新资料",
                    enabled = profileDirty && !saving,
                    onClick = { onSaveProfile(name.trim(), nick.trim()) },
                )

                Spacer(Modifier.height(26.dp))

                Text(
                    text = "Claude 在回复中应考虑哪些个人偏好?",
                    fontFamily = AnthropicSans,
                    fontSize = 17.sp,
                    lineHeight = 23.sp,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "您的偏好设置将应用于所有对话。",
                    fontFamily = AnthropicSans,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = colors.tertiaryText,
                )
                Spacer(Modifier.height(3.dp))
                ProfileField(
                    value = prefs,
                    onValueChange = { prefs = it },
                    placeholder = "在给出详细回答前先提出澄清性问题",
                    height = 128.dp,
                    multiline = true,
                )

                Spacer(Modifier.height(16.dp))

                SaveButton(
                    text = "保存偏好设置",
                    enabled = prefsDirty && !saving,
                    onClick = { onSavePreferences(prefs.trim()) },
                )

                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = error,
                        fontFamily = AnthropicSans,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = colors.error,
                    )
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    val colors = ClaudeTheme.colors
    Text(
        text = text,
        fontFamily = AnthropicSans,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        color = colors.onSurface,
    )
}

/** 白底 + 1dp #DDDDDD 描边 + 12dp 圆角；官方输入框 56dp、文本域 128dp。 */
@Composable
private fun ProfileField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    height: androidx.compose.ui.unit.Dp,
    multiline: Boolean = false,
) {
    val colors = ClaudeTheme.colors
    val border = if (colors.isDark) colors.line else FieldBorder
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.card)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = if (multiline) 14.dp else 0.dp),
        contentAlignment = if (multiline) Alignment.TopStart else Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                fontFamily = AnthropicSans,
                fontSize = if (multiline) 17.sp else 16.sp,
                lineHeight = if (multiline) 24.sp else 22.sp,
                color = colors.tertiaryText,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = !multiline,
            textStyle = TextStyle(
                fontFamily = AnthropicSans,
                fontSize = if (multiline) 17.sp else 16.sp,
                lineHeight = if (multiline) 24.sp else 22.sp,
                color = colors.onSurface,
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.brand),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 「编辑照片」这类小胶囊：36dp 高、白底、1dp #DDDDDD、14sp。 */
@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    val colors = ClaudeTheme.colors
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(colors.card)
            .border(1.dp, if (colors.isDark) colors.line else FieldBorder, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontFamily = AnthropicSans,
            fontSize = 14.sp,
            color = colors.onSurface,
        )
    }
}

/** 40dp 全圆角按钮：可用 = 黑底白字；未改动 = #9C9A92 底白字（官方截图就是这个灰）。 */
@Composable
private fun SaveButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = ClaudeTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(CircleShape)
            .background(if (enabled) colors.onSurface else DisabledButton)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontFamily = AnthropicSans,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            color = Color.White,
        )
    }
}

@Composable
private fun HandoffLinkCard(url: String) {
    val colors = ClaudeTheme.colors
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var copied by remember(url) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceVariant)
            .padding(12.dp),
    ) {
        Text(
            text = url,
            fontFamily = AnthropicSans,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = colors.secondaryText,
            maxLines = 3,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (copied) "已复制" else "复制链接",
            fontFamily = AnthropicSans,
            fontSize = 14.sp,
            color = colors.brand,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    runCatching {
                        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("账户管理链接", url))
                        copied = true
                    }
                }
                .padding(horizontal = 2.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "链接 2 分钟内有效；过期就回来重新点一次「删除账户」。",
            fontFamily = AnthropicSans,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = colors.tertiaryText,
        )
    }
}

/** 删除账户：16dp 垃圾桶 + 16sp 文字，红 #D03B3B；点击跳统一账号网页（App 内不删）。 */
@Composable
private fun DeleteAccountRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_trash),
            contentDescription = null,
            tint = DeleteRed,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "删除账户",
            fontFamily = AnthropicSans,
            fontSize = 16.sp,
            color = DeleteRed,
        )
    }
}

/** 官方输入框描边（截图采样 #DDDDDD）。 */
private val FieldBorder = Color(0xFFDDDDDD)

/** 未改动时按钮底色（截图采样 #9C9A92）。 */
private val DisabledButton = Color(0xFF9C9A92)

/** 删除红（截图采样 #D03B3B）。 */
private val DeleteRed = Color(0xFFD03B3B)
