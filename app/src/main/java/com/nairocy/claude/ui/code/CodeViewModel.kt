package com.nairocy.claude.ui.code

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nairocy.claude.data.AccountStore
import com.nairocy.claude.data.CodeConversation
import com.nairocy.claude.data.CodeConversationStore
import com.nairocy.claude.data.CodeMessage
import com.nairocy.claude.data.CodePart
import com.nairocy.claude.data.CodeToolRun
import com.nairocy.claude.data.CodeWorkspace
import com.nairocy.claude.data.CodeWorkspaceMeta
import com.nairocy.claude.data.CodeWorkspaceStore
import com.nairocy.claude.data.CodeForegroundService
import com.nairocy.claude.data.CodeNotifications
import com.nairocy.claude.data.Prefs
import com.nairocy.claude.data.Role
import com.nairocy.claude.data.WorkspaceFile
import com.nairocy.claude.net.OpenAiClient
import com.nairocy.claude.net.StreamEvent
import com.nairocy.claude.net.ToolSpec
import com.nairocy.claude.net.WireMessage
import com.nairocy.claude.ui.components.effectiveTierId
import com.nairocy.claude.ui.components.modelTierById
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Code 页的会话内核。
 *
 * 与对话页（[com.nairocy.claude.ui.chat.ChatViewModel]）共用同一套「流式 + 周期落盘 + 工具循环」骨架，
 * 但**独立会话与消息流**（`code_conversations.json`），并且：
 * - 不做通知 / 图片 / 附件 / 无痕；
 * - 助手消息按**顺序**存块（text / tool），工具卡带 diff；
 * - 工具循环上限 12 轮（一轮里会连着 ls→read→edit→grep）。
 */
class CodeViewModel(app: Application) : AndroidViewModel(app) {

    private val convStore = CodeConversationStore(app)
    private val wsStore = CodeWorkspaceStore(app)
    private val accountStore = AccountStore(app)
    private val client = OpenAiClient()
    private val prefs = Prefs(app)

    var messages by mutableStateOf<List<CodeMessage>>(emptyList())
        private set
    var streaming by mutableStateOf(false)
        private set

    /**
     * 工具执行中的提示文案（"读取 /index.html…" / "搜索以太网..."），由思考行显示；
     * 不在工具执行期间为 null（此时显示随机思考文案）。
     */
    var toolHint by mutableStateOf<String?>(null)
        private set

    var input by mutableStateOf("")
    var title by mutableStateOf("新对话")
        private set

    var history by mutableStateOf<List<CodeConversation>>(emptyList())
        private set
    var files by mutableStateOf<List<WorkspaceFile>>(emptyList())
        private set

    /** 网页搜索开关（与对话页共用 prefs.webSearch）。 */
    var webSearch by mutableStateOf(prefs.webSearch)
        private set

    /** 模型档位（与对话页共用 prefs.modelTier）。 */
    var modelTier by mutableStateOf(prefs.modelTier)
        private set

    /** 全部工作区（相互独立，目录各一份）。 */
    var workspaces by mutableStateOf<List<CodeWorkspaceMeta>>(emptyList())
        private set

    /** 当前工作区 id。 */
    var workspaceId by mutableStateOf("")
        private set

    /** 当前工作区名（界面标题/导出用）。 */
    val workspaceName: String
        get() = workspaces.firstOrNull { it.id == workspaceId }?.name ?: "工作区"

    /** 当前工作区的真实目录。 */
    private fun wsRoot(): java.io.File = CodeWorkspaceStore.dirOf(getApplication(), workspaceId)

    private var convId: String = UUID.randomUUID().toString()
    private var createdAt: Long = System.currentTimeMillis()
    private var job: Job? = null

    /**
     * 代际号：每次「换会话 / 开新一轮」都 +1。
     * 被取消的旧流在收尾时可能还会跑一小段（catch Throwable 分支、落盘），
     * 靠它把这些迟到的写入挡掉，避免内容串到新会话或覆盖新状态。
     */
    private var gen: Int = 0

    /** 当前会话 id。 */
    val currentId: String get() = convId

    /* ── 本轮流式的「活」状态（stop / 落盘 / 收尾都要用） ── */

    /** 这一轮回复的块（顺序：text / tool / text …）。 */
    private val parts = mutableListOf<CodePart>()
    private var base: List<CodeMessage> = emptyList()
    private var replyTs: Long = 0L
    /** 当前末尾是不是一个「还没定稿」的 text 块。 */
    private var textOpen: Boolean = false
    /** 当前 text 块已累积的文本。 */
    private var segAcc: String = ""

    init {
        // 工作区：老版本只有固定目录 workspace/，ensure() 会把它搬进第一个工作区
        runCatching {
            val list = wsStore.ensure()
            workspaces = list
            val remembered = prefs.codeWorkspaceId
            workspaceId = list.firstOrNull { it.id == remembered }?.id ?: list.first().id
            prefs.codeWorkspaceId = workspaceId
        }
        refreshFiles()
        refreshHistory()
        // 回桌面 / 被别的页面盖住时立刻把手上的内容落盘：进程被杀后磁盘就是「离开那一刻」。
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStopped(activity: android.app.Activity) {
                if (streaming) runCatching { persist(messages) }
            }

            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) = Unit
            override fun onActivityStarted(activity: android.app.Activity) = Unit
            override fun onActivityResumed(activity: android.app.Activity) = Unit
            override fun onActivityPaused(activity: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        })
    }

    /* ── 对外操作 ── */

    fun send(text: String) {
        val trimmed = text.trim()
        if (streaming) return
        if (trimmed.isEmpty()) return
        val userMsg = CodeMessage(role = Role.USER, parts = listOf(CodePart("text", trimmed)))
        val next = messages + userMsg
        messages = next
        if (title == "新对话") title = trimmed.take(24)
        persist(next)
        launchStream(next)
    }

    fun stop() {
        gen++
        job?.cancel()
        job = null
        streaming = false
        toolHint = null
        CodeNotifications.interrupted(getApplication())
        CodeForegroundService.stop(getApplication())
        // 停止时还在跑的工具不会走 replaceTool：把「执行中」抹掉，别让卡片永远停在「…」
        clearRunningTools()
        // 还在缓冲、没来得及上屏的部分一次补齐：停止不该丢字
        if (parts.isNotEmpty() || textOpen) {
            endTextPart()
            messages = base + reply(null)
        }
        // 一个字都没收到就停止：别把空的助手消息留在会话里
        val cleaned = messages.dropLastWhile { isEmptyAssistant(it) }
        if (cleaned.size != messages.size) messages = cleaned
        persist(messages)
        refreshHistory()
    }

    fun regenerate() {
        if (streaming) return
        val keep = messages.dropLastWhile { it.role == Role.ASSISTANT }
        if (keep.isEmpty()) return
        messages = keep
        launchStream(keep)
    }

    fun newConversation() {
        gen++
        job?.cancel()
        job = null
        streaming = false
        toolHint = null
        clearLive()
        convId = UUID.randomUUID().toString()
        createdAt = System.currentTimeMillis()
        title = "新对话"
        messages = emptyList()
        refreshHistory()
    }

    /**
     * 切到历史会话。
     *
     * 点回「当前正在生成」的这条时什么都不做 —— 否则正在写的回答当场消失
     * （与对话页 `ChatViewModel.load` 的 no-op 规则一致）。
     */
    fun openConversation(id: String) {
        if (id == convId && job?.isActive == true) return
        // 必须先停掉在跑的流：否则旧流的收尾会把内容写进新会话 id（内容串台）
        gen++
        job?.cancel()
        job = null
        streaming = false
        toolHint = null
        clearLive()
        val conv = convStore.loadAll().firstOrNull { it.id == id } ?: return
        convId = conv.id
        createdAt = conv.createdAt
        title = conv.title
        messages = conv.messages
    }

    fun rename(t: String) {
        val trimmed = t.trim()
        if (trimmed.isEmpty()) return
        title = trimmed
        persist(messages)
        refreshHistory()
    }

    fun deleteCurrent() {
        gen++
        job?.cancel()
        job = null
        streaming = false
        toolHint = null
        runCatching { convStore.delete(convId) }
        clearLive()
        convId = UUID.randomUUID().toString()
        createdAt = System.currentTimeMillis()
        title = "新对话"
        messages = emptyList()
        refreshHistory()
    }

    fun updateModelTier(id: String) {
        modelTier = id
        prefs.modelTier = id
    }

    fun updateWebSearch(on: Boolean) {
        webSearch = on
        prefs.webSearch = on
    }

    fun refreshFiles() {
        files = runCatching {
            CodeWorkspace.list(wsRoot())
        }.getOrDefault(emptyList())
    }

    /** 当前工作区的会话（历史 sheet 默认只看这个）。 */
    fun refreshHistory() {
        history = runCatching { convStore.loadAll() }
            .getOrDefault(emptyList())
            .filter { convWorkspace(it) == workspaceId }
            .sortedByDescending { it.updatedAt }
    }

    /** 老会话没有 workspaceId（v0.17.0 之前）：归到第一个工作区，别让它们凭空消失。 */
    private fun convWorkspace(c: CodeConversation): String =
        c.workspaceId.ifBlank { workspaces.firstOrNull()?.id.orEmpty() }

    /** 全部工作区的会话，按「工作区 → 时间倒序」分组（历史 sheet 用）。 */
    fun historyGroups(): List<Pair<CodeWorkspaceMeta, List<CodeConversation>>> {
        val all = runCatching { convStore.loadAll() }.getOrDefault(emptyList())
        return workspaces.map { ws ->
            ws to all.filter { convWorkspace(it) == ws.id }.sortedByDescending { it.updatedAt }
        }
    }

    /** 历史 sheet 里按 id 改名（不影响当前会话的其它状态）。 */
    fun renameConversation(id: String, name: String) {
        val n = name.trim().take(40)
        if (n.isEmpty()) return
        val conv = runCatching { convStore.loadAll().firstOrNull { it.id == id } }.getOrNull() ?: return
        convStore.upsert(conv.copy(title = n, updatedAt = System.currentTimeMillis()))
        if (convId == id) title = n
        refreshHistory()
    }

    /** 历史 sheet 里按 id 删除；删的是当前会话就换成一条新的空白会话。 */
    fun deleteConversation(id: String) {
        runCatching { convStore.delete(id) }
        if (convId == id) {
            gen++
            job?.cancel()
            job = null
            streaming = false
            toolHint = null
            clearLive()
            convId = UUID.randomUUID().toString()
            createdAt = System.currentTimeMillis()
            title = "新对话"
            messages = emptyList()
        }
        refreshHistory()
    }

    /* ── 工作区 ── */

    /** 切工作区：停掉当前流，换目录，开一条新会话。 */
    fun switchWorkspace(id: String) {
        if (id == workspaceId) return
        gen++
        job?.cancel()
        job = null
        streaming = false
        toolHint = null
        CodeNotifications.interrupted(getApplication())
        CodeForegroundService.stop(getApplication())
        clearLive()
        workspaceId = id
        prefs.codeWorkspaceId = id
        convId = UUID.randomUUID().toString()
        createdAt = System.currentTimeMillis()
        title = "新对话"
        messages = emptyList()
        refreshFiles()
        refreshHistory()
    }

    fun createWorkspace(name: String) {
        val meta = wsStore.create(name)
        workspaces = wsStore.loadAll()
        switchWorkspace(meta.id)
    }

    fun renameWorkspace(id: String, name: String) {
        wsStore.rename(id, name)
        workspaces = wsStore.loadAll()
    }

    /** 删工作区：连目录一起删。至少留一个（否则界面没有可用工作区）。 */
    fun deleteWorkspace(id: String) {
        val rest = workspaces.filterNot { it.id == id }
        if (rest.isEmpty()) return
        wsStore.delete(id)
        workspaces = wsStore.loadAll()
        if (workspaceId == id) switchWorkspace(rest.first().id)
        refreshHistory()
    }

    /* ── 内部 ── */

    private fun clearLive() {
        synchronized(parts) { parts.clear() }
        textOpen = false
        segAcc = ""
        replyTs = 0L
        base = emptyList()
    }

    private fun partsCopy(): List<CodePart> = synchronized(parts) { parts.toList() }

    private fun reply(error: String?): CodeMessage =
        CodeMessage(role = Role.ASSISTANT, parts = partsCopy(), ts = replyTs, error = error)

    private fun publish(error: String? = null, onlyIf: Int? = null) {
        if (onlyIf != null && onlyIf != gen) return
        messages = base + reply(error)
    }

    /** 把当前 text 块定稿：工具要插进来了，后面的文字会是新的一块。 */
    private fun endTextPart() {
        if (!textOpen) return
        textOpen = false
        segAcc = ""
        synchronized(parts) {
            if (parts.lastOrNull()?.let { it.kind == "text" && it.text.isBlank() } == true) {
                parts.removeAt(parts.lastIndex)
            }
        }
    }

    private fun appendToolPlaceholder(run: CodeToolRun) {
        synchronized(parts) { parts += CodePart("tool", tool = run) }
    }

    private fun replaceTool(id: String, run: CodeToolRun) {
        synchronized(parts) {
            val i = parts.indexOfLast { it.kind == "tool" && it.tool?.id == id }
            if (i >= 0) parts[i] = parts[i].copy(tool = run.copy(running = false))
        }
    }

    /** 把还在「执行中」的工具卡收尾（停止 / 异常退出时调用）。 */
    private fun clearRunningTools() {
        synchronized(parts) {
            for (i in parts.indices) {
                val t = parts[i].tool ?: continue
                if (t.running) parts[i] = parts[i].copy(tool = t.copy(running = false))
            }
        }
    }

    private fun isEmptyAssistant(m: CodeMessage): Boolean =
        m.role == Role.ASSISTANT &&
            m.error == null &&
            m.plainText().isBlank() &&
            m.parts.none { it.kind == "tool" }

    private fun persist(list: List<CodeMessage>) {
        // 落盘时丢掉「还没出字的助手占位」：进程在思考阶段被杀，重开会看到一条空白回复
        val cleaned = list.dropLastWhile { isEmptyAssistant(it) }
        runCatching {
            convStore.upsert(
                CodeConversation(
                    id = convId,
                    title = title,
                    workspaceId = workspaceId,
                    createdAt = createdAt,
                    updatedAt = System.currentTimeMillis(),
                    messages = cleaned,
                )
            )
        }
    }

    private fun launchStream(next: List<CodeMessage>) {
        val cfg = com.nairocy.claude.data.SettingsStore(getApplication()).load()
            .copy(temperature = 0.4, systemPrompt = SYSTEM_PROMPT)
        // 门禁：套餐买不起这个档位就回落 Sonnet（不改写 prefs，升级后仍是用户原选择）
        val tier = modelTierById(modelTier)

        gen++
        val genAtStart = gen
        streaming = true
        // 后台保命：前台服务（ROM 不会冻）+ 一条常驻进度条；两者都受通知开关控制
        CodeForegroundService.start(getApplication())
        runCatching {
            CodeNotifications.progress(
                getApplication(),
                "Claude Code 正在思索…",
                workspaceName + " · " + title,
                convId,
            )
        }
        base = next
        replyTs = System.currentTimeMillis()
        textOpen = false
        segAcc = ""
        synchronized(parts) { parts.clear() }
        // 先挂一条空助手消息，UI 才有「思考中」可显示；这条消息的 ts 全程不变
        // （流式浮升按 (消息 ts + 块序号) 记状态，每帧换 ts 会每帧从头浮升一遍）。
        messages = base + CodeMessage(role = Role.ASSISTANT, parts = emptyList(), ts = replyTs)

        val convIdAtStart = convId
        val titleAtStart = title
        val workspaceAtStart = workspaceId
        val createdAtAtStart = createdAt

        job = viewModelScope.launch {
            val app = getApplication<Application>()
            val wire = withContext(Dispatchers.IO) {
                next.map { m -> WireMessage(role = m.role, text = m.plainText()) }
            }

            // 额外系统段：设备时间 / 用户偏好 / 工作区文件清单
            var extraSystem = ""
            fun addSection(text: String) {
                if (text.isBlank()) return
                extraSystem = if (extraSystem.isBlank()) text else extraSystem + "\n\n" + text
            }
            addSection(
                runCatching { com.nairocy.claude.data.ContextInfo.promptBlock(app) }
                    .getOrDefault("")
            )
            val userPrefs = runCatching { accountStore.preferences.trim() }.getOrDefault("")
            if (userPrefs.isNotEmpty()) {
                addSection("用户的个人偏好（请在所有回复中遵守）：\n$userPrefs")
            }
            val wsFiles = runCatching { CodeWorkspace.list(wsRoot()) }.getOrDefault(emptyList())
            if (wsFiles.isNotEmpty()) {
                val lines = wsFiles.take(40).joinToString("\n") { "- ${it.path}（${it.size} B）" }
                addSection("工作区文件（路径以 / 开头，共 ${wsFiles.size} 个）：\n$lines")
            }

            val tools = FILE_TOOLS

            val onTool: suspend (String, String) -> String = { name, args ->
                val argObj = runCatching { json.parseToJsonElement(args).jsonObject }.getOrNull()
                val target = targetOf(name, argObj)
                val toolId = UUID.randomUUID().toString()
                // 工具开始：先把当前 text 块定稿，再插工具占位块（ok=true），执行完替换成终态
                endTextPart()
                appendToolPlaceholder(CodeToolRun(id = toolId, name = name, target = target, ok = true, running = true))
                toolHint = hintFor(name, target)
                publish(onlyIf = genAtStart)
                try {
                    when (name) {
                        else -> {
                            // 文件工具（ls / read / write / edit / grep）：同步读写本机工作区
                            val outcome = CodeWorkspace.runTool(wsRoot(), toolId, name, argObj ?: JsonObject(emptyMap()))
                            replaceTool(toolId, outcome.run)
                            outcome.content
                        }
                    }
                } catch (t: Throwable) {
                    // edit 的 old_str 不唯一 / 文件不存在：折成 ok=false 的工具卡，错误文案回给模型
                    val msg = t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
                    replaceTool(toolId, CodeToolRun(toolId, name, target, ok = false, output = msg))
                    "工具执行失败：$msg"
                } finally {
                    // 工具结束（含异常/取消）就恢复随机思考文案
                    if (genAtStart == gen) {
                        toolHint = null
                        publish(onlyIf = genAtStart)
                    }
                }
            }

            var failure: String? = null
            val startedAt = System.currentTimeMillis()
            var firstToken = true
            var continuationHint = false
            // 每个 token 都刷 messages 会让主线程每秒重组上百次、Markdown 全文重解析。
            // 按 80ms 时间片合并，逐字节奏交给渲染层。
            var lastUiAt = 0L
            var lastPersistAt = 0L
            var lastNotifyAt = 0L

            suspend fun persistPartial() {
                if (parts.isEmpty() && textOpen.not()) return
                val snapshot = base + reply(null)
                withContext(Dispatchers.IO) {
                    convStore.upsert(
                        CodeConversation(
                            id = convIdAtStart,
                            title = titleAtStart,
                            workspaceId = workspaceAtStart,
                            createdAt = createdAtAtStart,
                            updatedAt = System.currentTimeMillis(),
                            messages = snapshot,
                        )
                    )
                }
            }

            var cancelled = false
            try {
                client.stream(
                    config = cfg,
                    history = wire,
                    extraSystem = extraSystem,
                    tools = tools,
                    onToolCall = onTool,
                    reasoningEffort = "",
                    maxToolRounds = MAX_TOOL_ROUNDS,
                ).collect { ev ->
                    when (ev) {
                        is StreamEvent.Continuing -> {
                            // 续写/空正文重试的空窗期可能很长，不提示看起来像卡死
                            continuationHint = true
                            toolHint = if (ev.reason == "retry") "重新生成中…" else "正在接着写…"
                        }

                        is StreamEvent.Delta -> {
                            if (continuationHint) {
                                continuationHint = false
                                toolHint = null
                            }
                            if (firstToken) {
                                firstToken = false
                                // 首字太快时「思考过程」一闪就没，补足最短展示时间
                                val wait = MIN_THINKING_MS - (System.currentTimeMillis() - startedAt)
                                delay(if (wait > 0) wait else 0)
                            }
                            // 工具跑完后第一条 Delta 会开一个新 text 块
                            if (!textOpen) {
                                synchronized(parts) { parts += CodePart("text", "") }
                                textOpen = true
                                segAcc = ""
                            }
                            segAcc += ev.text
                            synchronized(parts) {
                                if (parts.isNotEmpty() && parts.last().kind == "text") {
                                    parts[parts.lastIndex] = parts.last().copy(text = segAcc)
                                }
                            }
                            val now = System.currentTimeMillis()
                            if (now - lastUiAt >= UI_FLUSH_MS) {
                                lastUiAt = now
                                publish(onlyIf = genAtStart)
                            }
                            if (now - lastPersistAt >= PERSIST_MS) {
                                lastPersistAt = now
                                persistPartial()
                            }
                            // 通知 1.2s 刷一次就够：跨进程调用，按字刷会拖慢主线程
                            if (now - lastNotifyAt >= NOTIFY_MS) {
                                lastNotifyAt = now
                                runCatching {
                                    CodeNotifications.progress(
                                        app,
                                        "Claude Code 正在输出…",
                                        "$workspaceAtStart · 已输出 ${segAcc.length} 字",
                                        convIdAtStart,
                                    )
                                }
                            }
                        }

                        is StreamEvent.Failed -> failure = ev.message
                        StreamEvent.Done -> Unit
                    }
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                // 用户点了停止 / 切了会话：把已经收到的部分保住，再让取消继续传播
                cancelled = true
                runCatching {
                    withContext(NonCancellable) {
                        if (parts.isNotEmpty() || textOpen) persistPartial()
                    }
                }
                throw ce
            } catch (t: Throwable) {
                // 断网 / 超时 / 代理掐连接：折成一条错误行，已收到的内容保住，绝不把进程带走
                failure = "连接中断：" + (t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName)
                android.util.Log.w("CodeViewModel", "stream 中断", t)
            } finally {
                if (cancelled) {
                    // 已由 stop() / 换会话那边收尾：这里只清理，不覆盖界面状态
                    if (genAtStart == gen) toolHint = null
                } else if (genAtStart != gen) {
                    // 这一轮已被新一轮/换会话顶掉：它的 parts 与状态都不属于界面了，
                    // 只把磁盘上的那一份落好（按开跑时的会话 id），不要碰当前界面。
                    runCatching { persistPartial() }
                } else {
                    try {
                        endTextPart()
                        clearRunningTools()
                        // 收尾丢掉空 text 块（工具卡保留）
                        val cleanedParts = partsCopy().filterNot { it.kind == "text" && it.text.isBlank() }
                        synchronized(parts) {
                            parts.clear()
                            parts.addAll(cleanedParts)
                        }
                        if (parts.isEmpty() && failure == null) {
                            synchronized(parts) { parts += CodePart("text", "（没有收到内容）") }
                        }
                        val finalList = base + reply(failure)
                        messages = finalList
                        persist(finalList)
                        if (failure == null && partsCopy().isNotEmpty()) {
                            runCatching {
                                CodeNotifications.done(app, titleAtStart, finalList.lastOrNull()?.parts
                                    ?.filter { it.kind == "text" }?.joinToString("") { it.text }.orEmpty(), convIdAtStart)
                            }
                        } else {
                            CodeNotifications.interrupted(app)
                        }
                        if (failure == null) {
                            runCatching { com.nairocy.claude.data.Haptics.done(app) }
                        }
                        refreshFiles()
                        refreshHistory()
                    } finally {
                        // 写盘失败也不能把界面卡在「思考中」
                        toolHint = null
                        streaming = false
                        CodeForegroundService.stop(app)
                    }
                }
            }
        }
    }

    private companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** 工具循环上限：文件工具会连着调很多次（ls → read → edit → grep…）。 */
        const val MAX_TOOL_ROUNDS = 12

        /** 思考指示最短展示时长（ms）。首字再快也要撑这么久。 */
        const val MIN_THINKING_MS = 700L

        /** 流式期间 UI 刷新间隔（ms）。 */
        const val UI_FLUSH_MS = 80L

        /** 流式期间落盘间隔（ms）。 */
        const val PERSIST_MS = 3000L

        /** 常驻通知刷新间隔（ms）：notify 是跨进程调用，别按字刷。 */
        const val NOTIFY_MS = 1200L

        /** Code 页的人格底座（英文，人格在前；与对话页的 persona 不同：这里是「改工作区」的工程师）。 */
        const val SYSTEM_PROMPT = """You're Claude Code inside the Claude Android app. You build a static web project that lives in the
user's on-device workspace; the entry file is /index.html and assets use relative paths.

Workflow: start with ls to see what exists, read the files you intend to change, then edit for small
changes or write for new files. Never guess a file's contents — read it first. After finishing, say in
one or two sentences what you changed. Always reply in the user's language.

Do not use emoji. Write clean modern front-end code: semantic HTML, plain CSS/JS, no build step, no
external CDN dependencies, no external fonts."""

        private fun params(vararg fields: Triple<String, String, Boolean>): JsonObject = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                fields.forEach { (name, desc, _) ->
                    put(name, buildJsonObject {
                        put("type", "string")
                        put("description", desc)
                    })
                }
            })
            put("required", buildJsonArray {
                fields.filter { it.third }.forEach { add(JsonPrimitive(it.first)) }
            })
        }

        /** 5 个文件工具：读写本机工作区（沙箱在 [CodeWorkspace] 里）。 */
        val FILE_TOOLS: List<ToolSpec> = listOf(
            ToolSpec(
                name = "ls",
                description = "列出工作区里的全部文件（路径 + 大小）。动手改任何东西之前先调用它，看清已有什么。",
                parameters = params(),
            ),
            ToolSpec(
                name = "read",
                description = "读一个文件的全文（带行号）。路径以 / 开头，例如 /index.html。" +
                    "要改文件必须先读它，不要凭猜。",
                parameters = params(
                    Triple("path", "要读的文件路径，以 / 开头，例如 /index.html 或 /style.css", true),
                ),
            ),
            ToolSpec(
                name = "write",
                description = "写入（新建或整文件覆盖）一个文件。路径以 / 开头。会返回行级 diff。" +
                    "小改动优先用 edit，避免整文件重写。",
                parameters = params(
                    Triple("path", "文件路径，以 / 开头，例如 /index.html", true),
                    Triple("content", "完整的文件内容（整份，不是片段）", true),
                ),
            ),
            ToolSpec(
                name = "edit",
                description = "把文件里的一段 old_str 替换成 new_str。**调用前必须先用 read 读原文**：" +
                    "old_str 必须是文件里唯一出现的一段（含缩进与换行，原样照抄）。" +
                    "路径以 / 开头。找不到或不唯一都会失败。",
                parameters = params(
                    Triple("path", "文件路径，以 / 开头，例如 /style.css", true),
                    Triple("old_str", "要被替换的原文片段，必须与文件内容逐字符一致且在文件里只出现一次", true),
                    Triple("new_str", "替换成的新内容（可为空字符串表示删除这段）", true),
                ),
            ),
            ToolSpec(
                name = "grep",
                description = "在工作区所有文本文件里搜索一个字符串/正则片段，返回「文件:行号:内容」，最多 60 条。" +
                    "用来定位要改的地方。",
                parameters = params(
                    Triple("pattern", "要搜索的字符串或正则片段", true),
                ),
            ),
        )

        /** 联网搜索（与对话页同一套描述与实现）。 */
        val WEB_SEARCH_TOOL = ToolSpec(
            name = "web_search",
            description = "联网搜索实时网页信息。只在需要最新/外部事实时调用：新闻、当前事件、" +
                "实时数据（价格、天气、版本号、发布日期）、你不确定或可能过时的知识。" +
                "改工作区里的页面不需要联网，不要调用；同一个问题最多搜一次。" +
                "搜索结果会带 url；摘要不足以回答、需要页面里的细节时，再用 fetch_page 抓那几页正文。",
            parameters = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("query", buildJsonObject {
                        put("type", "string")
                        put("description", "搜索词，简洁明确，1-128 字")
                    })
                })
                put("required", buildJsonArray { add(JsonPrimitive("query")) })
            },
        )

        /** 网页抓取。 */
        val FETCH_TOOL = ToolSpec(
            name = "fetch_page",
            description = "抓取指定网页的正文（Markdown）。当搜索结果的摘要不足以回答、需要页面里的" +
                "具体数据、原文细节或最新数字时调用。只抓最相关的 1-3 个页面；不要抓搜索结果列表页、" +
                "登录墙、视频页；同一个 url 不要重复抓。抓不到就换一条结果，不要把失败当成结论。",
            parameters = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("url", buildJsonObject {
                        put("type", "string")
                        put("description", "要抓取的完整 http(s) 地址，通常来自 web_search 的结果")
                    })
                })
                put("required", buildJsonArray { add(JsonPrimitive("url")) })
            },
        )

        /** 工具卡右侧的灰字（path / pattern / query / url）。 */
        fun targetOf(name: String, args: JsonObject?): String {
            val key = when (name) {
                "read", "write", "edit" -> "path"
                "grep" -> "pattern"
                "web_search" -> "query"
                "fetch_page" -> "url"
                else -> return ""
            }
            return args?.get(key)?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        }

        /** 思考行的提示文案。 */
        fun hintFor(name: String, target: String): String = when (name) {
            "ls" -> "查看工作区…"
            "read" -> "读取 ${target.ifBlank { "文件" }}…"
            "write" -> "写入 ${target.ifBlank { "文件" }}…"
            "edit" -> "编辑 ${target.ifBlank { "文件" }}…"
            "grep" -> "搜索工作区…"
            "web_search" -> "搜索以太网..."
            "fetch_page" -> "抓取页面..."
            else -> "执行 $name…"
        }
    }
}

/** 助手消息里所有 text 块拼起来的纯文本（不进模型上下文里那些工具卡）。 */
private fun CodeMessage.plainText(): String =
    parts.filter { it.kind == "text" }.joinToString("") { it.text }

/**
 * 进程级 Code 会话持有者（照 ChatSessions）。
 *
 * ⚠️ 不要改回 `viewModel(key = "code-…")`：ViewModel 挂在 Activity 的 ViewModelStore 上，
 * Activity 在后台被销毁是常态，onCleared 会把正在跑的流一起取消，用户切个页面答案就没了。
 */
object CodeSessions {
    private val byUid = mutableMapOf<String, CodeViewModel>()

    /** 这个账号的 Code 会话是否已经在本次进程里创建过（区分「Activity 重建」与「进程重启」）。 */
    fun has(uid: String): Boolean = byUid.containsKey(uid)

    fun of(app: Application, uid: String): CodeViewModel =
        byUid.getOrPut(uid) { CodeViewModel(app) }
}
