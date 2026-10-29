package com.nairocy.claude.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** 一条记忆主题（对应官方「记忆文件」里的一行 / 一个可点开的条目）。 */
@Serializable
data class MemoryTopic(
    val id: String,
    val title: String,
    val content: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * 记忆主题存储：`files/memory_topics.json`。
 *
 * 迁移：老版本只有一个 `files/memory.md`，首次读到空列表且旧文件非空时，
 * 自动包成一条标题为「记忆」的主题（旧文件保留不删）。
 */
class MemoryStore(context: Context) {

    private val appContext = context.applicationContext
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val file: File get() = File(AppDirs.userRoot(appContext), "memory_topics.json")
    private val legacy: File get() = File(AppDirs.userRoot(appContext), "memory.md")

    fun loadAll(): List<MemoryTopic> {
        val list = runCatching {
            if (file.exists()) json.decodeFromString<List<MemoryTopic>>(file.readText()) else emptyList()
        }.getOrDefault(emptyList())
        if (list.isNotEmpty()) return list.sortedByDescending { it.updatedAt }

        val old = runCatching { if (legacy.exists()) legacy.readText().trim() else "" }.getOrDefault("")
        if (old.isBlank()) return emptyList()
        val migrated = MemoryTopic(id = UUID.randomUUID().toString(), title = "记忆", content = old)
        saveAll(listOf(migrated))
        return listOf(migrated)
    }

    fun saveAll(list: List<MemoryTopic>) {
        runCatching { file.writeText(json.encodeToString(list)) }
    }

    fun create(title: String, content: String): MemoryTopic {
        val topic = MemoryTopic(
            id = UUID.randomUUID().toString(),
            title = title.trim().ifBlank { "新记忆" },
            content = content.trim(),
        )
        saveAll(loadAll() + topic)
        return topic
    }

    fun update(topic: MemoryTopic) {
        saveAll(loadAll().map { if (it.id == topic.id) topic.copy(updatedAt = System.currentTimeMillis()) else it })
    }

    fun delete(id: String) {
        saveAll(loadAll().filterNot { it.id == id })
    }

    /** 注入系统提示用：把所有主题拼成一段文本。 */
    fun allText(): String = loadAll()
        .joinToString("\n\n") { "${it.title}\n${it.content}" }
        .trim()
}
