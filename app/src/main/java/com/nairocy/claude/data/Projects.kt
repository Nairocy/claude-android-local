package com.nairocy.claude.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** 本机项目：一组对话的归类，数据只在本机。 */
@Serializable
data class Project(
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val conversationIds: List<String> = emptyList(),
)

class ProjectStore(private val context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val file: File get() = File(AppDirs.userRoot(context), "projects.json")

    fun loadAll(): List<Project> = runCatching {
        if (!file.exists()) emptyList()
        else json.decodeFromString(ListSerializer(Project.serializer()), file.readText())
    }.getOrDefault(emptyList()).sortedByDescending { it.createdAt }

    fun saveAll(list: List<Project>) {
        runCatching {
            file.writeText(json.encodeToString(ListSerializer(Project.serializer()), list))
        }
    }

    fun create(name: String): Project {
        val p = Project(id = UUID.randomUUID().toString(), name = name.trim())
        saveAll(loadAll() + p)
        return p
    }

    fun rename(id: String, name: String) {
        saveAll(loadAll().map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    fun delete(id: String) {
        saveAll(loadAll().filterNot { it.id == id })
    }

    /** 把对话塞进项目（已在里面就忽略）。 */
    fun addConversation(projectId: String, conversationId: String) {
        saveAll(loadAll().map {
            if (it.id == projectId && conversationId !in it.conversationIds) {
                it.copy(conversationIds = it.conversationIds + conversationId)
            } else it
        })
    }

    fun removeConversation(projectId: String, conversationId: String) {
        saveAll(loadAll().map {
            if (it.id == projectId) it.copy(conversationIds = it.conversationIds - conversationId) else it
        })
    }

    /** 某个对话所属的项目名（可能没有）。 */
    fun projectOf(conversationId: String): Project? =
        loadAll().firstOrNull { conversationId in it.conversationIds }
}
