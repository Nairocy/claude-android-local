package com.nairocy.claude.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 设备上下文：当前时间 / 位置 / 今天的日程。
 *
 * - 时间每次提问现算（不缓存）；
 * - 位置与日程读系统数据，结果按天缓存在 [Prefs] 里，避免每个 token 都去查 ContentProvider；
 * - 没有权限或读不到就**什么都不注入**，不编造。
 */
object ContextInfo {

    private val WEEK = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

    private val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    /** 还缺哪些运行时权限（位置优先，日历其次）。 */
    fun missingPermissions(context: Context): List<String> {
        val out = mutableListOf<String>()
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            out += Manifest.permission.ACCESS_COARSE_LOCATION
            out += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            out += Manifest.permission.READ_CALENDAR
        }
        return out
    }

    fun hasLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun hasCalendar(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /** 抓一次位置 + 读今天的日程，写进 prefs（IO 线程调用）。 */
    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        val prefs = Prefs(context)
        val today = LocalDate.now().toString()

        if (hasLocation(context)) {
            val text = runCatching { readLocation(context) }.getOrNull()
            if (!text.isNullOrBlank()) {
                prefs.locText = text
                prefs.locAt = System.currentTimeMillis()
            }
        } else {
            prefs.clearLocation()
        }

        if (hasCalendar(context)) {
            prefs.calText = runCatching { readTodayEvents(context) }.getOrNull() ?: ""
            prefs.calDay = today
        } else {
            prefs.clearCalendar()
        }
    }

    /** 拼进系统提示的上下文块；没有任何可注入内容时返回空串。 */
    fun promptBlock(context: Context): String {
        val prefs = Prefs(context)
        val now = java.time.LocalDateTime.now()
        val zone = ZoneId.systemDefault().id

        val sb = StringBuilder()
        sb.append("当前时间：")
        sb.append(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
        sb.append(" ").append(WEEK[(now.dayOfWeek.value + 6) % 7])
        sb.append(" ").append(now.format(TIME_FMT))
        sb.append("（").append(zone).append("）")

        val loc = prefs.locText.trim()
        if (loc.isNotEmpty()) sb.append("\n当前位置：").append(loc)

        // 日程只在“今天读的”前提下使用，隔天自动失效
        if (prefs.calDay == now.toLocalDate().toString()) {
            val cal = prefs.calText.trim()
            if (cal.isNotEmpty()) sb.append("\n今天的日程：\n").append(cal)
        }
        return sb.toString()
    }

    /* ---------------- 位置 ---------------- */

    private fun readLocation(context: Context): String? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        var best: android.location.Location? = null
        for (p in providers) {
            val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
            if (best == null || loc.time > best!!.time) best = loc
        }
        val loc = best ?: return null

        val lat = loc.latitude
        val lon = loc.longitude
        val coords = String.format(Locale.US, "%.4f, %.4f", lat, lon)
        if (!Geocoder.isPresent()) return coords

        val geocoder = Geocoder(context, Locale.CHINA)
        val place = runCatching { geocoder.getFromLocation(lat, lon, 1)?.firstOrNull() }.getOrNull()
            ?: return coords
        val parts = listOfNotNull(
            place.locality ?: place.subAdminArea,
            place.adminArea,
            place.countryName,
        ).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return if (parts.isEmpty()) coords else parts.joinToString(" ")
    }

    /* ---------------- 日历 ---------------- */

    private fun readTodayEvents(context: Context, limit: Int = 20): String {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val end = start + 24 * 60 * 60 * 1000L - 1
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(start.toString())
            .appendPath(end.toString())
            .build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ALL_DAY,
        )
        val lines = mutableListOf<String>()
        context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            CalendarContract.Instances.BEGIN + " ASC",
        )?.use { c ->
            val iTitle = c.getColumnIndex(CalendarContract.Instances.TITLE)
            val iBegin = c.getColumnIndex(CalendarContract.Instances.BEGIN)
            val iEnd = c.getColumnIndex(CalendarContract.Instances.END)
            val iLoc = c.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
            val iAllDay = c.getColumnIndex(CalendarContract.Instances.ALL_DAY)
            while (c.moveToNext() && lines.size < limit) {
                val title = c.getString(iTitle)?.trim().orEmpty().ifBlank { "（无标题）" }
                val allDay = iAllDay >= 0 && c.getInt(iAllDay) == 1
                val whenText = if (allDay) {
                    "全天"
                } else {
                    val b = Instant.ofEpochMilli(c.getLong(iBegin)).atZone(zone).toLocalTime()
                    val e = Instant.ofEpochMilli(c.getLong(iEnd)).atZone(zone).toLocalTime()
                    "${b.format(TIME_FMT)}–${e.format(TIME_FMT)}"
                }
                val place = if (iLoc >= 0) c.getString(iLoc)?.trim().orEmpty() else ""
                lines += if (place.isEmpty()) "- $whenText $title" else "- $whenText $title（$place）"
            }
        }
        return lines.joinToString("\n")
    }

    /** 给「今天」这个词用的午夜时间点（测试与调试用）。 */
    fun startOfTodayMillis(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** 当前小时（0–23），问候语按它分档。 */
    fun currentHour(): Int = LocalTime.now().hour
}
