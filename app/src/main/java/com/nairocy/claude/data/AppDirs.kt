package com.nairocy.claude.data

import android.content.Context
import java.io.File

/**
 * 按账号隔离的数据目录：`files/users/<uid>/`。
 *
 * 会话、图片、附件、记忆、项目、模型设置都在账号目录下；退出登录不删，
 * 换账号互不可见。未登录期间用 `files/users/_local/`（登录页不需要数据）。
 */
object AppDirs {

    private const val LOCAL_UID = "_local"

    /** 当前账号的数据根目录。 */
    fun userRoot(context: Context): File {
        val uid = runCatching { AccountStore(context).uid }.getOrDefault("")
        val name = if (uid.isBlank()) LOCAL_UID else uid
        return File(context.filesDir, "users/$name").apply { mkdirs() }
    }

    fun images(context: Context): File = File(userRoot(context), "images").apply { mkdirs() }

    fun attachments(context: Context): File = File(userRoot(context), "attachments").apply { mkdirs() }

    /** Code 页的静态网页工作区（agent 直接读写这里，预览也读这里）。 */
    fun workspace(context: Context): File = File(userRoot(context), "workspace").apply { mkdirs() }

    /** 头像本地缓存（每个账号一份），服务端 avatarVer 变了就覆盖。 */
    fun avatar(context: Context): File = File(userRoot(context), "avatar.jpg")

    /**
     * 老版本的数据散落在 `files/` 根下；首个账号首次登录时整体搬进它的目录，
     * 只搬一次（`files/users/.legacy_assigned` 作标记）。
     */
    fun adoptLegacyData(context: Context, uid: String) {
        if (uid.isBlank()) return
        val root = context.filesDir
        val marker = File(root, "users/.legacy_assigned")
        if (marker.exists()) return
        val target = File(root, "users/$uid").apply { mkdirs() }

        for (name in listOf(
            "conversations.json", "settings.json", "projects.json", "memory_topics.json", "memory.md",
        )) {
            val src = File(root, name)
            if (!src.exists()) continue
            runCatching {
                val dst = File(target, name)
                if (!dst.exists()) src.copyTo(dst, overwrite = true)
                src.delete()
            }
        }
        for (dir in listOf("images", "attachments")) {
            val src = File(root, dir)
            if (!src.isDirectory) continue
            val dstDir = File(target, dir).apply { mkdirs() }
            src.listFiles()?.forEach { f -> runCatching { f.renameTo(File(dstDir, f.name)) } }
            runCatching { src.delete() }
        }
        runCatching {
            marker.parentFile?.mkdirs()
            marker.writeText(uid)
        }
    }
}
