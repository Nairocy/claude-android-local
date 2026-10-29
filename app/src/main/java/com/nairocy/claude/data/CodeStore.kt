package com.nairocy.claude.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Code 页的数据模型（`docs/CODE.md` 第 2 节）。
 *
 * 与对话页的区别：助手消息不是一个字符串，而是**有序块**（text / tool）。
 * 工具块自带 diff，界面上能看见 agent 到底改了哪几行。
 */

/** 一行 diff。"+" 行记新文件行号，"-" 行记旧文件行号，都从 1 开始。 */
@Serializable
data class CodeDiffLine(
    val op: String,
    val no: Int,
    val text: String,
)

/** 一次工具调用（卡片上展示的那条记录）。 */
@Serializable
data class CodeToolRun(
    val id: String,
    /** ls / read / write / edit / grep / web_search / fetch_page */
    val name: String,
    /** 卡片右侧灰字：path / pattern / query / url */
    val target: String = "",
    val ok: Boolean = true,
    /** 纯文本结果（ls/read/grep 正文；失败时是错误文案） */
    val output: String = "",
    val diff: List<CodeDiffLine> = emptyList(),
    /**
     * 正在执行：卡片右侧显示「…」，执行完（含失败）置回 false。
     * 不落盘也不要紧 —— 序列化默认值不写，读回来天然是 false。
     */
    val running: Boolean = false,
)

/** 助手消息的有序块。kind = "text" | "tool"。 */
@Serializable
data class CodePart(
    val kind: String,
    val text: String = "",
    val tool: CodeToolRun? = null,
)

@Serializable
data class CodeMessage(
    val role: Role,
    val parts: List<CodePart> = emptyList(),
    val ts: Long = System.currentTimeMillis(),
    val error: String? = null,
)

@Serializable
data class CodeConversation(
    val id: String,
    val title: String,
    /** 属于哪个工作区（CodeWorkspaceMeta.id）。老数据没有这个字段 → 默认空串，迁移时归到第一个工作区。 */
    val workspaceId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<CodeMessage> = emptyList(),
)

/**
 * Code 页的会话库：`code_conversations.json`（与对话页的 `conversations.json` 分开，
 * 互不污染）。
 *
 * 写法照抄 [ConversationStore]：单文件 JSON + 原子写（tmp + rename）+ 进程级锁。
 */
class CodeConversationStore(private val context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val file: File get() = File(AppDirs.userRoot(context), "code_conversations.json")

    private fun readAll(): List<CodeConversation> = runCatching {
        if (!file.exists()) emptyList()
        else json.decodeFromString<List<CodeConversation>>(file.readText(Charsets.UTF_8))
    }.getOrDefault(emptyList())

    /** 最近改过的排最前（历史 sheet 直接用它）。 */
    fun loadAll(): List<CodeConversation> =
        synchronized(LOCK) { readAll() }.sortedByDescending { it.updatedAt }

    fun saveAll(list: List<CodeConversation>) {
        // ⚠️ 整个 Code 会话库都在这一个 JSON 里：先写 tmp 再 rename，进程在写一半时被杀
        // 也不会留下半截文件（半截 = loadAll 解析失败 = 所有会话看起来都没了）。
        // 写操作串行化：流式期间周期落盘在 IO 线程写，stop()/回桌面的落盘在主线程写。
        synchronized(LOCK) { writeLocked(list) }
    }

    private fun writeLocked(list: List<CodeConversation>) {
        runCatching {
            val dir = AppDirs.userRoot(context)
            val tmp = File(dir, "code_conversations.json.tmp")
            tmp.writeText(
                json.encodeToString(
                    ListSerializer(CodeConversation.serializer()),
                    list.sortedByDescending { it.updatedAt },
                ),
                Charsets.UTF_8,
            )
            if (!tmp.renameTo(file)) {
                // 极少数文件系统 rename 失败：退回直接写（内容已经算好了）
                file.writeText(tmp.readText(Charsets.UTF_8), Charsets.UTF_8)
                tmp.delete()
            }
        }
    }

    fun upsert(conv: CodeConversation) {
        synchronized(LOCK) {
            val all = readAll().toMutableList()
            val i = all.indexOfFirst { it.id == conv.id }
            if (i >= 0) all[i] = conv else all.add(conv)
            writeLocked(all)
        }
    }

    fun delete(id: String) {
        synchronized(LOCK) { writeLocked(readAll().filterNot { it.id == id }) }
    }

    private companion object {
        /** 进程级锁：不同 CodeViewModel / 不同线程共享同一个会话文件。 */
        val LOCK = Any()
    }
}

/** 所有 text 块拼起来（复制 / 分享 / 起标题用）。多块之间留一个空行。 */
fun CodeMessage.textOf(): String = parts
    .filter { it.kind == "text" && it.text.isNotBlank() }
    .joinToString("\n\n") { it.text.trim() }

/**
 * 把「最后一个 text 块」替换成 [t]；没有 text 块就追加一个。
 * 流式时每来一批 delta 都调它，工具块因此不会被冲掉。
 */
fun CodeMessage.withText(t: String): CodeMessage {
    val idx = parts.indexOfLast { it.kind == "text" }
    val newParts: List<CodePart> = if (idx < 0) {
        parts + CodePart(kind = "text", text = t)
    } else {
        parts.toMutableList().also { it[idx] = it[idx].copy(text = t) }
    }
    return copy(parts = newParts)
}
