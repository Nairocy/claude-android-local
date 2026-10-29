package com.nairocy.claude.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nairocy.claude.R
import com.nairocy.claude.ui.theme.MutedTextDark
import com.nairocy.claude.ui.theme.SurfaceDark
import com.nairocy.claude.ui.theme.SurfaceLight
import kotlinx.coroutines.delay

/** 品牌启动页底色：日间米白；夜间与 SurfaceDark 一致（官方只给了浅色版，深色按项目色板推）。 */
private val SplashSurfaceLight = SurfaceLight
private val SplashSurfaceDark = SurfaceDark

/** 底部 ANTHROP\C 品牌行的灰；深色底上提亮一档保证对比度。 */
private val SplashBrandingLight = Color(0xFF8A8A88)
private val SplashBrandingDark = MutedTextDark

/**
 * 开屏加载页（对齐官方启动截图）：
 * 正中 星芒 + Claude 字标，底部 ANTHROP\C 品牌行。
 * 素材：logo_claude_splash.xml / branding_claude_splash.xml（官方矢量）。
 */
@Composable
fun ClaudeSplash(dark: Boolean = false, onFinish: () -> Unit) {
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        delay(700)
        alpha.animateTo(0f, tween(durationMillis = 260, easing = LinearEasing))
        onFinish()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (dark) SplashSurfaceDark else SplashSurfaceLight)
            .alpha(alpha.value)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Image(
            painter = painterResource(
                if (dark) R.drawable.logo_claude_splash_dark else R.drawable.logo_claude_splash
            ),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .width(266.dp)
                .height(266.dp),
        )
        Image(
            painter = painterResource(R.drawable.branding_claude_splash),
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (dark) SplashBrandingDark else SplashBrandingLight),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 62.dp)
                .width(200.dp)
                .height(80.dp),
        )
    }
}

/** 首帧盖一层启动页，底下的 App 照常初始化；淡出后交还交互。 */
@Composable
fun SplashGate(dark: Boolean = false, content: @Composable () -> Unit) {
    var done by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        content()
        if (!done) ClaudeSplash(dark = dark, onFinish = { done = true })
    }
}
