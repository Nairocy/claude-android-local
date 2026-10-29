package com.nairocy.claude.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nairocy.claude.data.AccountStore
import com.nairocy.claude.data.ChatMessage
import com.nairocy.claude.data.ChatNotifications
import com.nairocy.claude.data.Conversation
import com.nairocy.claude.data.Attachment
import com.nairocy.claude.data.ConversationStore
import com.nairocy.claude.data.FileStore
import com.nairocy.claude.data.ImageStore
import com.nairocy.claude.data.Role
import com.nairocy.claude.net.OpenAiClient
import com.nairocy.claude.net.StreamEvent
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import com.nairocy.claude.net.WireMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val convStore = ConversationStore(app)
    private val accountStore = AccountStore(app)
    private val imageStore = ImageStore(app)
    private val fileStore = FileStore(app)
    private val client = OpenAiClient()
    private val settingsStore = com.nairocy.claude.data.SettingsStore(app)
    private val prefs = com.nairocy.claude.data.Prefs(app)

    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
        private set
    var streaming by mutableStateOf(false)
        private set
    var input by mutableStateOf("")
    var title by mutableStateOf("新对话")
        private set
    var pinned by mutableStateOf(false)
        private set
    /** 已选好、还没发出去的图片（文件名）。 */
    var pendingImages by mutableStateOf<List<String>>(emptyList())
        private set
    /** 已选好、还没发出去的非图片附件。 */
    var pendingAttachments by mutableStateOf<List<Attachment>>(emptyList())
        private set
    /** 无痕对话：不落盘、不进抽屉。 */
    var incognito by mutableStateOf(false)
        private set

    /** 网页搜索开关（DeepSeek /v1/tools/search）。 */
    var webSearch by mutableStateOf(prefs.webSearch)
        private set

    /** 模型档位：fable / opus / sonnet / haiku → reasoning_effort。 */
    var modelTier by mutableStateOf(prefs.modelTier)
        private set

    fun updateModelTier(id: String) {
        modelTier = id
        prefs.modelTier = id
    }

    /** 记忆开关：开启时把 files/memory.md 注入系统提示。 */
    var memory by mutableStateOf(prefs.memory)
        private set

    /**
     * 工具执行中的提示文案（"搜索以太网..." / "抓取页面..."），由 ChatScreen 的
     * ThinkingRow 显示；不在工具执行期间时为 null（此时显示随机思考文案）。
     */
    var toolHint by mutableStateOf<String?>(null)
        private set

    fun updateWebSearch(on: Boolean) {
        webSearch = on
        prefs.webSearch = on
    }

    fun updateMemory(on: Boolean) {
        memory = on
        prefs.memory = on
    }

    /** 正在编辑的历史用户消息下标（null = 普通输入）。 */
    var editingIndex by mutableStateOf<Int?>(null)
        private set

    /** 本轮流式已收到的完整文本（含还没上屏的部分），停止时用来一次补齐。 */
    private var liveText: String = ""

    private var convId: String = UUID.randomUUID().toString()

    /** 当前会话 id（用于「添加到项目」）。 */
    val currentId: String get() = convId
    private var job: Job? = null

    init {
        // 回桌面 / 被别的页面盖住时立刻把手上的内容落盘：
        // 后台被系统杀掉后，磁盘就是「离开那一刻」的版本，而不是最多 3 秒前的旧快照。
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

    fun load(conversation: Conversation?) {
        // 点回「当前正在生成」的这条会话时什么都不要动：
        // 旧实现会先 cancel 再从磁盘读，正在写的回答当场消失，界面只剩用户那句（用户报过）。
        if (conversation != null && conversation.id == convId && job?.isActive == true) return
        // 必须先停掉在跑的流：否则旧流的收尾会把内容 persist 到新会话 id 上，
        // 造成「删不掉的重复会话 / 内容串台」。
        job?.cancel()
        ChatNotifications.interrupted(getApplication())
        job = null
        streaming = false
        toolHint = null
        pendingImages = emptyList()
        pendingAttachments = emptyList()
        incognito = false
        editingIndex = null
        if (conversation == null) {
            convId = UUID.randomUUID().toString()
            title = "新对话"
            pinned = false
            messages = emptyList()
        } else {
            convId = conversation.id
            title = conversation.title
            pinned = conversation.pinned
            messages = conversation.messages
        }
    }

    /**
     * 首页是「新对话」的起点：如果 vm 还挂着上一段会话（有消息 / 无痕），
     * 在发消息、选图、选文件之前先切到全新会话。
     *
     * 不做这一步就会出现「首页看着是新的起始页，发出去却追加进上一条会话」——
     * 因为从对话页按返回键回首页时并不会重置 convId。
     */
    fun startFreshIfStale() {
        if (messages.isNotEmpty() || incognito) load(null)
    }

    /** 首页右上「无痕对话」：开一段不落盘的新会话。 */
    fun startIncognito() {
        job?.cancel()
        ChatNotifications.interrupted(getApplication())
        job = null
        streaming = false
        convId = UUID.randomUUID().toString()
        title = "无痕对话"
        pinned = false
        incognito = true
        messages = emptyList()
        pendingImages = emptyList()
        pendingAttachments = emptyList()
    }

    /** 退出无痕：清掉无痕会话，回到普通新对话。 */
    fun exitIncognito() {
        if (incognito) load(null)
    }

    /** 长按菜单「编辑」：进入编辑态，输入框由界面填入原文本。 */
    fun beginEdit(index: Int) {
        if (index !in messages.indices) return
        if (messages[index].role != Role.USER) return
        if (streaming) return
        editingIndex = index
    }

    fun cancelEdit() {
        editingIndex = null
    }

    /** 编辑后重发：从这里截断，用新文本替换该条用户消息，再重新请求。 */
    fun sendEdited(text: String) {
        val idx = editingIndex ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (idx !in messages.indices) { editingIndex = null; return }
        val edited = messages[idx].copy(content = trimmed)
        val next = messages.take(idx) + edited
        editingIndex = null
        pendingImages = emptyList()
        pendingAttachments = emptyList()
        messages = next
        if (title == "新对话") title = trimmed.take(24)
        persist(next)
        launchStream(next)
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (streaming) return
        if (trimmed.isEmpty() && pendingImages.isEmpty() && pendingAttachments.isEmpty()) return
        val userMsg = ChatMessage(
            role = Role.USER,
            content = trimmed,
            images = pendingImages,
            attachments = pendingAttachments,
        )
        pendingImages = emptyList()
        pendingAttachments = emptyList()
        val next = messages + userMsg
        messages = next
        if (title == "新对话") {
            title = if (trimmed.isNotEmpty()) trimmed.take(24) else "图片对话"
        }
        persist(next)
        launchStream(next)
    }

    /** 从相册选了一张图：压缩落地后挂到待发列表。 */
    fun addImage(uri: android.net.Uri) {
        imageStore.import(uri)?.let { pendingImages = pendingImages + it }
    }

    fun removePendingImage(name: String) {
        pendingImages = pendingImages - name
    }

    /** 从「文件」入口选了一个文件：图片转图片通道，其余当附件。 */
    fun addFile(uri: android.net.Uri) {
        val att = fileStore.import(uri) ?: return
        if (fileStore.isImage(att)) {
            imageStore.import(uri)?.let { pendingImages = pendingImages + it }
        } else {
            pendingAttachments = pendingAttachments + att
        }
    }

    fun removePendingAttachment(file: String) {
        pendingAttachments = pendingAttachments.filterNot { it.file == file }
    }

    /** 重生成：丢掉最后一条助手回复，用同一段上下文重发。 */
    fun regenerate() {
        if (streaming) return
        val base = messages.dropLastWhile { it.role == Role.ASSISTANT }
        if (base.isEmpty()) return
        messages = base
        launchStream(base)
    }

    private fun launchStream(next: List<ChatMessage>) {
        // 本地版：接口地址 / Key / 模型全部来自「设置 → API 设置」，不经过任何代理
        val cfg = settingsStore.load()
        streaming = true
        liveText = ""
        // 流式累积与落盘都以 base 为底（不含这条尚未生成的助手消息）
        val base = next
        // 关键：先挂一条内容为空的助手消息，UI 才有「思考中」可显示。
        // 否则要等首个 token 才创建消息，整个等待期界面上什么都不显示。
        // ⚠️ 这条消息的 ts 必须全程不变：流式浮升按 (消息 ts + 块序号) 记状态，
        // 每 80ms 造一条新的 ChatMessage 会让它每帧重置、从头浮升一遍。
        val reply = ChatMessage(Role.ASSISTANT, "")
        messages = base + reply
        // 记住这次流属于哪个会话：中途停/切走/被杀，已收到的内容也要能写回原会话，
        // 不能写进当前 convId（那会造成串台）。
        val convIdAtStart = convId
        val titleAtStart = title
        val pinnedAtStart = pinned
        val incognitoAtStart = incognito
        // 通知（设置→通知→回复 + 系统通知权限，两个都满足才发）：
        // 生成中挂一条常驻条，写完换成「会话已完成」。无痕对话一个字都不往通知栏留。
        val notifyApp = getApplication<android.app.Application>()
        val notifyOn = !incognitoAtStart
        if (notifyOn) {
            ChatNotifications.progress(notifyApp, "Claude 正在思索…", titleAtStart, convIdAtStart)
        }
        job = viewModelScope.launch {
            // base64 编码放 IO：大图在主线程转会卡首字
            val wire = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                next.map { m ->
                    // 文本类附件直接拼进正文；PDF 渲染成图走多模态
                    val textParts = buildString {
                        if (m.content.isNotBlank()) append(m.content)
                        m.attachments.filter { fileStore.isTextLike(it) }.forEach { att ->
                            fileStore.readText(att)?.let { body ->
                                if (isNotEmpty()) append("\n\n")
                                append("[附件 ").append(att.name).append("]\n").append(body)
                            }
                        }
                    }
                    val images = m.images.mapNotNull { imageStore.dataUrl(it) }.toMutableList()
                    m.attachments.filter { fileStore.isPdf(it) }.forEach { att ->
                        fileStore.pdfToImageNames(att).forEach { name ->
                            imageStore.dataUrl(name)?.let { images += it }
                        }
                    }
                    WireMessage(role = m.role, text = textParts, imageDataUrls = images)
                }
            }
            // 记忆：拼进 system。联网搜索改成 function calling——由模型自己决定要不要搜。
            var extraSystem = ""
            fun addSection(text: String) {
                if (text.isBlank()) return
                extraSystem = if (extraSystem.isBlank()) text else extraSystem + "\n\n" + text
            }
            // 设备上下文：当前时间 / 位置 / 今天的日程（没授权就不注入，绝不编造）
            addSection(
                runCatching { com.nairocy.claude.data.ContextInfo.promptBlock(getApplication()) }
                    .getOrDefault("")
            )
            // 账号里的「个人偏好」（设置 → 个人资料）按用户要求追加进系统提示，所有对话生效
            val userPrefs = runCatching {
                com.nairocy.claude.data.AccountStore(getApplication()).preferences.trim()
            }.getOrDefault("")
            if (userPrefs.isNotEmpty()) {
                addSection("用户的个人偏好（请在所有回复中遵守）：\n$userPrefs")
            }
            // 「我们该怎么称呼您」（设置 → 个人资料）也要进提示词：
            // 只放称呼，不带全名/邮箱 —— 那是账号信息，模型不需要。
            val nick = runCatching { accountStore.preferredName.trim() }.getOrDefault("")
            if (nick.isNotEmpty()) {
                addSection("用户希望你这样称呼他/她：$nick。回复里请直接用这个称呼，不要叫错或省略。")
            }
            // 本地版不内置联网工具：客户端只发对话，搜索由用户自备的上游决定
            val tools = emptyList<com.nairocy.claude.net.ToolSpec>()
            val onTool: (suspend (String, String) -> String)? = null
            if (memory) {
                val mem = com.nairocy.claude.data.MemoryStore(getApplication()).allText()
                if (mem.isNotBlank()) {
                    extraSystem = (extraSystem + "\n\n用户记忆（长期偏好，供参考）：\n" + mem).trim()
                }
            }

            var acc = ""
            var failure: String? = null
            val startedAt = System.currentTimeMillis()
            var firstToken = true
            var continuationHint = false
            // 每个 token 都刷一次 messages 会让主线程每秒重组上百次、Markdown 全文重解析，
            // 长回复（几百行代码）直接把 UI 卡死（表现为「文字不出现 / 消失」）。
            // 这里按 80ms 时间片合并，把整段正文交给界面；**逐字上屏的节奏放在渲染层**
            // （ui/components/RiseText.kt）：界面自己决定显示到第几个 token，
            // 代码块不参与节奏、原样直出。
            var lastUiAt = 0L
            var lastPersistAt = 0L
            var lastNotifyAt = 0L

            // 把当前已累积的文本落盘（流式期间也存，长回复被系统杀掉也不会整段消失）
            suspend fun persistPartial(text: String, error: String? = null) {
                if (incognitoAtStart) return
                if (text.isBlank() && error == null) return
                val snapshot = base + ChatMessage(Role.ASSISTANT, text, error = error)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    convStore.upsert(
                        Conversation(
                            id = convIdAtStart,
                            title = titleAtStart,
                            pinned = pinnedAtStart,
                            messages = snapshot,
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                }
            }

            var cancelled = false
            try {
            client.stream(cfg, wire, extraSystem, tools, onTool, reasoningEffort = "").collect { ev ->
                when (ev) {
                    is StreamEvent.Continuing -> {
                        // 长输出被截断后客户端会自动续写；空正文重试则是「重新生成」。
                        // 这段空窗期（重新思考 + 连上）可能有一两分钟，不提示的话看起来像卡死了。
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
                            // DeepSeek 首字太快时「思考过程」一闪就没，强制补足最短展示时间
                            val wait = MIN_THINKING_MS - (System.currentTimeMillis() - startedAt)
                            kotlinx.coroutines.delay(if (wait > 0) wait else 0)
                        }
                        acc += ev.text
                        liveText = acc
                        val now = System.currentTimeMillis()
                        if (now - lastUiAt >= UI_FLUSH_MS) {
                            lastUiAt = now
                            messages = base + reply.copy(content = acc)
                        }
                        if (now - lastPersistAt >= PERSIST_MS) {
                            lastPersistAt = now
                            persistPartial(acc)
                        }
                        // 常驻通知 1.2s 更新一次就够：每次 notify 都要跨进程，按字刷会拖慢主线程。
                        if (notifyOn && now - lastNotifyAt >= NOTIFY_MS) {
                            lastNotifyAt = now
                            ChatNotifications.progress(
                                notifyApp,
                                "Claude 正在输出…",
                                "$titleAtStart · 已输出 ${acc.length} 字",
                                convIdAtStart,
                            )
                        }
                    }
                    is StreamEvent.Failed -> failure = ev.message
                    StreamEvent.Done -> Unit
                }
            }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                // 用户点了停止 / 切了会话：把已经收到的部分保住，再让取消继续传播
                cancelled = true
                if (notifyOn) ChatNotifications.interrupted(notifyApp)
                persistPartial(acc)
                throw ce
            } catch (t: Throwable) {
                // ⚠️ 读流时断网 / 超时 / 代理掐连接都会抛到这里。原来只有 CancellationException
                // 这一层 catch，其它异常会冒到主线程把进程带走 —— 表现就是「回桌面待一会儿回来，
                // 刚才的回答全没了」。这里统一折成一条错误行，并把已收到的内容保住。
                failure = "连接中断：" + (t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName)
                android.util.Log.w("ChatViewModel", "stream 中断", t)
            }
            if (!cancelled) {
                try {
                    val finalList = base + reply.copy(
                        content = acc.ifBlank { if (failure != null) "" else "（没有收到内容）" },
                        error = failure,
                    )
                    messages = finalList
                    persist(finalList)
                    // 通知收尾：写完换成可点的「会话「X」已完成」，出错/空正文只撤常驻条。
                    if (notifyOn) {
                        if (failure == null && acc.isNotBlank()) {
                            ChatNotifications.done(notifyApp, titleAtStart, acc, convIdAtStart)
                        } else {
                            ChatNotifications.interrupted(notifyApp)
                        }
                    }
                    // 回答完成：两下轻震。失败不震 —— 失败本身有错误行，再震会被误当成「成功了」
                    if (failure == null) com.nairocy.claude.data.Haptics.done(getApplication())
                } finally {
                    // 收尾清掉「正在接着写…」这类临时提示，别留到下一次对话。
                    // 放在 finally：写盘失败也不能把界面卡在「思考中」。
                    toolHint = null
                    streaming = false
                    liveText = ""
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        ChatNotifications.interrupted(getApplication())
        streaming = false
        toolHint = null
        // 还在缓冲、没来得及上屏的部分一次补齐：停止不该丢字
        val pending = liveText
        if (pending.isNotBlank() && messages.lastOrNull()?.role == Role.ASSISTANT) {
            messages = messages.dropLast(1) + messages.last().copy(content = pending)
        }
        // 一个字都没收到就停止：别把空的助手消息留在会话里
        // （error != null 的那种不能删 —— 那是「已切回 Sonnet」之类的提示行）
        val cleaned = messages.dropLastWhile {
            it.role == Role.ASSISTANT && it.content.isBlank() && it.error == null
        }
        if (cleaned.size != messages.size) messages = cleaned
        // 立刻把已收到的部分落盘，别等协程收尾
        persist(messages)
    }

    /** 顶栏 ⋯ 菜单：重命名当前对话。 */
    fun rename(newTitle: String) {
        val t = newTitle.trim()
        if (t.isEmpty()) return
        title = t
        persist(messages)
    }

    /** 顶栏 ⋯ 菜单：置顶 / 取消置顶当前对话。 */
    fun togglePin() {
        pinned = !pinned
        persist(messages)
    }

    /** 顶栏 ⋯ 菜单：删除当前对话，并把状态清成一条新对话。 */
    fun deleteCurrent() {
        job?.cancel()
        ChatNotifications.interrupted(getApplication())
        streaming = false
        convStore.delete(convId)
        load(null)
    }

    private fun persist(list: List<ChatMessage>) {
        if (incognito) return
        // 落盘时丢掉「还没出字的助手占位」：进程在思考阶段被杀，重开会看到一条空白回复，
        // 看起来就像答案丢了。只留用户那句更干净（重新发一次即可）。
        val cleaned = list.dropLastWhile {
            it.role == Role.ASSISTANT && it.content.isBlank() && it.error == null
        }
        convStore.upsert(
            Conversation(
                id = convId,
                title = title,
                pinned = pinned,
                messages = cleaned,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    private companion object {
        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }

        /** 给模型自己判断要不要联网的工具。 */
        val WEB_SEARCH_TOOL = com.nairocy.claude.net.ToolSpec(
            name = "web_search",
            description = "联网搜索实时网页信息。只在需要最新/外部事实时调用：新闻、当前事件、" +
                "实时数据（价格、天气、版本号、发布日期）、你不确定或可能过时的知识。" +
                "闲聊、写作、翻译、编程、数学、逻辑题、常识不要调用；同一个问题最多搜一次。" +
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

        /** 网页抓取：DeepSeek Fetch API（模型自己决定要不要抓、抓哪一页）。 */
        val FETCH_TOOL = com.nairocy.claude.net.ToolSpec(
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

        /** 思考指示最短展示时长（ms）。首字再快也要撑这么久。 */
        const val MIN_THINKING_MS = 700L

        /** 流式期间 UI 刷新间隔（ms）。token 可能每秒几百个，全刷会把主线程打满。 */
        const val UI_FLUSH_MS = 80L

        // 逐字上屏的节奏（示例速率、标点停顿、代码块直出）都在
        // ui/components/RiseText.kt 的 RiseSpec 里，别再在这里做字符级节流。

        /** 流式期间落盘间隔（ms）。长回复中途被系统杀掉也能留下已生成的部分。 */
        const val PERSIST_MS = 3000L

        /** 常驻通知的刷新间隔（ms）。比 UI 刷新慢得多：notify 是跨进程调用。 */
        const val NOTIFY_MS = 1200L
    }
}

/**
 * 进程级会话持有者。
 *
 * ⚠️ 不要改回 `viewModel(key = "chat-…")`：ViewModel 挂在 Activity 的 ViewModelStore 上，
 * Activity 在后台被系统销毁是常态（国产 ROM 更激进），onCleared 会把正在跑的流一起取消，
 * 用户切个页面 / 回个桌面回来，答案就没了。
 *
 * 放到进程里之后：切页面、Activity 重建都不影响生成；进程真的被杀时靠
 * conversations.json 的周期落盘 + onActivityStopped 落盘兜底。
 */
object ChatSessions {
    private val byUid = mutableMapOf<String, ChatViewModel>()

    /** 这个账号的会话是否已经在本次进程里创建过（用来区分「Activity 重建」与「进程重启」）。 */
    fun has(uid: String): Boolean = byUid.containsKey(uid)

    fun of(app: Application, uid: String): ChatViewModel =
        byUid.getOrPut(uid) { ChatViewModel(app) }
}
