package com.nairocy.claude.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.nairocy.claude.R
import com.nairocy.claude.data.Prefs
import com.nairocy.claude.ui.components.BackTopBar
import com.nairocy.claude.ui.components.CardSeparator
import com.nairocy.claude.ui.components.CheckRow
import com.nairocy.claude.ui.components.ChevronRow
import com.nairocy.claude.ui.components.GroupCard
import com.nairocy.claude.ui.components.SectionHeader
import com.nairocy.claude.ui.components.ToggleRow
import com.nairocy.claude.ui.theme.ClaudeTheme

/**
 * 功能页（二级页，按官方截图做）。
 *
 * 版式：卡片左右 12dp、圆角 15dp、行间 2dp 底色分隔；有图标的行文字 x=46dp，
 * 记忆组没有图标、文字 x=12dp；工具访问权限选中态是蓝色文字 + 蓝勾（#284E90）。
 *
 * 只有「网页搜索 / 记忆」两行有真实效果（Prefs），其余开关先落盘占位。
 */
@Composable
fun FeaturesScreen(
    onBack: () -> Unit,
    onOpenMemoryFiles: () -> Unit,
) {
    val colors = ClaudeTheme.colors
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }

    var webSearch by remember { mutableStateOf(prefs.webSearch) }
    var artifacts by remember { mutableStateOf(prefs.artifacts) }
    var inChatViz by remember { mutableStateOf(prefs.inChatViz) }
    var codeExec by remember { mutableStateOf(prefs.codeExec) }
    var modelFallback by remember { mutableStateOf(prefs.modelFallback) }
    var memory by remember { mutableStateOf(prefs.memory) }
    var memoryAutogen by remember { mutableStateOf(prefs.memoryAutogen) }
    var memorySensitive by remember { mutableStateOf(prefs.memorySensitive) }
    var toolAccess by remember { mutableStateOf(prefs.toolAccess) }
    var fastOutput by remember { mutableStateOf(prefs.fastOutput) }

    val linkStyle = TextLinkStyles(
        style = SpanStyle(color = colors.brand, textDecoration = TextDecoration.Underline)
    )
    val memoryLearnMore = buildAnnotatedString {
        append("允许 Claude 根据您的对话生成记忆。")
        withLink(LinkAnnotation.Url("https://support.claude.com/", linkStyle)) { append("了解更多。") }
    }
    val sensitiveLearnMore = buildAnnotatedString {
        append("允许 Claude 将健康状况或宗教信仰等敏感话题的细节保存到记忆中。")
        withLink(LinkAnnotation.Url("https://support.claude.com/", linkStyle)) { append("了解更多。") }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .systemBarsPadding()
    ) {
        BackTopBar(title = "功能", onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 19.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                GroupCard {
                    ToggleRow(
                        title = "Artifacts",
                        subtitlePlain = "代码执行功能需要此项",
                        icon = R.drawable.cds_icon_artifacts,
                        checked = if (codeExec) true else artifacts,
                        enabled = !codeExec,
                        onCheckedChange = { artifacts = it; prefs.artifacts = it },
                    )
                    CardSeparator()
                    ToggleRow(
                        title = "对话内可视化",
                        badge = "测试版",
                        subtitlePlain = "允许 Claude 直接在对话中生成交互式可视化内容、图表和示意图。",
                        icon = R.drawable.cds_icon_chart,
                        checked = inChatViz,
                        onCheckedChange = { inChatViz = it; prefs.inChatViz = it },
                    )
                    CardSeparator()
                    ToggleRow(
                        title = "代码执行与文件创建",
                        subtitlePlain = "允许 Claude 执行代码，以及创建和编辑文档、电子表格、演示文稿、PDF 和数据报告。",
                        icon = R.drawable.cds_icon_file,
                        checked = codeExec,
                        onCheckedChange = { codeExec = it; prefs.codeExec = it },
                    )
                    CardSeparator()
                    ToggleRow(
                        title = "消息被标记时切换模型",
                        subtitlePlain = "当安全措施标记某条消息时，自动切换到其他模型以继续对话。关闭后，您的对话将改为暂停。",
                        icon = R.drawable.cds_icon_swap_vertical,
                        checked = modelFallback,
                        onCheckedChange = { modelFallback = it; prefs.modelFallback = it },
                    )
                    CardSeparator()
                    ToggleRow(
                        title = "快速输出",
                        subtitlePlain = "开启后，模型吐字比动画节奏快时会自动提速追平（最多落后约 1.5 秒）；" +
                            "关闭时严格按固定速率逐字浮出。代码块始终不受影响。",
                        icon = R.drawable.ic_fast_output,
                        checked = fastOutput,
                        onCheckedChange = { fastOutput = it; prefs.fastOutput = it },
                    )
                }
            }

            item {
                Column {
                    SectionHeader("记忆")
                    GroupCard {
                        ToggleRow(
                            title = "从对话生成记忆",
                            subtitle = memoryLearnMore,
                            checked = memoryAutogen,
                            onCheckedChange = { memoryAutogen = it; prefs.memoryAutogen = it },
                        )
                        CardSeparator()
                        ToggleRow(
                            title = "在记忆中包含敏感话题",
                            subtitle = sensitiveLearnMore,
                            checked = memorySensitive,
                            onCheckedChange = { memorySensitive = it; prefs.memorySensitive = it },
                        )
                        CardSeparator()
                        ChevronRow(
                            title = "记忆文件",
                            subtitle = "查看和管理 Claude 记住的内容。",
                            onClick = onOpenMemoryFiles,
                        )
                    }
                }
            }

            item {
                Column {
                    SectionHeader("工具访问权限")
                    GroupCard {
                        CheckRow(
                            title = "自动",
                            subtitle = "由 Claude 为您选择",
                            selected = toolAccess == "auto",
                            onClick = { toolAccess = "auto"; prefs.toolAccess = "auto" },
                        )
                        CardSeparator()
                        CheckRow(
                            title = "按需加载",
                            subtitle = "按需加载。消息数量更多，准确性较低",
                            selected = toolAccess == "onDemand",
                            onClick = { toolAccess = "onDemand"; prefs.toolAccess = "onDemand" },
                        )
                        CardSeparator()
                        CheckRow(
                            title = "始终可用",
                            subtitle = "从一开始即已就绪。消息数量较少，准确性更高",
                            selected = toolAccess == "always",
                            onClick = { toolAccess = "always"; prefs.toolAccess = "always" },
                        )
                    }
                }
            }
        }
    }
}
