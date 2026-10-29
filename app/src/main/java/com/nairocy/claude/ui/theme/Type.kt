package com.nairocy.claude.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nairocy.claude.R

/**
 * Anthropic 给的是 **variable font**：wght 300–800、opsz 16–48，**默认实例是 Light 300**。
 * 必须用 FontVariation 显式指定轴值，否则所有字重都会渲染成 300，再由系统合成假粗体
 * （之前正文发虚、加粗发糊就是这个原因）。
 */
private fun anth(
    resId: Int,
    weight: Int,
    opsz: Float,
    style: FontStyle = FontStyle.Normal,
): Font = Font(
    resId = resId,
    weight = FontWeight(weight),
    style = style,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", opsz),
    ),
)

/**
 * 字体样式 →「系统」时切成 FontFamily.Default（设置页弹窗控制）。
 * 做成 CompositionLocal 是为了让 200+ 个 `fontFamily = AnthropicSans` 调用点不用改。
 */
val LocalSystemFont = staticCompositionLocalOf { false }

/** UI / 正文：Text 光学尺寸（opsz 16）。 */
private val SansFamily = FontFamily(
    anth(R.font.anthropic_sans, 400, 16f),
    anth(R.font.anthropic_sans, 500, 16f),
    anth(R.font.anthropic_sans, 600, 16f),
    anth(R.font.anthropic_sans, 700, 16f),
    anth(R.font.anthropic_sans_italic, 400, 16f, FontStyle.Italic),
)

/** 正文衬线：问候语、区块标题。 */
private val SerifFamily = FontFamily(
    anth(R.font.anthropic_serif, 400, 16f),
    anth(R.font.anthropic_serif, 500, 16f),
    anth(R.font.anthropic_serif, 600, 16f),
    anth(R.font.anthropic_serif_italic, 400, 16f, FontStyle.Italic),
)

/** 大标题：Display 光学尺寸（opsz 32），字距更紧、笔画更精神。 */
private val SerifDisplayFamily = FontFamily(
    anth(R.font.anthropic_serif, 400, 32f),
    anth(R.font.anthropic_serif, 500, 32f),
    anth(R.font.anthropic_serif, 600, 32f),
)

val AnthropicSans: FontFamily
    @Composable @ReadOnlyComposable
    get() = if (LocalSystemFont.current) FontFamily.Default else SansFamily

val AnthropicSerif: FontFamily
    @Composable @ReadOnlyComposable
    get() = if (LocalSystemFont.current) FontFamily.Default else SerifFamily

val AnthropicSerifDisplay: FontFamily
    @Composable @ReadOnlyComposable
    get() = if (LocalSystemFont.current) FontFamily.Default else SerifDisplayFamily

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
)

val ClaudeTypography: Typography
    @Composable @ReadOnlyComposable
    get() = Typography(
    displayLarge = TextStyle(fontFamily = AnthropicSerifDisplay, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 46.sp),
    headlineLarge = TextStyle(fontFamily = AnthropicSerifDisplay, fontWeight = FontWeight.Normal, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = AnthropicSerifDisplay, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = AnthropicSans, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = AnthropicSans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = AnthropicSans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = AnthropicSans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = AnthropicSans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = AnthropicSans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = AnthropicSans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
)
