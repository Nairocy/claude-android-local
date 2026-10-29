package com.nairocy.claude.ui.home

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * 首页问候语。
 *
 * 时段档（用户 2026-10-28 拍板）：
 *  8–9   早上好
 *  11–12 日安
 *  14–15 下午好
 *  19–20 夜安
 * 其余时段回到普通欢迎语，三种随机（**按天固定**，避免每次重组都换词）。
 */
object Greeting {

    val CASUAL = listOf("嗨", "别来无恙", "构建你的工作")

    fun prefixFor(hour: Int, date: LocalDate = LocalDate.now()): String = when (hour) {
        in 8..9 -> "早上好"
        in 11..12 -> "日安"
        in 14..15 -> "下午好"
        in 19..20 -> "夜安"
        else -> CASUAL[Random(date.toEpochDay()).nextInt(CASUAL.size)]
    }

    fun forMoment(now: LocalDateTime, name: String): String =
        "${prefixFor(now.hour, now.toLocalDate())}，${name.ifBlank { "你" }}"

    fun forHour(hour: Int, name: String, date: LocalDate = LocalDate.now()): String =
        "${prefixFor(hour, date)}，${name.ifBlank { "你" }}"
}
