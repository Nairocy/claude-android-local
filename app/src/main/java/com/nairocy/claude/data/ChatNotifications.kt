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
 * 生成过程通知（设置 → 通知 → 「回复」控制）。
 *
 * - 生成中：一条**常驻**通知，先从「Claude 正在思索…」变成「Claude 正在输出…（已输出 N 字）」，
 *   低优先级、不响不震、只更新一次（`setOnlyAlertOnce` + `setSilent`）。
 * - 写完后：撤掉常驻条，发一条可点的「会话「标题」已完成」，点开直接落到那条会话。
 *
 * 两个前提都满足才发：`Prefs.notifyReplies` 开着，且系统通知权限给了
 * （Android 13+ 的 POST_NOTIFICATIONS；`areNotificationsEnabled()` 会一并反映）。
 * 无痕对话一律不发 —— 既然不落盘，也不该在通知栏留字。
 */
object ChatNotifications {

    /** 点通知落到哪条会话。MainActivity 的 onCreate / onNewIntent 读它。 */
    const val EXTRA_CONV = "com.nairocy.claude.extra.CONVERSATION"

    private const val CH_PROGRESS = "claude_progress"
    private const val CH_DONE = "claude_done"
    private const val ID_PROGRESS = 4101
    private const val ID_DONE = 4102

    /** 回复开关 + 系统通知权限，缺一个就整条链路静默。 */
    fun enabled(context: Context): Boolean =
        Prefs(context).notifyReplies && NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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

    private fun openConversation(context: Context, convId: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            // singleTop + SINGLE_TOP：前台时走 onNewIntent，不在任务栈里叠第二个 Activity
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!convId.isNullOrBlank()) putExtra(EXTRA_CONV, convId)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 常驻进度条：思考中 / 输出中原地更新同一条通知。 */
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
            .setContentIntent(openConversation(context, convId, 1))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_PROGRESS, n) }
    }

    fun cancelProgress(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(ID_PROGRESS) }
    }

    /** 写完：撤常驻条，发一条可点的完成通知。 */
    fun done(context: Context, convTitle: String, reply: String, convId: String) {
        cancelProgress(context)
        if (!enabled(context)) return
        ensureChannels(context)
        val preview = reply.replace(Regex("\\s+"), " ").trim().take(90)
            .ifBlank { "点开查看完整回复" }
        val n = NotificationCompat.Builder(context, CH_DONE)
            .setSmallIcon(R.drawable.ic_notify_small)
            .setContentTitle("会话「$convTitle」已完成")
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openConversation(context, convId, 2))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_DONE, n) }
    }

    /** 中断（出错 / 停止 / 被切会话）：只撤常驻条，不发「已完成」。 */
    fun interrupted(context: Context) {
        cancelProgress(context)
    }
}
