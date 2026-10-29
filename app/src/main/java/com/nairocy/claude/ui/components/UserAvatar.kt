package com.nairocy.claude.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nairocy.claude.ui.theme.AnthropicSans
import com.nairocy.claude.ui.theme.SparkOrange
import java.io.File

/**
 * 用户头像：有自定义图就圆形裁切显示，没有就是**砖红底 + 首字母**（官方同款底色 #D97757）。
 * 首字母由 [com.nairocy.claude.data.Account.initial] 算（称呼 → 全名 → 邮箱）。
 */
@Composable
fun UserAvatar(
    size: Dp,
    initial: String,
    file: File?,
    modifier: Modifier = Modifier,
) {
    val bitmap: Bitmap? = remember(file?.path, file?.lastModified(), file?.length()) {
        file?.takeIf { it.exists() && it.length() > 0 }?.let { f ->
            runCatching { android.graphics.BitmapFactory.decodeFile(f.path) }.getOrNull()
        }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(SparkOrange),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val font = (size.value * 0.42f).sp
            Text(
                text = initial,
                fontFamily = AnthropicSans,
                fontWeight = FontWeight.Medium,
                fontSize = font,
                lineHeight = font,
                color = Color.White,
            )
        }
    }
}
