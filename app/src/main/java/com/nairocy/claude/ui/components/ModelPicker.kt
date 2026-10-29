package com.nairocy.claude.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nairocy.claude.R
import com.nairocy.claude.data.Plan
import com.nairocy.claude.ui.theme.AnthropicSans
import com.nairocy.claude.ui.theme.ClaudeTheme

/**
 * 模型档位。界面照官方「选择模型」面板，档位映射到 DeepSeek 的 reasoning_effort：
 * Fable 5.1 = max，Opus 5.5 = xhigh，Sonnet 5.5 = high，Haiku 5.5 = low。
 *
 * [minPlan] 是这一档要求的最低套餐（只比 rank）：Free 只能选 sonnet / haiku，
 * fable / opus 在面板里显示锁，点了引导去账单页 —— 行本身仍可点，不做成灰死。
 */
data class ModelTier(
    val id: String,
    val name: String,
    val levelLabel: String,
    val effort: String,
    val badge: String?,
    val blurb: String,
    val minPlan: Plan = Plan.FREE,
)

val MODEL_TIERS = listOf(
    ModelTier("fable", "Fable 5.1", "最高", "max", "Pro 或 Max", "应对您最艰巨的挑战", Plan.PRO),
    ModelTier("opus", "Opus 5.5", "极高", "xhigh", "Pro", "适用于复杂工作与日常任务", Plan.PRO),
    ModelTier("sonnet", "Sonnet 5.5", "高", "high", null, "处理简单任务效率最高", Plan.FREE),
    ModelTier("haiku", "Haiku 5.5", "低", "low", null, "快速解答的最佳选择", Plan.FREE),
)

fun modelTierById(id: String): ModelTier = MODEL_TIERS.firstOrNull { it.id == id } ?: MODEL_TIERS[2]

/**
 * 当前套餐**真能发出去**的档位。
 *
 * 偏好里可能存着买不起的档（Pro 到期降回 Free、或换了账号），这时界面必须显示实际会用的那个 ——
 * 不能输入条写着「Opus 5.5」却偷偷发 Sonnet。**不改写 prefs**：升级回 Pro 时用户原来的选择还在。
 */
fun effectiveTierId(currentId: String, plan: Plan): String {
    val tier = modelTierById(currentId)
    return if (tier.minPlan.rank <= plan.rank) tier.id else "sonnet"
}

const val FABLE_INFO_URL = "https://support.claude.com/en/articles/15424964-claude-fable-models-on-your-plan"

/** 「选择模型」底部面板（只有四个模型 + 说明，不做思考强度/更多模型）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSheet(
    visible: Boolean,
    selectedId: String,
    onSelect: (ModelTier) -> Unit,
    onDismiss: () -> Unit,
    onLearnMore: () -> Unit,
    currentPlan: Plan = Plan.FREE,
    onSeePlans: () -> Unit = {},
) {
    if (!visible) return
    val colors = ClaudeTheme.colors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        scrimColor = Color.Black.copy(alpha = 0.32f),
        dragHandle = null,
    ) {
        ModelSheetContent(
            selectedId = selectedId,
            onSelect = onSelect,
            onDismiss = onDismiss,
            onLearnMore = onLearnMore,
            currentPlan = currentPlan,
            onSeePlans = onSeePlans,
        )
    }
}

/** 输入条上的模型胶囊文案：本地版直接显示配置里的模型名。 */
@Composable
fun localModelLabel(): String {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return remember { com.nairocy.claude.data.SettingsStore(ctx).load().model.trim().ifBlank { "模型" } }
}

/**
 * 面板本体（本地版）：只展示当前接口与模型，并给一个进入 API 设置的入口。
 *
 * 参数签名保留原样，三个调用点（对话 / 首页 / Code）不用改；[selectedId] / [onSelect] /
 * [onLearnMore] / [currentPlan] 在本地版不再使用。
 */
@Composable
fun ModelSheetContent(
    selectedId: String,
    onSelect: (ModelTier) -> Unit,
    onDismiss: () -> Unit,
    onLearnMore: () -> Unit,
    currentPlan: Plan = Plan.FREE,
    onSeePlans: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = ClaudeTheme.colors
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val cfg = remember { com.nairocy.claude.data.SettingsStore(ctx).load() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 26.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(42.dp)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = "关闭",
                    tint = colors.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = "模型",
                fontFamily = AnthropicSans,
                fontSize = 17.sp,
                color = colors.onSurface,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(1.dp)
                .background(colors.line)
        )
        Spacer(Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (colors.isDark) colors.surfaceVariant else colors.surface),
        ) {
            LocalInfoRow("模型", cfg.model.ifBlank { "未设置" })
            LocalDivider()
            LocalInfoRow("接口", cfg.baseUrl.ifBlank { "未设置" })
            LocalDivider()
            LocalInfoRow("API Key", if (cfg.apiKey.isBlank()) "未设置" else "已设置")
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "本地版直连你填写的接口，模型和 Key 只存在这台设备上。",
            fontFamily = AnthropicSans,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = colors.tertiaryText,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(if (colors.isDark) Color(0xFFF0EEE6) else Color(0xFF131313))
                .clickable(onClick = onSeePlans),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "修改 API 设置",
                fontFamily = AnthropicSans,
                fontSize = 15.sp,
                color = if (colors.isDark) Color(0xFF131313) else Color.White,
            )
        }
    }
}

@Composable
private fun LocalInfoRow(label: String, value: String) {
    val colors = ClaudeTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = AnthropicSans,
            fontSize = 15.sp,
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            fontFamily = AnthropicSans,
            fontSize = 13.sp,
            color = colors.tertiaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.4f, fill = false),
        )
    }
}

@Composable
private fun LocalDivider() {
    val colors = ClaudeTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(1.dp)
            .background(colors.line)
    )
}

@Composable
private fun ModelRow(tier: ModelTier, selected: Boolean, locked: Boolean, onClick: () -> Unit) {
    val colors = ClaudeTheme.colors
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                com.nairocy.claude.data.Haptics.tap(ctx)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tier.name,
                    fontFamily = AnthropicSans,
                    fontSize = 16.sp,
                    color = colors.onSurface,
                )
                tier.badge?.let { badge ->
                    Spacer(Modifier.size(8.dp))
                    // 锁住的档位胶囊转灰，但文字保留（「Pro 或 Max」本身就是卖点信息）
                    Text(
                        text = badge,
                        fontFamily = AnthropicSans,
                        fontSize = 10.sp,
                        color = if (locked) colors.tertiaryText else Color(0xFF2A78D6),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (locked) colors.tertiaryText.copy(alpha = 0.12f)
                                else Color(0x142A78D6)
                            )
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = tier.blurb,
                fontFamily = AnthropicSans,
                fontSize = 13.sp,
                color = colors.tertiaryText,
            )
        }
        if (locked) {
            Icon(
                painter = painterResource(R.drawable.ic_lock),
                contentDescription = "需要 Pro 或 Max 套餐",
                tint = colors.tertiaryText,
                modifier = Modifier.size(16.dp),
            )
        } else if (selected) {
            Image(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = "已选择",
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(Color(0xFF2A78D6)),
            )
        }
    }
}

/**
 * 「Pro 专属模型」引导框。卡片版式照 `ui/settings/ChoiceDialog`（298dp、圆角 15dp、页面底色），
 * 底部两个胶囊按钮 —— 取消（描边）/ 查看方案（品牌色填充）。
 */
@Composable
private fun LockedModelDialog(onSeePlans: () -> Unit, onDismiss: () -> Unit) {
    val colors = ClaudeTheme.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .width(298.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(if (colors.isDark) colors.card else colors.surface)
                .padding(20.dp)
        ) {
            Text(
                text = "Pro 专属模型",
                fontFamily = AnthropicSans,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(9.dp))
            Text(
                text = "Fable 5.1 与 Opus 5.5 需要 Pro 或 Max 套餐。",
                fontFamily = AnthropicSans,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = colors.mutedText,
            )
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DialogPill(
                    label = "取消",
                    filled = false,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                DialogPill(
                    label = "查看方案",
                    filled = true,
                    onClick = onSeePlans,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DialogPill(
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ClaudeTheme.colors
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(if (filled) colors.brand else Color.Transparent)
            .then(if (filled) Modifier else Modifier.border(1.dp, colors.line, CircleShape))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = AnthropicSans,
            fontSize = 15.sp,
            color = if (filled) Color.White else colors.onSurface,
        )
    }
}
