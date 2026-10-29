package com.nairocy.claude.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** 模型配置：一套 base_url + key + model 就能接任意 OpenAI 兼容服务。 */
@Serializable
data class ModelConfig(
    val baseUrl: String = "https://api.deepseek.com/v1",
    val apiKey: String = "",
    val model: String = "deepseek-flash",
    val temperature: Double = 1.0,
    val systemPrompt: String = "",
)

@Serializable
enum class Role { USER, ASSISTANT }

/** 非图片附件：原始文件名 + mime + 落到 files/attachments/ 的文件名。 */
@Serializable
data class Attachment(
    val name: String,
    val mime: String,
    val file: String,
)

@Serializable
data class ChatMessage(
    val role: Role,
    val content: String,
    val ts: Long = System.currentTimeMillis(),
    val error: String? = null,
    /** 图片文件名（存 files/images/，消息里不塞 base64） */
    val images: List<String> = emptyList(),
    /** 非图片附件（PDF / 文本等） */
    val attachments: List<Attachment> = emptyList(),
)

@Serializable
data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
)

/**
 * 本地存储：P2 阶段先用单文件 JSON，够个人用且零依赖。
 * 会话多了再换 Room（蓝本里预留了位置）。
 */
class SettingsStore(private val context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val file: File get() = File(AppDirs.userRoot(context), "settings.json")

    fun load(): ModelConfig = runCatching {
        if (!file.exists()) ModelConfig()
        else json.decodeFromString<ModelConfig>(file.readText())
    }.getOrDefault(ModelConfig())

    fun save(config: ModelConfig) {
        runCatching { file.writeText(json.encodeToString(ModelConfig.serializer(), config)) }
    }
}

private val PINNED_FIRST = compareByDescending<Conversation> { it.pinned }
    .thenByDescending { it.updatedAt }

class ConversationStore(private val context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val file: File get() = File(AppDirs.userRoot(context), "conversations.json")

    private fun readAll(): List<Conversation> = runCatching {
        if (!file.exists()) emptyList()
        else json.decodeFromString<List<Conversation>>(file.readText())
    }.getOrDefault(emptyList())

    fun loadAll(): List<Conversation> = synchronized(LOCK) { readAll() }.sortedWith(PINNED_FIRST)

    fun saveAll(list: List<Conversation>) {
        // ⚠️ 整个会话库都在这一个 JSON 里：先写临时文件再 rename，进程在写一半时被杀
        // 也不会留下半截文件（半截 = loadAll 解析失败 = 所有会话看起来都没了）。
        // 另外所有写操作串行化：流式期间 persistPartial 在 IO 线程写，stop()/回桌面
        // 的 persist 在主线程写，并发读改写会丢会话。
        synchronized(LOCK) { writeLocked(list) }
    }

    private fun writeLocked(list: List<Conversation>) {
        runCatching {
            val dir = AppDirs.userRoot(context)
            val tmp = File(dir, "conversations.json.tmp")
            tmp.writeText(
                json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(Conversation.serializer()),
                    list.sortedWith(PINNED_FIRST),
                )
            )
            if (!tmp.renameTo(file)) {
                // 极少数文件系统 rename 失败：退回直接写（内容已经算好了）
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    fun upsert(conv: Conversation) {
        synchronized(LOCK) {
            val all = readAll().toMutableList()
            val i = all.indexOfFirst { it.id == conv.id }
            if (i >= 0) all[i] = conv else all.add(0, conv)
            writeLocked(all)
        }
    }

    fun delete(id: String) {
        synchronized(LOCK) { writeLocked(readAll().filterNot { it.id == id }) }
    }

    private companion object {
        /** 进程级锁：不同 ChatViewModel / 不同线程共享同一个会话文件。 */
        val LOCK = Any()
    }
}
