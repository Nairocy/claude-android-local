package com.nairocy.claude.ui.motion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.nairocy.claude.ui.theme.AnthropicSans
import com.nairocy.claude.ui.theme.AnthropicSerif
import com.nairocy.claude.ui.theme.AnthropicSerifDisplay
import com.nairocy.claude.ui.theme.JetBrainsMono

/**
 * 字体预热：这四套字体是 variable font，第一次真正排版某个字重时才会去解码字体文件，
 * 所以「第一次出现的衬线正文 / 抽屉大标题」会有一帧卡顿（之后正常）。
 *
 * 这里在界面挂载时就把它们各排一次（1dp 尺寸 + 几乎全透明，看不见也不挡点击），
 * 让解码发生在启动那一帧，而不是用户第一次看抽屉 / 第一条回复时。
 */
@Composable
fun FontWarmup() {
    val sample = "ABCDEFGHIJKLMNOPQRSTUVWXYZ abcdefghijklmnopqrstuvwxyz 0123456789 「」，。"
    Box(
        modifier = Modifier
            .size(1.dp)
            .graphicsLayer { alpha = 0.01f }
            // 即使被布局成 1dp 也会走完整的排版（字体在 measure 阶段解析）
            .drawWithContent { drawContent() },
    ) {
        Text(text = sample, fontFamily = AnthropicSerif)
        Text(text = sample, fontFamily = AnthropicSerifDisplay)
        Text(text = sample, fontFamily = AnthropicSans)
        Text(text = sample, fontFamily = JetBrainsMono)
    }
}
