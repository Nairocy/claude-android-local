package com.nairocy.claude.ui.artifacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nairocy.claude.R
import com.nairocy.claude.data.ArtifactItem
import com.nairocy.claude.ui.theme.AnthropicSans
import com.nairocy.claude.ui.theme.ClaudeTheme
import com.nairocy.claude.ui.theme.JetBrainsMono

/** Artifact 详情：整段代码 + 运行 + 复制。 */
@Composable
fun ArtifactDetailScreen(
    item: ArtifactItem,
    onBack: () -> Unit,
    onMenuClick: () -> Unit = {},
    /** 交给 WebView 渲染（html / svg 才有，null 或不可预览时不出按钮）。 */
    onRun: (() -> Unit)? = null,
) {
    val colors = ClaudeTheme.colors
    val clipboard = LocalClipboardManager.current
    val canRun = onRun != null && com.nairocy.claude.data.Artifacts.canPreview(item.lang, item.code)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .systemBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(44.dp)
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
                text = item.title,
                fontFamily = AnthropicSans,
                fontSize = 17.sp,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 60.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canRun) {
                com.nairocy.claude.ui.components.RunGlyphButton(onClick = onRun!!, size = 28.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = item.lang.ifBlank { "code" },
                fontFamily = JetBrainsMono,
                fontSize = 12.sp,
                color = colors.tertiaryText,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "来自「${item.convTitle}」",
                fontFamily = AnthropicSans,
                fontSize = 12.sp,
                color = colors.tertiaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            com.nairocy.claude.ui.components.CopyGlyphButton(
                onClick = { clipboard.setText(AnnotatedString(item.code)) },
                tint = colors.brand,
            )
        }

        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.card),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
            ) {
                Text(
                    text = item.code,
                    fontFamily = JetBrainsMono,
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    color = colors.onSurface,
                    softWrap = false,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                )
            }
        }
    }
}
