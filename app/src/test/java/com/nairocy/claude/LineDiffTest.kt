package com.nairocy.claude

import com.nairocy.claude.data.lineDiff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 行 diff（Code 页工具卡的「+ / -」行）。
 *
 * 这几点最容易改坏：行号归属（`+` 是新文件、`-` 是旧文件）、同段先删后加、
 * 结尾换行不多算一行、超长文件退化。改完先跑这里，别上真机猜。
 */
class LineDiffTest {

    private fun ops(list: List<com.nairocy.claude.data.CodeDiffLine>) =
        list.map { "${it.op}${it.no}:${it.text}" }

    @Test
    fun identicalTextHasNoDiff() {
        assertTrue(lineDiff("", "").isEmpty())
        assertTrue(lineDiff("a\nb\nc", "a\nb\nc").isEmpty())
        // 结尾换行不算多一行
        assertTrue(lineDiff("a\nb\n", "a\nb\n").isEmpty())
    }

    @Test
    fun insertReportsPlusLineWithNewNumber() {
        val d = lineDiff("a\nc", "a\nb\nc")
        assertEquals(listOf("+2:b"), ops(d))
    }

    @Test
    fun deleteReportsMinusLineWithOldNumber() {
        val d = lineDiff("a\nb\nc", "a\nc")
        assertEquals(listOf("-2:b"), ops(d))
    }

    @Test
    fun replacePutsDeleteBeforeAdd() {
        val d = lineDiff("a\nb\nc", "a\nx\nc")
        assertEquals(listOf("-2:b", "+2:x"), ops(d))
    }

    @Test
    fun multipleHunksKeepTheirOwnLineNumbers() {
        val old = "1\n2\n3\n4\n5"
        val new = "1\nX\n3\n4\nY"
        // 两个独立改动点：第 2 行替换、第 5 行替换
        assertEquals(listOf("-2:2", "+2:X", "-5:5", "+5:Y"), ops(lineDiff(old, new)))
    }

    @Test
    fun lineNumbersAreOneBasedAndSkipUnchangedLines() {
        val d = lineDiff("a\nb\nc\nd", "a\nb\nc\nd\ne")
        // 追加的一行是新文件第 5 行
        assertEquals(listOf("+5:e"), ops(d))
        val d2 = lineDiff("z\na\nb", "a\nb")
        assertEquals(listOf("-1:z"), ops(d2))
    }

    @Test
    fun maxLinesTruncatesOutput() {
        val old = (1..50).joinToString("\n") { "old$it" }
        val new = (1..50).joinToString("\n") { "new$it" }
        assertEquals(100, lineDiff(old, new, maxLines = 200).size)
        assertEquals(20, lineDiff(old, new, maxLines = 20).size)
    }

    @Test
    fun hugeFileFallsBackToFullDeletePlusFullAdd() {
        // 任一侧 > 400 行：放弃 LCS，全删 + 全加
        val old = (1..500).joinToString("\n") { "line$it" }
        val new = (1..500).joinToString("\n") { if (it == 10) "changed" else "line$it" }
        val d = lineDiff(old, new, maxLines = 2000)
        assertEquals(1000, d.size)
        assertEquals("减号行在前 500 条", 500, d.count { it.op == "-" })
        assertEquals("加号行在后 500 条", 500, d.count { it.op == "+" })
        // 行号各自从 1 开始、连续
        d.take(500).forEachIndexed { i, l -> assertEquals(i + 1, l.no) }
        d.drop(500).forEachIndexed { i, l -> assertEquals(i + 1, l.no) }
    }

    @Test
    fun exactlyFourHundredLinesStillUsesLcs() {
        val old = (1..400).joinToString("\n") { "line$it" }
        val new = (1..400).joinToString("\n") { if (it == 200) "changed" else "line$it" }
        val d = lineDiff(old, new, maxLines = 2000)
        // 只有一行变了，退化的话会是 800 行
        assertEquals(listOf("-200:line200", "+200:changed"), ops(d))
    }
}
