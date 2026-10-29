package com.nairocy.claude.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nairocy.claude.R
import com.nairocy.claude.data.Plan
import com.nairocy.claude.data.Prefs
import com.nairocy.claude.ui.theme.AnthropicSans
import com.nairocy.claude.ui.theme.BadgeFreeBgDark
import com.nairocy.claude.ui.theme.BadgeFreeBgLight
import com.nairocy.claude.ui.theme.BadgeFreeFgDark
import com.nairocy.claude.ui.theme.BadgeFreeFgLight
import com.nairocy.claude.ui.theme.BadgeMaxBgDark
import com.nairocy.claude.ui.theme.BadgeMaxBgLight
import com.nairocy.claude.ui.theme.BadgeMaxFgDark
import com.nairocy.claude.ui.theme.BadgeMaxFgLight
import com.nairocy.claude.ui.theme.BadgeProBg
import com.nairocy.claude.ui.theme.BadgeProFg
import com.nairocy.claude.ui.theme.ClaudeTheme

/**
 * 设置页（官方版式；右上角信息菜单可点）。
 *
 * 版式按官方设置页截图量过（1368px 宽，≈3.25px/dp）：
 * 卡片左右 12dp、圆角 15dp、组间距 12dp；行高 56dp（带副标题 64dp）；
 * 行内 = 12dp 左内边距 + 24dp 图标 + 10dp 间隔 → 文字 x=46dp；
 * 行间分隔是 2dp 的页面底色，不是细线。
 *
 * 官方专属项里「升级横幅 / 连接器 / 语音」不做。
 * 二级页样式待用户提供，所以列表里的行都还不接点击。
 */
@Composable
fun SettingsScreen(
    onMenuClick: () -> Unit,
    /** 登录邮箱：由 MainActivity 从 AccountStore 传真实值，这里不再有兜底地址 */
    email: String = "",
    /** 订阅档位：由 MainActivity 从 EntitlementStore 传；截图测试也用它出三态 */
    plan: Plan = Plan.FREE,
    /** 首次拉取完成前不画徽标，避免先闪一下「Free」再变「Pro」；拉取失败也置真（用 Free 兜底） */
    showBadge: Boolean = true,
    /** 触感反馈总开关（Prefs.haptic） */
    haptic: Boolean = true,
    onHapticChange: (Boolean) -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenApiSettings: () -> Unit = {},
    onLogout: () -> Unit = {},
    themeMode: String = "system",
    onThemeChange: (String) -> Unit = {},
    fontMode: String = "default",
    onFontModeChange: (String) -> Unit = {},
    onOpenFeatures: () -> Unit = {},
    onOpenBilling: () -> Unit = {},
    onOpenPermissions: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenFocus: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    /** 仅截图测试用：进页面就把信息菜单摊开。 */
    initiallyInfoOpen: Boolean = false,
    /** 仅截图测试用：进页面就把颜色模式 / 字体样式弹窗打开。 */
    initiallyColorModeOpen: Boolean = false,
    initiallyFontModeOpen: Boolean = false,
    /** 仅截图测试用：进页面就把退出登录确认框打开。 */
    initiallyLogoutOpen: Boolean = false,
) {
    val colors = ClaudeTheme.colors
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val enabledFeatures = listOf(
        prefs.artifacts,
        prefs.inChatViz,
        prefs.codeExec,
        prefs.modelFallback,
    ).count { it }

    // API 设置行的副标题：一眼看出是否已经填了 Key
    val apiCfg = remember { com.nairocy.claude.data.SettingsStore(ctx).load() }

    var infoOpen by remember { mutableStateOf(initiallyInfoOpen) }
    var colorModeOpen by remember { mutableStateOf(initiallyColorModeOpen) }
    var fontModeOpen by remember { mutableStateOf(initiallyFontModeOpen) }
    var logoutOpen by remember { mutableStateOf(initiallyLogoutOpen) }
    var licensesOpen by remember { mutableStateOf(false) }

    val statusTop = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.surface)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            SettingsTopBar(
                onMenuClick = onMenuClick,
                onInfoClick = {
                    infoOpen = true
                },
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    AccountRow(
                        email = email.ifBlank { "本机" },
                        badge = null,
                        badgeStyle = BadgeStyle.MAX,
                    )
                }

                item {
                    SettingsCard {
                        SettingsItem(R.drawable.ic_set_profile, "个人资料", onClick = onOpenProfile)
                        RowGap()
                        SettingsItem(
                            icon = R.drawable.ic_key,
                            title = "API 设置",
                            subtitle = if (apiCfg.apiKey.isBlank()) "未配置 Key"
                            else apiCfg.model.ifBlank { "已配置" },
                            onClick = onOpenApiSettings,
                        )
                    }
                }

                item {
                    SettingsCard {
                        SettingsItem(
                            icon = R.drawable.ic_set_features,
                            title = "功能",
                            subtitle = "已启用 $enabledFeatures 项",
                            onClick = onOpenFeatures,
                        )
                        RowGap()
                        SettingsItem(
                            icon = R.drawable.ic_set_permissions,
                            title = "权限",
                            onClick = onOpenPermissions,
                        )
                    }
                }

                item {
                    SettingsCard {
                        SettingsItem(
                            icon = R.drawable.ic_set_moon,
                            title = "颜色模式",
                            subtitle = themeLabel(themeMode),
                            onClick = { colorModeOpen = true },
                        )
                        RowGap()
                        SettingsItem(
                            icon = R.drawable.ic_set_font,
                            title = "字体样式",
                            subtitle = fontLabel(fontMode),
                            onClick = { fontModeOpen = true },
                        )
                    }
                }

                item {
                    SettingsCard {
                        SettingsItem(
                            icon = R.drawable.ic_set_haptic,
                            title = "触感反馈",
                            trailing = {
                                com.nairocy.claude.ui.components.ClaudeSwitch(
                                    checked = haptic,
                                    onCheckedChange = onHapticChange,
                                )
                            },
                            onClick = { onHapticChange(!haptic) },
                        )
                        RowGap()
                        SettingsItem(
                            icon = R.drawable.ic_set_bell,
                            title = "通知",
                            onClick = onOpenNotifications,
                        )
                        RowGap()
                        SettingsItem(
                            icon = R.drawable.ic_set_focus,
                            title = "时间与专注",
                            onClick = onOpenFocus,
                        )
                        RowGap()
                        SettingsItem(
                            icon = R.drawable.ic_set_privacy,
                            title = "隐私",
                            onClick = onOpenPrivacy,
                        )
                    }
                }

            }
        }

        if (infoOpen) {
            // 点击空白处关闭（无涟漪）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { infoOpen = false }
            )
            InfoMenu(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = statusTop + 49.dp, end = 4.dp),
                onEntry = { entry ->
                    infoOpen = false
                    when (entry) {
                        InfoEntry.Terms -> openUrl(ctx, "https://www.anthropic.com/legal/consumer-terms")
                        InfoEntry.Usage -> openUrl(ctx, "https://www.anthropic.com/legal/aup")
                        InfoEntry.Privacy -> openUrl(ctx, "https://www.anthropic.com/legal/privacy")
                        InfoEntry.Licenses -> licensesOpen = true
                        InfoEntry.Help -> openUrl(ctx, "https://support.claude.com/")
                    }
                },
            )
        }
    }

    if (licensesOpen) {
        LicensesDialog(onDismiss = { licensesOpen = false })
    }

    if (colorModeOpen) {
        ChoiceDialog(
            title = "颜色模式",
            options = listOf(
                ChoiceOption("system", "跟随系统", R.drawable.cds_icon_device),
                ChoiceOption("light", "浅色", R.drawable.cds_icon_sun),
                ChoiceOption("dark", "深色", R.drawable.ic_set_moon),
            ),
            selectedId = themeMode,
            onSelect = { mode ->
                colorModeOpen = false
                onThemeChange(mode)
            },
            onDismiss = { colorModeOpen = false },
        )
    }

    if (fontModeOpen) {
        ChoiceDialog(
            title = "字体样式",
            options = listOf(
                ChoiceOption("default", "默认", R.drawable.cds_icon_font_mode),
                ChoiceOption("system", "系统", R.drawable.cds_icon_device),
            ),
            selectedId = fontMode,
            onSelect = { mode ->
                fontModeOpen = false
                onFontModeChange(mode)
            },
            onDismiss = { fontModeOpen = false },
        )
    }

    if (logoutOpen) {
        LogoutDialog(
            onDismiss = { logoutOpen = false },
            onConfirm = {
                logoutOpen = false
                onLogout()
            },
        )
    }
}

/**
 * 账号卡片徽标样式（尺寸固定 18dp 胶囊，只换配色）。
 * 档位来自 [Plan]；文案直接用 `Plan.label`（Free / Pro / Max）。
 */
private enum class BadgeStyle { FREE, PRO, MAX }

private fun badgeStyleOf(plan: Plan): BadgeStyle = when (plan) {
    Plan.FREE -> BadgeStyle.FREE
    Plan.PRO -> BadgeStyle.PRO
    Plan.MAX -> BadgeStyle.MAX
}

/** 徽标「底 / 字」配色；深浅色两套在 theme 包里有具名常量，这里只做选择。 */
private fun badgeColors(style: BadgeStyle, dark: Boolean): Pair<Color, Color> = when (style) {
    BadgeStyle.FREE ->
        if (dark) BadgeFreeBgDark to BadgeFreeFgDark else BadgeFreeBgLight to BadgeFreeFgLight
    BadgeStyle.PRO -> BadgeProBg to BadgeProFg
    BadgeStyle.MAX ->
        if (dark) BadgeMaxBgDark to BadgeMaxFgDark else BadgeMaxBgLight to BadgeMaxFgLight
}

/** 与官方完全一致的版本号（用户要求一字不差）。 */
private const val OFFICIAL_VERSION = "版本 1.261023.20 (26102320)"

/** 官方「退出登录」砖红（截图采样 #832E2B）。 */
private val LogoutRedLight = Color(0xFF832E2B)
private val LogoutRedDark = Color(0xFFE08A80)

private fun themeLabel(mode: String): String = when (mode) {
    "light" -> "浅色"
    "dark" -> "深色"
    else -> "跟随系统"
}

private fun fontLabel(mode: String): String = if (mode == "system") "系统" else "默认"

private fun openUrl(ctx: Context, url: String) {
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

internal enum class InfoEntry {
    Terms, Usage, Privacy, Licenses, Help,
}

@Composable
private fun SettingsTopBar(onMenuClick: () -> Unit, onInfoClick: () -> Unit) {
    val colors = ClaudeTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp)
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onMenuClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_menu),
                contentDescription = "菜单",
                tint = colors.onSurface,
                modifier = Modifier.size(24.dp),
            )
        }

        Text(
            text = "设置",
            fontFamily = AnthropicSans,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            color = colors.onSurface,
            modifier = Modifier.align(Alignment.Center),
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp)
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onInfoClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_info),
                contentDescription = "信息",
                tint = colors.onSurface,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** 右上角信息菜单：官方量得 190dp 宽（由版本号撑开）、圆角 12dp、行高 48dp。 */
@Composable
internal fun InfoMenu(
    modifier: Modifier = Modifier,
    onEntry: (InfoEntry) -> Unit,
) {
    val colors = ClaudeTheme.colors
    Column(
        modifier = modifier
            .width(IntrinsicSize.Max)
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(12.dp),
                ambientColor = Color.Black.copy(alpha = 0.10f),
                spotColor = Color.Black.copy(alpha = 0.16f),
            )
            .clip(RoundedCornerShape(12.dp))
            .background(colors.card)
            .padding(vertical = 8.dp)
    ) {
        MenuRow(R.drawable.ic_ext_link, "消费者条款") { onEntry(InfoEntry.Terms) }
        MenuRow(R.drawable.ic_ext_link, "使用政策") { onEntry(InfoEntry.Usage) }
        MenuRow(R.drawable.ic_ext_link, "隐私政策") { onEntry(InfoEntry.Privacy) }
        MenuRow(R.drawable.ic_license_doc, "许可证") { onEntry(InfoEntry.Licenses) }
        MenuSeparator()
        MenuRow(R.drawable.ic_ext_link, "帮助与支持") { onEntry(InfoEntry.Help) }
        MenuSeparator()

        // 版本号只保留按压手感：点击不做任何动作，也不关闭菜单。
        val versionInteraction = remember { MutableInteractionSource() }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable(
                    interactionSource = versionInteraction,
                    indication = ripple(color = colors.onSurface.copy(alpha = 0.08f)),
                    onClick = {},
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = OFFICIAL_VERSION,
                fontFamily = AnthropicSans,
                fontSize = 13.sp,
                color = colors.onSurface,
            )
        }
    }
}

@Composable
private fun MenuRow(icon: Int, label: String, onClick: () -> Unit) {
    val colors = ClaudeTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = colors.onSurface.copy(alpha = 0.08f)),
                onClick = onClick,
            )
            .padding(start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = colors.tertiaryText,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            fontFamily = AnthropicSans,
            fontSize = 15.sp,
            color = colors.onSurface,
        )
    }
}

@Composable
private fun MenuSeparator() {
    val colors = ClaudeTheme.colors
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(colors.surfaceVariant)
    )
}

@Composable
private fun AccountRow(email: String, badge: String?, badgeStyle: BadgeStyle) {
    val colors = ClaudeTheme.colors
    val (badgeBg, badgeFg) = badgeColors(badgeStyle, colors.isDark)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(colors.card)
            .height(56.dp)
            .padding(start = 12.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = email,
            fontFamily = AnthropicSans,
            fontSize = 15.sp,
            color = colors.onSurface,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (badge != null) {
            Box(
                modifier = Modifier
                    .height(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(badgeBg)
                    .padding(horizontal = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = badge,
                    fontFamily = AnthropicSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    color = badgeFg,
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = ClaudeTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(colors.card),
        content = content,
    )
}

/** 行间分隔：官方是 2dp 的页面底色，不是细线。 */
@Composable
private fun RowGap() {
    val colors = ClaudeTheme.colors
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(colors.surface)
    )
}

@Composable
private fun SettingsItem(
    icon: Int? = null,
    title: String,
    subtitle: String? = null,
    tint: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = ClaudeTheme.colors
    val fg = tint ?: colors.onSurface
    val rowHeight: Dp = if (subtitle == null) 56.dp else 64.dp
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .then(
                if (onClick != null) {
                    Modifier.clickable {
                        com.nairocy.claude.data.Haptics.tap(ctx)
                        onClick()
                    }
                } else {
                    Modifier
                }
            )
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = AnthropicSans,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                color = fg,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontFamily = AnthropicSans,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    color = if (tint == null) colors.tertiaryText else fg.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        trailing?.invoke()
    }
}

/** 「许可证」：只列本项目实际用到的库。 */
@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    val colors = ClaudeTheme.colors
    val entries = listOf(
        "AndroidX / Jetpack Compose / Material 3" to "Apache License 2.0",
        "Kotlin / kotlinx.coroutines / kotlinx.serialization" to "Apache License 2.0",
        "OkHttp" to "Apache License 2.0",
        "JetBrains Mono" to "SIL Open Font License 1.1",
        "Anthropic Sans / Anthropic Serif" to "随官方素材提供，仅本机自用",
    )
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        titleContentColor = colors.onSurface,
        title = { Text("许可证", fontFamily = AnthropicSans, fontSize = 17.sp) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                entries.forEach { (name, license) ->
                    Text(
                        text = name,
                        fontFamily = AnthropicSans,
                        fontSize = 14.sp,
                        color = colors.onSurface,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        text = license,
                        fontFamily = AnthropicSans,
                        fontSize = 13.sp,
                        color = colors.tertiaryText,
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("关闭", fontFamily = AnthropicSans, fontSize = 15.sp, color = colors.brand)
            }
        },
    )
}
