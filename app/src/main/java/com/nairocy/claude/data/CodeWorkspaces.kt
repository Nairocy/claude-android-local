package com.nairocy.claude.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** 一个工作区：名字 + 目录 id（目录在 `files/users/<uid>/workspaces/<id>/`）。 */
@Serializable
data class CodeWorkspaceMeta(
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * 工作区清单（`code_workspaces.json`）。
 *
 * 目录约定：`files/users/<uid>/workspaces/<id>/`。工作区之间**完全独立** ——
 * 模型只能读写当前工作区，预览也只认当前工作区。
 *
 * 老版本（v0.17.0）只有一个固定目录 `files/users/<uid>/workspace/`，
 * 首次启动时整体搬进第一个工作区，别让用户已写的东西消失。
 */
class CodeWorkspaceStore(private val context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val file: File get() = File(AppDirs.userRoot(context), "code_workspaces.json")

    fun loadAll(): List<CodeWorkspaceMeta> = runCatching {
        if (!file.exists()) emptyList()
        else json.decodeFromString(ListSerializer(CodeWorkspaceMeta.serializer()), file.readText())
    }.getOrDefault(emptyList()).sortedBy { it.createdAt }

    fun saveAll(list: List<CodeWorkspaceMeta>) {
        runCatching {
            val dir = AppDirs.userRoot(context)
            val tmp = File(dir, "code_workspaces.json.tmp")
            tmp.writeText(json.encodeToString(ListSerializer(CodeWorkspaceMeta.serializer()), list))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    /**
     * 首次拿清单：空清单就建一个默认工作区，并把老版本的 `workspace/` 目录整体搬进来。
     */
    fun ensure(): List<CodeWorkspaceMeta> {
        val existing = loadAll()
        if (existing.isNotEmpty()) return existing
        val meta = CodeWorkspaceMeta(id = "w" + System.currentTimeMillis().toString(36), name = "我的工作区")
        saveAll(listOf(meta))
        migrateLegacy(meta.id)
        return listOf(meta)
    }

    private fun migrateLegacy(newId: String) {
        val legacy = File(AppDirs.userRoot(context), "workspace")
        if (!legacy.isDirectory) return
        val target = dirOf(context, newId)
        target.mkdirs()
        legacy.listFiles()?.forEach { f ->
            runCatching { f.renameTo(File(target, f.name)) }
        }
        runCatching { legacy.delete() }
    }

    fun create(name: String): CodeWorkspaceMeta {
        val meta = CodeWorkspaceMeta(
            id = "w" + UUID.randomUUID().toString().take(8),
            name = name.trim().ifBlank { "未命名工作区" }.take(24),
        )
        saveAll(loadAll() + meta)
        dirOf(context, meta.id).mkdirs()
        return meta
    }

    fun rename(id: String, name: String) {
        val n = name.trim().take(24)
        if (n.isEmpty()) return
        saveAll(loadAll().map { if (it.id == id) it.copy(name = n) else it })
    }

    /** 删工作区：连同目录一起删（调用方要先确认，这里不做二次确认）。 */
    fun delete(id: String) {
        val rest = loadAll().filterNot { it.id == id }
        saveAll(rest)
        runCatching { dirOf(context, id).deleteRecursively() }
    }

    fun find(id: String): CodeWorkspaceMeta? = loadAll().firstOrNull { it.id == id }

    companion object {
        /** 工作区目录：`files/users/<uid>/workspaces/<id>/`。 */
        fun dirOf(context: Context, id: String): File =
            File(AppDirs.userRoot(context), "workspaces/$id").apply { mkdirs() }
    }
}
