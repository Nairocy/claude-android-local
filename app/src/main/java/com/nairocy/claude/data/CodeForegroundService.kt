package com.nairocy.claude.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nairocy.claude.MainActivity
import com.nairocy.claude.R

/**
 * Code 页的前台服务：**只干一件事 —— 让进程在后台不被冻结/回收**。
 *
 * 为什么需要它：Code 的流式请求跑在进程里，用户按 Home 回桌面后，国产 ROM 会把「后台无前台服务」的
 * 进程冻住甚至杀掉，OkHttp 的长连接当场断（用户实测：退回桌面 API 就中断）。一条普通通知挡不住，
 * 必须是 `startForeground`（Android 14 起要声明 `foregroundServiceType=dataSync`）。
 *
 * 通知的文案由 [CodeNotifications] 更新（同一个 id 原地刷新），服务本身不关心进度。
 */
class CodeForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(ID, buildNotification())
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        ensureChannel()
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 3, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_notify_small)
            .setContentTitle("Claude Code 正在工作")
            .setContentText("回到 App 可以看到进度")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CH) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH, "Claude Code 后台运行", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "在后台保持 Code 页的生成不中断"
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                }
            )
        }
    }

    override fun onDestroy() {
        stopForegroundCompat()
        super.onDestroy()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        const val CH = "claude_code_job"
        const val ID = 4200
        const val ACTION_STOP = "com.nairocy.claude.CODE_STOP"

        /** 流开始时调（必须在 App 前台时调，Android 12+ 不许从后台起前台服务）。 */
        fun start(ctx: Context) {
            runCatching {
                ContextCompat.startForegroundService(ctx, Intent(ctx, CodeForegroundService::class.java))
            }
        }

        /**
         * 收尾：用 `stopService` 而不是 startService(ACTION_STOP) —— 流可能在 App 处于后台时结束，
         * Android 8+ 从后台 startService 会直接抛 IllegalStateException，服务就一直挂着。
         */
        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, CodeForegroundService::class.java)) }
        }
    }
}
