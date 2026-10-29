package com.nairocy.claude.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── 日间（实测自 APK 资源 + 截图采样）──
val SurfaceLight = Color(0xFFF9F9F7)
val CardLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFF3F3F0)
val OnSurfaceLight = Color(0xFF131313)
val SecondaryTextLight = Color(0xFF383835)
val TertiaryTextLight = Color(0xFF7B7974)
/** 时间与专注页的长文副标题：比 tertiary 更浅（截图采样 #898781） */
val MutedTextLight = Color(0xFF898781)
val BrandAccentLight = Color(0xFFC6613F)
val UserBubbleLight = Color(0xFFF0EFEC)
val LineLight = Color(0xFFE7E6E1)
val LineStrongLight = Color(0xFFE7E6E1)
val ErrorLight = Color(0xFFB3261E)
val ErrorContainerLight = Color(0xFFF9DEDC)

// ── 夜间 ──
val SurfaceDark = Color(0xFF151515)
val CardDark = Color(0xFF20201F)
val SurfaceVariantDark = Color(0xFF1A1A19)
val OnSurfaceDark = Color(0xFFF9F9F7)
val SecondaryTextDark = Color(0xFFC3C2B7)
val TertiaryTextDark = Color(0xFF97958D)
val MutedTextDark = Color(0xFFA5A39B)
val BrandAccentDark = Color(0xFFD97757)
val UserBubbleDark = Color(0xFF0D0D0D)
val LineDark = Color(0xFF313130)
val LineStrongDark = Color(0xFF20201F)
val ErrorDark = Color(0xFFF2B8B5)
val ErrorContainerDark = Color(0xFF8C1D18)

// ── 固定色（不随主题变）──
val SparkOrange = Color(0xFFD97757)
val ArtifactIcon = Color(0xFF796DC0)
val ArtifactSurface = Color(0xFFEFEDFF)

// ── 订阅徽标（设置页账号行；尺寸固定 18dp 胶囊，只换配色）──
// Free：浅灰底 + 中灰字（弱化）；深色下底稍亮、字稍暗，保证对比度不刺眼
val BadgeFreeBgLight = Color(0xFFE7E6E1)
val BadgeFreeFgLight = Color(0xFF6B6A66)
val BadgeFreeBgDark = Color(0xFF3A3A38)
val BadgeFreeFgDark = Color(0xFFC7C6C1)
// Pro：品牌橙 + 白字，日夜同色
val BadgeProBg = Color(0xFFD97757)
val BadgeProFg = Color(0xFFFFFFFF)
// Max：浅色下黑底白字，深色下反相（米白底黑字）
val BadgeMaxBgLight = Color(0xFF131313)
val BadgeMaxFgLight = Color(0xFFFFFFFF)
val BadgeMaxBgDark = Color(0xFFF0EEE6)
val BadgeMaxFgDark = Color(0xFF131313)

// ── Artifact 卡片夜间 ──
val ArtifactSurfaceDark = Color(0xFF23203A)
val ArtifactIconDark = Color(0xFF9A8EE8)

// ── Code 页工具卡的 diff 底色（日 / 夜）──
// 取自 clawd-vibe 参考稿（--add / --del），深色下压暗、保持低饱和，不抢正文
val CodeDiffAddLight = Color(0xFFE9F3DF)
val CodeDiffDelLight = Color(0xFFFBE9E7)
val CodeDiffAddDark = Color(0xFF26361F)
val CodeDiffDelDark = Color(0xFF3B2321)


@Immutable
data class ClaudeColors(
    val surface: Color,
    val card: Color,
    val surfaceVariant: Color,
    val onSurface: Color,
    val secondaryText: Color,
    val tertiaryText: Color,
    val mutedText: Color,
    val brand: Color,
    val userBubble: Color,
    val line: Color,
    val lineStrong: Color,
    val error: Color,
    val errorContainer: Color,
    val artifactSurface: Color,
    val artifactIcon: Color,
    /** Code 页工具卡 diff 底色（+ 行 / - 行） */
    val codeDiffAdd: Color,
    val codeDiffDel: Color,
    val isDark: Boolean,
)

val LightColors = ClaudeColors(
    surface = SurfaceLight, card = CardLight, surfaceVariant = SurfaceVariantLight,
    onSurface = OnSurfaceLight, secondaryText = SecondaryTextLight, tertiaryText = TertiaryTextLight,
    mutedText = MutedTextLight,
    brand = BrandAccentLight, userBubble = UserBubbleLight, line = LineLight, lineStrong = LineStrongLight,
    error = ErrorLight, errorContainer = ErrorContainerLight,
    artifactSurface = ArtifactSurface, artifactIcon = ArtifactIcon,
    codeDiffAdd = CodeDiffAddLight, codeDiffDel = CodeDiffDelLight, isDark = false,
)

val DarkColors = ClaudeColors(
    surface = SurfaceDark, card = CardDark, surfaceVariant = SurfaceVariantDark,
    onSurface = OnSurfaceDark, secondaryText = SecondaryTextDark, tertiaryText = TertiaryTextDark,
    mutedText = MutedTextDark,
    brand = BrandAccentDark, userBubble = UserBubbleDark, line = LineDark, lineStrong = LineStrongDark,
    error = ErrorDark, errorContainer = ErrorContainerDark,
    artifactSurface = ArtifactSurfaceDark, artifactIcon = ArtifactIconDark,
    codeDiffAdd = CodeDiffAddDark, codeDiffDel = CodeDiffDelDark, isDark = true,
)

val LocalClaudeColors = staticCompositionLocalOf { LightColors }
