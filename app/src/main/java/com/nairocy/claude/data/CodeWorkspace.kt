package com.nairocy.claude.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** 工作区里的一个文件。[path] 带前导 "/"，如 `/index.html`。 */
data class WorkspaceFile(val path: String, val size: Long)

/** 一次工具调用的结果：回给模型的纯文本 + 界面要展示的卡片。 */
data class ToolOutcome(val content: String, val run: CodeToolRun)

/**
 * 本机静态网页工作区（`files/users/<uid>/workspace/`）。
 *
 * 安全前提：**路径一律不可越界**。模型给的是 `/index.html` 这种工作区路径，
 * 我们剥掉前导 `/`、拒绝 `..`、拒绝盘符/UNC 这类绝对路径，最后再拿 canonical
 * 路径复核一遍必须落在 root 里 —— 三层都过了才动文件。
 */
object CodeWorkspace {

    /** 单次 read 回给模型的上限（字符）。超了截断并注明总长，别把上下文一次塞爆。 */
    const val MAX_READ_CHARS = 30_000

    /** ls 最多列这么多条。 */
    private const val MAX_LIST = 200

    /** 文件名里不允许出现的字符（Windows 侧最严的那一套，跨平台都别用）。 */
    private const val ILLEGAL_CHARS = "\\/:*?\"<>|"

    private val DRIVE = Regex("^[A-Za-z]:")

    /** 递归列出文件：跳过 `.` 开头的目录与文件，按路径排序，最多 200 条。 */
    fun list(root: File): List<WorkspaceFile> {
        val out = mutableListOf<WorkspaceFile>()

        fun walk(dir: File, prefix: String, depth: Int) {
            if (depth > 8 || out.size >= MAX_LIST) return
            val children = dir.listFiles()?.sortedBy { it.name } ?: return
            for (f in children) {
                if (f.name.startsWith(".")) continue
                if (out.size >= MAX_LIST) return
                if (f.isDirectory) {
                    walk(f, "$prefix/${f.name}", depth + 1)
                } else if (f.isFile) {
                    out += WorkspaceFile("$prefix/${f.name}", f.length())
                }
            }
        }

        walk(root, "", 0)
        return out.sortedBy { it.path }
    }

    /** 读正文（UTF-8）。不存在就抛，由上层折成失败工具卡。超过 30000 字符截断并注明。 */
    fun read(root: File, path: String): String {
        val f = resolve(root, path)
        if (!f.exists() || !f.isFile) throw IllegalArgumentException("找不到文件: ${normPath(path)}")
        val raw = f.readText(Charsets.UTF_8)
        return if (raw.length <= MAX_READ_CHARS) raw else raw.take(MAX_READ_CHARS) + truncNote(raw.length)
    }

    private fun truncNote(total: Int): String = "\n（已截断，共 $total 字符）"

    /** 写文件（UTF-8），父目录自动建。 */
    fun write(root: File, path: String, content: String) {
        val f = resolve(root, path)
        f.parentFile?.mkdirs()
        f.writeText(content, Charsets.UTF_8)
    }

    /**
     * 工作区路径 → 真实文件。
     *
     * 规则：先剥前导 `/`（工作区根标记），再拒绝 `..`、盘符/UNC 绝对路径、
     * 非法文件名字符，最后 canonical 复核必须落在 root 内。
     */
    fun resolve(root: File, path: String): File = resolveIn(root.canonicalFile, path)

    /**
     * 沙箱解析的纯函数版本（root 由调用方给）：单测直接喂临时目录，不用 Android Context。
     * [resolve] 只是把 workspace 根算出来再转给它。
     */
    internal fun resolveIn(root: File, path: String): File {
        // 反斜杠也当分隔符（模型偶尔写 Windows 风格）；前导 / 只是「工作区根」的写法，剥掉。
        val p = path.trim().replace('\\', '/').trimStart('/')
        if (p.isBlank()) throw IllegalArgumentException("路径为空")
        if (DRIVE.containsMatchIn(p)) throw IllegalArgumentException("不支持绝对路径: $path")

        val segments = p.split('/').filter { it.isNotEmpty() && it != "." }
        if (segments.isEmpty()) throw IllegalArgumentException("路径无效: $path")
        for (seg in segments) {
            if (seg == "..") throw IllegalArgumentException("路径不能包含 ..: $path")
            if (seg.any { it in ILLEGAL_CHARS || it.code < 0x20 }) {
                throw IllegalArgumentException("文件名含非法字符: $seg")
            }
        }

        val target = File(root, segments.joinToString("/")).canonicalFile
        val rootPath = root.canonicalPath
        if (target.path != rootPath && !target.path.startsWith(rootPath + File.separator)) {
            throw IllegalArgumentException("路径越界: $path")
        }
        return target
    }

    /** 预览用 MIME；认不出的当纯文本。 */
    fun mimeOf(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> "text/html;charset=utf-8"
        "css" -> "text/css;charset=utf-8"
        "js", "mjs" -> "application/javascript;charset=utf-8"
        "json" -> "application/json;charset=utf-8"
        "xml" -> "application/xml;charset=utf-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "ico" -> "image/x-icon"
        "pdf" -> "application/pdf"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        "ttf" -> "font/ttf"
        "otf" -> "font/otf"
        "mp4" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        else -> "text/plain;charset=utf-8"
    }

    /** 能在 WebView 里直接跑的：html / htm / svg。 */
    fun isPreviewable(path: String): Boolean =
        path.substringAfterLast('.', "").lowercase() in setOf("html", "htm", "svg")

    // ───────────────────────── 工具 ─────────────────────────

    /**
     * 执行一个文件工具。5 个文件工具在这里；`web_search` / `fetch_page` 要发网络请求，
     * 由 ViewModel 处理（这里直接抛，免得把网络层依赖拖进数据层）。
     *
     * 失败一律抛 [IllegalArgumentException]（找不到文件 / old_str 不唯一 / 路径越界），
     * 上层把它折成 `ok = false` 的工具卡再回给模型。
     */
    fun runTool(root: File, id: String, name: String, args: JsonObject): ToolOutcome =
        when (name) {
            "ls" -> ls(root, id)
            "read" -> readTool(root, id, args.str("path"))
            "write" -> writeTool(root, id, args.str("path"), args.str("content"))
            "edit" -> editTool(root, id, args.str("path"), args.str("old_str"), args.str("new_str"))
            "grep" -> grepTool(root, id, args.str("pattern"))
            "web_search", "fetch_page" -> throw IllegalArgumentException("网络工具由上层处理")
            else -> throw IllegalArgumentException("未知工具: $name")
        }

    private fun ls(root: File, id: String): ToolOutcome {
        val files = list(root)
        val text = if (files.isEmpty()) "（工作区为空）"
        else files.joinToString("\n") { "${it.path}（${it.size} B）" }
        return ToolOutcome(
            content = text,
            run = CodeToolRun(id = id, name = "ls", target = "", output = text),
        )
    }

    private fun readTool(root: File, id: String, path: String): ToolOutcome {
        val target = normPath(path)
        val body = read(root, path)
        // 带行号，模型引用行号才说得清「第几行」。
        val lines = if (body.endsWith("\n")) body.dropLast(1).split('\n') else body.split('\n')
        val numbered = if (lines.size == 1 && lines[0].isEmpty()) "（空文件）"
        else lines.mapIndexed { i, line -> "${i + 1}\t$line" }.joinToString("\n")
        return ToolOutcome(
            content = numbered,
            run = CodeToolRun(id = id, name = "read", target = target, output = numbered),
        )
    }

    private fun writeTool(root: File, id: String, path: String, content: String): ToolOutcome {
        val target = normPath(path)
        val f = resolve(root, path)
        val old = if (f.exists() && f.isFile) f.readText(Charsets.UTF_8) else ""
        f.parentFile?.mkdirs()
        f.writeText(content, Charsets.UTF_8)
        // 新文件 = 全部 "+" 行（lineDiff 拿空旧文本算就是这个效果）。
        val diff = lineDiff(old, content)
        return ToolOutcome(
            content = "已写入 $target（${content.length} 字符）",
            run = CodeToolRun(id = id, name = "write", target = target, diff = diff),
        )
    }

    private fun editTool(
        root: File,
        id: String,
        path: String,
        oldStr: String,
        newStr: String,
    ): ToolOutcome {
        if (oldStr.isEmpty()) throw IllegalArgumentException("old_str 不能为空")
        val target = normPath(path)
        val f = resolve(root, path)
        if (!f.exists() || !f.isFile) throw IllegalArgumentException("找不到文件: $target")
        val oldText = f.readText(Charsets.UTF_8)

        val count = countOccurrences(oldText, oldStr)
        if (count == 0) throw IllegalArgumentException("未找到 old_str，请先 read 确认原文")
        if (count > 1) throw IllegalArgumentException("old_str 出现了 $count 次，需要唯一")

        val pos = oldText.indexOf(oldStr)
        val newText = oldText.substring(0, pos) + newStr + oldText.substring(pos + oldStr.length)
        f.writeText(newText, Charsets.UTF_8)

        // 只给这次替换的上下文：全量 diff 之后按行号窗口裁（±3 行）。
        // 行号是 lineDiff 算出来的原值，窗口只做过滤，不重新编号。
        val startLine = oldText.take(pos).count { it == '\n' }          // 0-based
        val span = maxOf(
            oldStr.count { it == '\n' },
            newStr.count { it == '\n' },
        )
        val lo = startLine - 3
        val hi = startLine + span + 3
        val diff = lineDiff(oldText, newText)
            .filter { (it.no - 1) in lo..hi }
            .take(200)

        return ToolOutcome(
            content = "已编辑 $target",
            run = CodeToolRun(id = id, name = "edit", target = target, diff = diff),
        )
    }

    private fun grepTool(root: File, id: String, pattern: String): ToolOutcome {
        if (pattern.isEmpty()) throw IllegalArgumentException("pattern 不能为空")
        val out = mutableListOf<String>()
        val needle = pattern.lowercase()

        fun walk(dir: File, prefix: String, depth: Int) {
            if (depth > 8 || out.size >= MAX_GREP) return
            val children = dir.listFiles()?.sortedBy { it.name } ?: return
            for (f in children) {
                if (out.size >= MAX_GREP) return
                if (f.name.startsWith(".")) continue
                if (f.isDirectory) {
                    walk(f, "$prefix/${f.name}", depth + 1)
                    continue
                }
                if (!f.isFile || f.length() > MAX_GREP_FILE_BYTES) continue
                val lines = runCatching { f.readText(Charsets.UTF_8).split('\n') }.getOrNull() ?: continue
                for ((i, line) in lines.withIndex()) {
                    if (out.size >= MAX_GREP) return
                    if (line.lowercase().contains(needle)) {
                        val trimmed = line.trim().let { if (it.length > 160) it.take(160) else it }
                        out += "$prefix/${f.name}:${i + 1}:$trimmed"
                    }
                }
            }
        }

        walk(root, "", 0)
        val text = if (out.isEmpty()) "无匹配" else out.joinToString("\n")
        return ToolOutcome(
            content = text,
            run = CodeToolRun(id = id, name = "grep", target = pattern, output = text),
        )
    }

    // ───────────────────────── 小工具 ─────────────────────────

    /** 统一成 `/a/b` 形式（用于卡片与提示文案）。 */
    fun normPath(path: String): String {
        val rel = path.trim().replace('\\', '/').trimStart('/')
        return if (rel.isBlank()) "/" else "/$rel"
    }

    private fun countOccurrences(haystack: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var n = 0
        var i = haystack.indexOf(needle)
        while (i >= 0) {
            n++
            i = haystack.indexOf(needle, i + needle.length)
        }
        return n
    }

    private fun JsonObject.str(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

    private const val MAX_GREP = 60
    private const val MAX_GREP_FILE_BYTES = 512_000L

    // ───────────────────────── demo 种子 ─────────────────────────

    /** 星芒 path 取自 `res/drawable/claude_spark_icon.xml`（同一个 Claude 标记）。 */
}
