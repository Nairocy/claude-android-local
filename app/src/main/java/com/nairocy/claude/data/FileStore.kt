package com.nairocy.claude.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** 非图片附件：只存文件名与原始文件名，正文/PDF 在发请求时才处理。 */
class FileStore(private val context: Context) {

    private val dir: File get() = AppDirs.attachments(context)
    private val imageDir: File get() = AppDirs.images(context)

    fun import(uri: Uri): Attachment? = runCatching {
        val name = queryName(uri) ?: "file"
        val mime = context.contentResolver.getType(uri) ?: mimeFromName(name)
        val ext = name.substringAfterLast('.', "").lowercase()
        val stored = UUID.randomUUID().toString().take(8) + if (ext.isNotEmpty()) ".$ext" else ""
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(File(dir, stored)).use { out -> input.copyTo(out) }
        } ?: return null
        Attachment(name = name, mime = mime, file = stored)
    }.getOrNull()

    fun pathOf(att: Attachment): File = File(dir, att.file)

    fun isTextLike(att: Attachment): Boolean {
        if (att.mime.startsWith("text/")) return true
        if (att.mime in setOf("application/json", "application/xml", "application/javascript")) return true
        val n = att.name.lowercase()
        return listOf(
            ".md", ".markdown", ".txt", ".csv", ".json", ".xml", ".yaml", ".yml", ".html", ".htm",
            ".css", ".js", ".ts", ".kt", ".java", ".py", ".c", ".cpp", ".h", ".sh", ".log", ".ini", ".conf",
        ).any { n.endsWith(it) }
    }

    fun isPdf(att: Attachment): Boolean =
        att.mime == "application/pdf" || att.name.lowercase().endsWith(".pdf")

    fun isImage(att: Attachment): Boolean = att.mime.startsWith("image/")

    /** 读文本附件，超过 12k 字截断（避免一次把上下文塞爆）。 */
    fun readText(att: Attachment, limit: Int = 12_000): String? = runCatching {
        val f = pathOf(att)
        if (!f.exists() || f.length() > 2_000_000) return null
        f.readText().take(limit)
    }.getOrNull()

    /** PDF 前 maxPages 页渲染成 JPEG（存 images/），返回文件名列表。 */
    fun pdfToImageNames(att: Attachment, maxPages: Int = 3): List<String> = runCatching {
        val f = pathOf(att)
        if (!f.exists()) return emptyList()
        val names = mutableListOf<String>()
        ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val pages = minOf(renderer.pageCount, maxPages)
                for (i in 0 until pages) {
                    renderer.openPage(i).use { page ->
                        val scale = 2f
                        val bmp = Bitmap.createBitmap(
                            (page.width * scale).toInt().coerceAtLeast(1),
                            (page.height * scale).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888,
                        )
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val name = "pdf_" + UUID.randomUUID().toString().take(8) + ".jpg"
                        FileOutputStream(File(imageDir, name)).use { out ->
                            bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        }
                        bmp.recycle()
                        names += name
                    }
                }
            }
        }
        names
    }.getOrDefault(emptyList())

    private fun queryName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    private fun mimeFromName(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "json" -> "application/json"
        "md", "markdown", "txt", "csv", "log" -> "text/plain"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        else -> "application/octet-stream"
    }
}
