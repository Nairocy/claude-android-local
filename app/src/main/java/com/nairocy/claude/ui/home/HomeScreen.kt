package com.nairocy.claude.ui.home

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nairocy.claude.R
import com.nairocy.claude.ui.components.AttachSheet
import com.nairocy.claude.ui.components.Composer
import com.nairocy.claude.ui.theme.AnthropicSans
import com.nairocy.claude.ui.theme.AnthropicSerif
import com.nairocy.claude.ui.theme.ClaudeTheme

/** 首页空态：星芒 + 问候 + 输入条。对应截图 1。 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onMenuClick: () -> Unit,
    onGhostClick: () -> Unit = {},
    onMicClick: () -> Unit = {},
    onSend: (String) -> Unit = {},
    onPickImage: () -> Unit = {},
    onCamera: () -> Unit = {},
    onPickFile: () -> Unit = {},
    onStartIncognito: () -> Unit = {},
    modelTierId: String = "sonnet",
    onModelTierChange: (String) -> Unit = {},
    /** 当前套餐：模型面板据此给 fable / opus 上锁。 */
    plan: com.nairocy.claude.data.Plan = com.nairocy.claude.data.Plan.FREE,
    /** 点「查看方案」→ 账单页。 */
    onSeePlans: () -> Unit = {},
    webSearch: Boolean = false,
    onWebSearchChange: (Boolean) -> Unit = {},
    memory: Boolean = false,
    onMemoryChange: (Boolean) -> Unit = {},
    /** 首页问候语（时段档 + 随机欢迎语），由 MainActivity 按当前时间算好传进来 */
    greeting: String = "嗨，你",
) {
    val colors = ClaudeTheme.colors
    val keyboardVisible = WindowInsets.ime
        .getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    var input by remember { mutableStateOf("") }
    var attachOpen by remember { mutableStateOf(false) }
    var modelSheet by remember { mutableStateOf(false) }
    val tier = com.nairocy.claude.ui.components.modelTierById(
        com.nairocy.claude.ui.components.effectiveTierId(modelTierId, plan)
    )
    val ctxHome = androidx.compose.ui.platform.LocalContext.current
    // 打开附件面板/进入无痕时收回焦点
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    androidx.compose.runtime.LaunchedEffect(attachOpen) {
        if (attachOpen) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .systemBarsPadding()
            .imePadding()
    ) {
        // 顶栏：左汉堡，右侧留出品牌小图标位
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTap(onClick = onMenuClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_menu),
                    contentDescription = "菜单",
                    tint = colors.onSurface,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            IconTap(onClick = onStartIncognito) {
                Icon(
                    painter = painterResource(R.drawable.ic_ghost),
                    contentDescription = null,
                    tint = colors.onSurface,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        // 中部：星芒 + 问候
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-3).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 首页星芒是静态的（用户要求：只有登录页那枚才做动画）
            Image(
                painter = painterResource(R.drawable.claude_spark_icon),
                contentDescription = null,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.height(21.dp))
            androidx.compose.material3.Text(
                text = greeting,
                fontFamily = AnthropicSerif,
                fontSize = 22.sp,
                color = colors.onSurface,
            )
        }

        // 底部：输入条
        Composer(
            value = input,
            onValueChange = { input = it },
            onSend = {
                if (input.isNotBlank()) {
                    onSend(input.trim())
                    input = ""
                    focusManager.clearFocus(force = true)
                    keyboard?.hide()
                }
            },
            onMicClick = onMicClick,
            keyboardVisible = keyboardVisible,
            modelLabel = com.nairocy.claude.ui.components.localModelLabel(),
            onModelClick = {
                focusManager.clearFocus(force = true)
                keyboard?.hide()
                modelSheet = true
            },
            onPickImage = { attachOpen = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 18.dp)
                .padding(bottom = 18.dp),
        )

        com.nairocy.claude.ui.components.ModelSheet(
            visible = modelSheet,
            selectedId = com.nairocy.claude.ui.components.effectiveTierId(modelTierId, plan),
            currentPlan = plan,
            onSeePlans = {
                modelSheet = false
                onSeePlans()
            },
            onSelect = {
                onModelTierChange(it.id)
                modelSheet = false
            },
            onDismiss = { modelSheet = false },
            onLearnMore = {
                runCatching {
                    ctxHome.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(com.nairocy.claude.ui.components.FABLE_INFO_URL)
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            },
        )

        AttachSheet(
            visible = attachOpen,
            showProjectRow = false,
            webSearch = webSearch,
            onWebSearchChange = onWebSearchChange,
            memory = memory,
            onMemoryChange = onMemoryChange,
            onDismiss = { attachOpen = false },
            onCamera = {
                attachOpen = false
                onCamera()
            },
            onPhoto = {
                attachOpen = false
                onPickImage()
            },
            onFile = {
                attachOpen = false
                onPickFile()
            },
        )
    }
}

@Composable
private fun IconTap(onClick: () -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

