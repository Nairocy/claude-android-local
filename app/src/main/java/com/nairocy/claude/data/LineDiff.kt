package com.nairocy.claude.data

/**
 * 行级 diff（LCS 对齐），给 Code 页的工具卡用。
 *
 * 只输出变化行：`-` 是旧文件的、`+` 是新文件的，行号各自从 1 开始。
 * 同一段连续变化里**先删后加**（和 git 的显示顺序一致）。
 *
 * 任一侧行数超过 [MAX_LCS_LINES] 就放弃对齐，退化成「全删 + 全加」：
 * 手机上算 400×400 的 LCS 表已经是上限，再大不如直接整段换。
 */
fun lineDiff(old: String, new: String, maxLines: Int = 200): List<CodeDiffLine> {
    val a = textLines(old)
    val b = textLines(new)
    if (a == b) return emptyList()

    val raw: List<CodeDiffLine> = if (a.size > MAX_LCS_LINES || b.size > MAX_LCS_LINES) {
        a.mapIndexed { i, t -> CodeDiffLine("-", i + 1, t) } +
            b.mapIndexed { i, t -> CodeDiffLine("+", i + 1, t) }
    } else {
        lcsDiff(a, b)
    }
    return if (raw.size > maxLines) raw.take(maxLines) else raw
}

/** 按行切：`""` 是零行；结尾的换行不算多出一行空行。 */
internal fun textLines(s: String): List<String> {
    if (s.isEmpty()) return emptyList()
    val raw = s.split('\n')
    return if (s.endsWith("\n")) raw.dropLast(1) else raw
}

private fun lcsDiff(a: List<String>, b: List<String>): List<CodeDiffLine> {
    val n = a.size
    val m = b.size
    // dp[i][j] = a[i..] 与 b[j..] 的最长公共子序列长度
    val dp = Array(n + 1) { IntArray(m + 1) }
    for (i in n - 1 downTo 0) {
        for (j in m - 1 downTo 0) {
            dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
    }

    val out = mutableListOf<CodeDiffLine>()
    val dels = mutableListOf<CodeDiffLine>()
    val adds = mutableListOf<CodeDiffLine>()
    fun flush() {
        out += dels
        out += adds
        dels.clear()
        adds.clear()
    }

    var i = 0
    var j = 0
    while (i < n && j < m) {
        when {
            a[i] == b[j] -> {
                flush()
                i++
                j++
            }
            // 平手时先记删除，视觉上「旧行划掉、新行补上」
            dp[i + 1][j] >= dp[i][j + 1] -> {
                dels += CodeDiffLine("-", i + 1, a[i])
                i++
            }
            else -> {
                adds += CodeDiffLine("+", j + 1, b[j])
                j++
            }
        }
    }
    while (i < n) {
        dels += CodeDiffLine("-", i + 1, a[i])
        i++
    }
    while (j < m) {
        adds += CodeDiffLine("+", j + 1, b[j])
        j++
    }
    flush()
    return out
}

/** 超过这个行数就不做 LCS（手机内存与耗时不值当）。 */
private const val MAX_LCS_LINES = 400
