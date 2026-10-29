package com.nairocy.claude.data

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 轻量触感反馈。
 *
 * - **全部尊重设置页「触感反馈」开关**（`Prefs.haptic`）：关掉之后任何调用都是空操作，
 *   所以调用点不用自己判断，直接 `Haptics.tap(ctx)` 即可。
 * - 三档强度，别再加第四档（官方式样只有这三种手感）：
 *   - [tap]  按钮轻点，8ms 极短；
 *   - [send] 发送消息，比 tap 稍重；
 *   - [done] 模型回答完成，两下短震（第二下更轻）。
 * - API 26 起 `VibrationEffect` 可用；API 31 起 Vibrator 必须从 `VibratorManager` 取，
 *   直接 `getSystemService(VIBRATOR_SERVICE)` 会返回 null。
 */
object Haptics {

    /** 触感强度。调用点用 [play] 传这个枚举，不要自己 if 判断。 */
    enum class Kind { NONE, TAP, SEND, DONE }

    /** 按种类触发；[Kind.NONE] 静默（比如禁用态）。 */
    fun play(ctx: Context, kind: Kind) {
        when (kind) {
            Kind.NONE -> Unit
            Kind.TAP -> tap(ctx)
            Kind.SEND -> send(ctx)
            Kind.DONE -> done(ctx)
        }
    }

    /** 按钮轻点。 */
    fun tap(ctx: Context) = play(ctx, longArrayOf(0, 8), intArrayOf(0, 55))

    /** 发送消息。 */
    fun send(ctx: Context) = play(ctx, longArrayOf(0, 16), intArrayOf(0, 100))

    /** 模型回答完成：两下短震，收回时轻一些。 */
    fun done(ctx: Context) = play(ctx, longArrayOf(0, 12, 62, 10), intArrayOf(0, 110, 0, 80))

    private fun play(ctx: Context, timings: LongArray, amplitudes: IntArray) {
        if (!Prefs.isHapticEnabled(ctx)) return
        val app = ctx.applicationContext
        val v = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                    .defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        }.getOrNull() ?: return
        if (!v.hasVibrator()) return
        // 波形里 amplitude 0 表示停顿时长，长度必须与 timings 对齐
        runCatching {
            v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        }
    }
}
