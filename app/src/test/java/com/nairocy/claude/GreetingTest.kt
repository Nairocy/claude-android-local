package com.nairocy.claude

import com.nairocy.claude.ui.home.Greeting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 问候语分档（用户 2026-10-28 拍板：8–9 早上好 / 11–12 日安 / 14–15 下午好 / 19–20 夜安，其余随机） */
class GreetingTest {

    private val day = LocalDate.of(2026, 10, 28)

    @Test
    fun morningSlot() {
        assertEquals("早上好", Greeting.prefixFor(8, day))
        assertEquals("早上好", Greeting.prefixFor(9, day))
    }

    @Test
    fun noonSlot() {
        assertEquals("日安", Greeting.prefixFor(11, day))
        assertEquals("日安", Greeting.prefixFor(12, day))
    }

    @Test
    fun afternoonSlot() {
        assertEquals("下午好", Greeting.prefixFor(14, day))
        assertEquals("下午好", Greeting.prefixFor(15, day))
    }

    @Test
    fun nightSlot() {
        assertEquals("夜安", Greeting.prefixFor(19, day))
        assertEquals("夜安", Greeting.prefixFor(20, day))
    }

    @Test
    fun otherHoursFallBackToCasualWords() {
        for (h in listOf(0, 6, 7, 10, 13, 16, 17, 18, 21, 22, 23)) {
            val prefix = Greeting.prefixFor(h, day)
            assertTrue("hour=$h got=$prefix", prefix in Greeting.CASUAL)
        }
    }

    @Test
    fun casualWordIsStableWithinTheSameDay() {
        val a = Greeting.prefixFor(16, day)
        val b = Greeting.prefixFor(17, day)
        assertEquals("同一天应固定同一个随机词", a, b)
    }

    @Test
    fun greetingIncludesName() {
        assertEquals("早上好，你", Greeting.forHour(8, "你", day))
        assertEquals("日安，你", Greeting.forHour(11, "", day))
    }
}
