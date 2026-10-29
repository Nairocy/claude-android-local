package com.nairocy.claude.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nairocy.claude.MainActivity
import com.nairocy.claude.R

/**
 * Code 页的通知（与对话页分开一套 id，互不覆盖）：
 *
 * - 生成中：常驻进度条（「Claude Code 正在思索… / 正在输出… · 已输出 N 字」），
 *   由 [CodeForegroundService] 的 service 通知保命 + 这条文案通知两条并存；
 * - 写完：撤进度条，发一条可点的「Code 会话「X」已完成」，点开落到那条 Code 会话。
 *
 * 门禁与对话页一致：`Prefs.notifyReplies` + 系统通知权限，缺一个就静默。
 */
object CodeNotifications {

    /** 点通知落到哪条 Code 会话；MainActivity 读它。 */
    const val EXTRA_CODE_CONV = "com.nairocy.claude.extra.CODE_CONVERSATION"

    private const val CH_PROGRESS = "claude_progress"
    private const val CH_DONE = "claude_done"
    private const val ID_PROGRESS = 4201
    private const val ID_DONE = 4202

    fun enabled(context: Context): Boolean =
        Prefs(context).notifyReplies && NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // 频道与对话页共用（用户设置里不该出现两组一样的开关），id 不同所以互不覆盖
        if (nm.getNotificationChannel(CH_PROGRESS) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_PROGRESS, "Claude 正在生成", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Claude 思考与输出时的常驻进度"
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                }
            )
        }
        if (nm.getNotificationChannel(CH_DONE) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_DONE, "回复完成", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Claude 把回复写完后提醒"
                }
            )
        }
    }

    private fun openConversation(context: Context, convId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!convId.isNullOrBlank()) putExtra(EXTRA_CODE_CONV, convId)
        }
        return PendingIntent.getActivity(
            context, 4, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun progress(context: Context, title: String, body: String, convId: String?) {
        if (!enabled(context)) {
            cancelProgress(context)
            return
        }
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CH_PROGRESS)
            .setSmallIcon(R.drawable.ic_notify_small)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openConversation(context, convId))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_PROGRESS, n) }
    }

    fun cancelProgress(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(ID_PROGRESS) }
    }

    fun done(context: Context, title: String, reply: String, convId: String) {
        cancelProgress(context)
        if (!enabled(context)) return
        ensureChannels(context)
        val preview = reply.replace(Regex("\\s+"), " ").trim().take(90)
            .ifBlank { "点开查看完整回复" }
        val n = NotificationCompat.Builder(context, CH_DONE)
            .setSmallIcon(R.drawable.ic_notify_small)
            .setContentTitle("Code 会话「$title」已完成")
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openConversation(context, convId))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_DONE, n) }
    }

    fun interrupted(context: Context) {
        cancelProgress(context)
    }
}
