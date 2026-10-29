package com.nairocy.claude.ui.code

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nairocy.claude.R
import com.nairocy.claude.ui.theme.AnthropicSans
import com.nairocy.claude.ui.theme.ClaudeTheme
import com.nairocy.claude.ui.theme.JetBrainsMono

/**
 * Code 页的解锁页（Free 套餐看到的就是这一页，Pro / Max 才进真正的 Code）。
 *
 * 版式对齐官方 07_code 截图：汉堡 → 一段**静态示例会话**（用户气泡 / 助手说明 / 工具卡 + 绿色 diff）
 * → 大标题「升级以使用 Claude Code」+ 两行说明 → 底部「升级」黑胶囊与「返回对话」描边胶囊。
 * 示例会话是画出来的，不是真会话（官方也是 mock，不要在语义上当真）。
 */
@Composable
fun CodeLockedScreen(
    onMenuClick: () -> Unit,
    onUpgrade: () -> Unit,
    onBackToChats: () -> Unit,
) {
    val colors = ClaudeTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .systemBarsPadding()
    ) {
        // 顶栏：只有左上汉堡（官方这一页也没有标题）
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
        }

        // 中段：示例会话 + 升级文案（整块可滚，字号固定，别在小屏上被压扁）
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(28.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.userBubble)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "让 Clawd 跳舞",
                        fontFamily = AnthropicSans,
                        fontSize = 16.sp,
                        color = colors.onSurface,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = "我来给 Clawd 添加一个跳舞动画！先找一下角色组件。",
                fontFamily = AnthropicSans,
                fontSize = 16.sp,
                lineHeight = 25.sp,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(12.dp))

            MockToolCard()
            Spacer(Modifier.height(10.dp))
            MockDiffBlock()

            Spacer(Modifier.height(30.dp))
            Text(
                text = "升级以使用 Claude Code",
                fontFamily = AnthropicSans,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "您的智能体编程伙伴，直接在您的代码库中工作。构建功能、修复错误、提出问题。",
                fontFamily = AnthropicSans,
                fontSize = 16.sp,
                lineHeight = 26.sp,
                color = colors.secondaryText,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 底部两个胶囊按钮
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(CircleShape)
                    .background(colors.onSurface)
                    .clickable(onClick = onUpgrade),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "升级",
                    fontFamily = AnthropicSans,
                    fontSize = 17.sp,
                    color = colors.surface,
                )
            }
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(CircleShape)
                    .border(1.dp, colors.line, CircleShape)
                    .clickable(onClick = onBackToChats),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "返回对话",
                    fontFamily = AnthropicSans,
                    fontSize = 17.sp,
                    color = colors.onSurface,
                )
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

/** 示例工具卡：Grep / Read / Edit 三行，行间 1dp 分隔（与真工具卡同一套视觉）。 */
@Composable
private fun MockToolCard() {
    val colors = ClaudeTheme.colors
    val rows = listOf(
        "Grep" to "claw|crab|mascot",
        "Read" to "/src/components/ClawdCharacter.tsx",
        "Edit" to "/src/components/Clawd.css",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .border(1.dp, colors.line, RoundedCornerShape(14.dp)),
    ) {
        rows.forEachIndexed { i, (name, target) ->
            if (i > 0) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = name,
                    fontFamily = AnthropicSans,
                    fontSize = 18.sp,
                    color = colors.brand,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = target,
                    fontFamily = JetBrainsMono,
                    fontSize = 14.sp,
                    color = colors.tertiaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 示例 diff：+1…+5，绿底、左边行号列，最后两行做渐隐（官方截图就是这样收尾的）。 */
@Composable
private fun MockDiffBlock() {
    val colors = ClaudeTheme.colors
    val lines = listOf(
        "@keyframes clawd-dance {",
        "  0%, 100% { transform: rotate(0deg) translateY(0); }",
        "  25% { transform: rotate(-8deg) translateY(-12px); }",
        "  50% { transform: rotate(0deg) translateY(0); }",
        "  75% { transform: rotate(8deg) translateY(-12px); }",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 150.dp)
            .clip(RoundedCornerShape(12.dp))
            .clipToBounds()
            .background(colors.codeDiffAdd),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            lines.forEachIndexed { i, line ->
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        text = "+${i + 1}",
                        fontFamily = JetBrainsMono,
                        fontSize = 12.sp,
                        color = colors.tertiaryText,
                        modifier = Modifier
                            .width(40.dp)
                            .padding(end = 8.dp, top = 3.dp, bottom = 3.dp),
                        textAlign = TextAlign.End,
                    )
                    Text(
                        text = line,
                        fontFamily = JetBrainsMono,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = colors.onSurface.copy(alpha = if (i >= 3) 0.55f else 1f),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.weight(1f).padding(end = 10.dp, top = 2.dp, bottom = 2.dp),
                    )
                }
            }
        }
        // 底部渐隐：暗示代码还在往下写
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(26.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, colors.card.copy(alpha = 0f)),
                    )
                ),
        )
    }
}
