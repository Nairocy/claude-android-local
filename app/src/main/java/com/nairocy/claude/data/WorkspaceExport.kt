package com.nairocy.claude.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 工作区导出：单文件 / 整包 zip。
 *
 * 全部先复制到 `cache/export/`，再用 FileProvider 交给系统分享面板 —— 用户在那儿选
 * 「保存到文件 / 发给自己 / 其他 App」，导出到本地由系统负责，App 不申请存储权限。
 * （file_paths.xml 已经加了 cache-path export/，缺那条会抛 IllegalArgumentException。）
 */
object WorkspaceExport {

    private fun exportDir(ctx: Context): File =
        File(ctx.cacheDir, "export").apply { mkdirs() }

    /** 单文件导出，返回分享出去的那个副本（失败抛异常）。 */
    fun shareFile(ctx: Context, root: File, path: String): File {
        val src = CodeWorkspace.resolve(root, path)
        require(src.isFile) { "找不到文件：$path" }
        val name = src.name.ifBlank { "file" }
        val out = File(exportDir(ctx), name)
        src.copyTo(out, overwrite = true)
        share(ctx, out, CodeWorkspace.mimeOf(path).substringBefore(';').trim())
        return out
    }

    /**
     * 整个工作区打成 zip 导出。
     * 只装文件（空目录忽略），路径保持工作区内的相对结构，顶层套一层工作区名。
     */
    fun shareZip(ctx: Context, root: File, workspaceName: String): File {
        val files = CodeWorkspace.list(root)
        require(files.isNotEmpty()) { "工作区是空的" }
        val safeName = workspaceName.trim().ifBlank { "workspace" }
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val out = File(exportDir(ctx), "$safeName.zip")
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            files.forEach { f ->
                val src = File(root, f.path.trimStart('/'))
                if (!src.isFile) return@forEach
                zip.putNextEntry(ZipEntry("$safeName/${f.path.trimStart('/')}"))
                src.inputStream().buffered().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        share(ctx, out, "application/zip")
        return out
    }

    private fun share(ctx: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime.ifBlank { "application/octet-stream" }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "导出").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
