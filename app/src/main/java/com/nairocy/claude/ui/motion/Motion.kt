package com.nairocy.claude.ui.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 全局动效语言（Claude 那种「安静、快、不弹跳」的手感）。
 *
 * 规律：
 *  - 进入用强减速曲线（[Decel]）：起步快、尾巴长，像内容「落定」；
 *  - 退出用加速曲线（[Accel]）：不等，直接走；
 *  - 位移都很小（页宽 1/5 ~ 1/4），靠淡入淡出承担主要观感；
 *  - 只有「按下去」允许轻微过冲（弹簧），其余不用弹簧，避免廉价感；
 *  - 时长上限 300ms，任何过渡都不许让用户等。
 */
object Motion {
    /** 页面/内容进入时长。 */
    const val ENTER_MS = 300

    /** 页面/内容退出时长（比进入短，退场不拖）。 */
    const val EXIT_MS = 220

    /** 列表项、卡片、展开收起这类小结构。 */
    const val SMALL_MS = 220

    /** 强减速：进入。 */
    val Decel = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** 加速：退出。 */
    val Accel = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    /** 通用标准曲线（淡入淡出、颜色过渡）。 */
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** 按压回弹：只有这里允许一点点过冲。 */
    fun <T> pressSpring() = spring<T>(dampingRatio = 0.45f, stiffness = 1400f)
}

/**
 * 按下时轻微缩小（0.94×），松开弹回。配合 `clickable(interactionSource = …)` 使用：
 * 传进去的 source 必须是那个 clickable 自己用的那份，否则拿不到按压状态。
 */
@Composable
fun Modifier.pressScale(source: InteractionSource, pressed: Float = 0.94f): Modifier {
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressed else 1f,
        animationSpec = Motion.pressSpring(),
        label = "pressScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
