package com.nairocy.claude.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 头像：相册选图 → 居中裁方 → 256×256 JPEG(88) → 上传；本地也留一份用于显示。
 * 服务端 `avatarVer` 变化时由 Activity 重新下载覆盖。
 */
object AvatarStore {

    const val SIZE = 256

    /** 读相册 → 正方形裁剪 + 缩放 + JPEG 字节；失败返回 null。 */
    fun encode(context: Context, uri: Uri): ByteArray? = runCatching {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        val bitmap = input.use { BitmapFactory.decodeStream(it) } ?: return null
        val side = minOf(bitmap.width, bitmap.height)
        val left = (bitmap.width - side) / 2
        val top = (bitmap.height - side) / 2
        val cropped = Bitmap.createBitmap(bitmap, left, top, side, side)
        val scaled = Bitmap.createScaledBitmap(cropped, SIZE, SIZE, true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
        if (scaled !== cropped) scaled.recycle()
        if (cropped !== bitmap) cropped.recycle()
        bitmap.recycle()
        out.toByteArray()
    }.getOrNull()

    fun saveLocal(context: Context, jpeg: ByteArray): File? = runCatching {
        val f = AppDirs.avatar(context)
        f.writeBytes(jpeg)
        f
    }.getOrNull()

    fun loadLocal(context: Context): Bitmap? = runCatching {
        val f = AppDirs.avatar(context)
        if (!f.exists() || f.length() == 0L) null else BitmapFactory.decodeFile(f.path)
    }.getOrNull()

    fun clearLocal(context: Context) {
        runCatching { AppDirs.avatar(context).delete() }
    }
}
