package com.nairocy.claude.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 聊天图片：从相册导入时压到长边 1568px、JPEG 88，落到 files/images/<name>.jpg。
 * 消息里只存文件名，发请求时再转成 data URL（base64）。
 */
class ImageStore(private val context: Context) {

    private val dir: File get() = AppDirs.images(context)

    fun pathOf(name: String): File = File(dir, name)

    fun import(uri: Uri): String? = runCatching {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        val bitmap = input.use { BitmapFactory.decodeStream(it) } ?: return null
        val scaled = if (maxOf(bitmap.width, bitmap.height) > MAX_EDGE) {
            val ratio = MAX_EDGE.toFloat() / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }
        val name = UUID.randomUUID().toString().take(8) + ".jpg"
        FileOutputStream(File(dir, name)).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        if (scaled !== bitmap) bitmap.recycle()
        name
    }.getOrNull()

    /** 给 OpenAI 兼容接口用的 data URL；读不到返回 null。 */
    fun dataUrl(name: String): String? = runCatching {
        val f = pathOf(name)
        if (!f.exists()) return null
        "data:image/jpeg;base64," + Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
    }.getOrNull()

    private companion object {
        const val MAX_EDGE = 1568
    }
}
