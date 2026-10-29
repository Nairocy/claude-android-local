package com.nairocy.claude.data

/** 从会话里抽出来的 artifact 条目（用于 Artifacts 列表页）。 */
data class ArtifactItem(
    val id: String,
    val title: String,
    val lang: String,
    val code: String,
    val convId: String,
    val convTitle: String,
    val updatedAt: Long,
)

/**
 * Artifact 判定与抽取：与消息里的卡片口径一致（同一套规则，两处共用）。
 */
object Artifacts {

    private val LANGS = setOf(
        "html", "svg", "xml", "jsx", "tsx", "css", "javascript", "js", "mermaid",
    )

    fun isArtifact(lang: String, code: String): Boolean {
        val l = lang.trim().lowercase()
        // 语言标记本身就是 artifact 类型时，第一个字符到达就算：
        // 否则流式生成的头 40 个字符里卡片不出现，看起来像「artifact 出不来」。
        if (l in LANGS) return true
        val head = code.trimStart()
        if (head.length < 40) return false
        return head.startsWith("<!doctype html", ignoreCase = true) ||
            head.startsWith("<svg", ignoreCase = true) ||
            head.startsWith("<?xml", ignoreCase = true)
    }

    /**
     * 能不能在 App 内的 WebView 里跑起来（渲染）。
     * 只认真的能渲染的类型 —— 不做「点了没反应」的假运行按钮。
     */
    fun canPreview(lang: String, code: String): Boolean {
        val l = lang.trim().lowercase()
        val head = code.trimStart()
        return when {
            l == "html" || l == "svg" -> true
            l == "xml" -> head.startsWith("<svg", ignoreCase = true) || code.contains("<svg", ignoreCase = true)
            looksLikeSvg(l, code) -> true
            head.startsWith("<!doctype html", ignoreCase = true) -> true
            head.startsWith("<html", ignoreCase = true) -> true
            head.startsWith("<svg", ignoreCase = true) -> true
            else -> false
        }
    }

    /** 标题：优先 <title>，其次首行 HTML 注释，最后按语言给中文名。 */
    fun titleOf(lang: String, code: String): String {
        Regex("<title[^>]*>([^<]{1,60})</title>", RegexOption.IGNORE_CASE)
            .find(code)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        Regex("<!--\\s*([^\\n]{1,60}?)\\s*-->")
            .find(code)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return when (lang.trim().lowercase()) {
            "html" -> "HTML 页面"
            "svg" -> "SVG 图形"
            "mermaid" -> "流程图"
            "css" -> "样式表"
            "jsx", "tsx" -> "React 组件"
            else -> lang.ifBlank { "代码" }
        }
    }

    /** 扫描全部会话，按更新时间倒序。 */
    fun collect(conversations: List<Conversation>): List<ArtifactItem> {
        val out = mutableListOf<ArtifactItem>()
        conversations.forEach { conv ->
            conv.messages.forEachIndexed { mi, msg ->
                if (msg.role != Role.ASSISTANT) return@forEachIndexed
                fences(msg.content).forEachIndexed { bi, fence ->
                    if (isArtifact(fence.first, fence.second)) {
                        out += ArtifactItem(
                            id = "${conv.id}-$mi-$bi",
                            title = titleOf(fence.first, fence.second),
                            lang = fence.first,
                            code = fence.second,
                            convId = conv.id,
                            convTitle = conv.title,
                            updatedAt = conv.updatedAt,
                        )
                    }
                }
            }
        }
        return out.sortedByDescending { it.updatedAt }
    }

    /** 极简围栏解析：返回 (lang, code)。足够覆盖对话里的代码块。 */
    private fun fences(text: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val lines = text.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trimStart()
            if (line.startsWith("```")) {
                val lang = line.removePrefix("```").trim()
                val body = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    body.appendLine(lines[i])
                    i++
                }
                out += lang to body.toString().trimEnd()
            }
            i++
        }
        return out
    }

    /* ---------------- WebView 渲染 ---------------- */

    private const val VIEWPORT =
        """<meta name="viewport" content="width=device-width, initial-scale=1">"""

    /**
     * 预览页的公共样式。
     *
     * ⚠️ 两个真机（Android WebView 156）上踩过的坑：
     * 1. 根节点写 `width="100%" height="100%"` 的 SVG 放进「由内容撑开」的容器时百分比没有基准，
     *    算出来是 0 → 整页空白。所以 SVG 用 `width/height:100%` 撑满画布，由 preserveAspectRatio 等比缩放。
     * 2. **`html{height:100%}` 在 WebView 里会解析成 0**（innerHeight 正常，但初始包含块高度是 0，
     *    `100vh` 也是 0）。只有给 body 加 `position:fixed; inset:0`，containing block 才是真实视口。
     *    —— 这是「页面加载成功、DOM 也在，但 SVG 高度恒为 0」的直接原因。
     */
    private const val BASE_STYLE = """
<style>
  html, body { margin: 0; padding: 0; background: #ffffff; }
  body { position: fixed; inset: 0; display: flex; align-items: center; justify-content: center;
         padding: 16px; box-sizing: border-box; overflow: hidden; }
  svg { display: block; width: 100%; height: 100%; max-width: 100%; max-height: 100%; }
</style>
"""

    /**
     * 生成交给 WebView 的完整文档。
     *
     * - svg（含写在 ```html 里的裸 SVG）：套一层最小 HTML 页面 + 居中、等比缩放；
     * - html：原样加载，只补 viewport（不补的话移动端按 980px 版心缩放，字小得看不清）；
     * - 其它：按纯文本展示（调用方用 canPreview 挡住，正常到不了这里）。
     */
    fun previewDocument(lang: String, code: String): String {
        val l = lang.trim().lowercase()
        val body = code.trim()
        if (looksLikeSvg(l, body)) return svgDocument(body)
        if (l == "html" || body.startsWith("<!doctype", ignoreCase = true) ||
            body.startsWith("<html", ignoreCase = true)
        ) {
            return ensureViewport(body)
        }
        val escaped = body
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        return "<!doctype html><html><head><meta charset=\"utf-8\">" + VIEWPORT +
            "<style>body{margin:16px;background:#fff;font:14px/1.6 ui-monospace,monospace;white-space:pre-wrap}</style>" +
            "</head><body>$escaped</body></html>"
    }

    /**
     * 这段代码是不是「一张 SVG」。
     *
     * 不能只看语言标签：模型经常把 SVG 写在 ```html 里，或者把 `<svg>` 前面塞几句说明；
     * 只要内容里出现 `<svg` 且没有 `<html>/<body>`（说明它本身就是一张图而不是整页），
     * 就按 SVG 套壳 —— 否则裸 SVG 的百分比宽高在空文档里解析成 0，画面全空。
     */
    private fun looksLikeSvg(lang: String, body: String): Boolean {
        // 已经是完整页面（哪怕语言标签写成 svg）：不能塞进 <svg> 根节点里，按 HTML 处理
        if (body.contains("<html", ignoreCase = true)) return false
        if (body.contains("<body", ignoreCase = true)) return false
        if (body.trimStart().startsWith("<!doctype", ignoreCase = true)) return false
        if (lang == "svg") return true
        return body.contains("<svg", ignoreCase = true)
    }

    /** SVG → 最小 HTML 页面：去掉 XML 声明、只留 `<svg>…</svg>`、必要时补 viewBox。 */
    fun svgDocument(code: String): String {
        val svg = addViewBoxIfMissing(extractSvg(code))
        return "<!doctype html><html><head><meta charset=\"utf-8\">" + VIEWPORT + BASE_STYLE +
            "</head><body>" + svg + "</body></html>"
    }

    /**
     * 抽出 `<svg …>…</svg>` 本体。
     * 没有 `<svg>` 根（模型只给了图元片段）时补一个根节点，按原始坐标摆放。
     */
    private fun extractSvg(code: String): String {
        val start = code.indexOf("<svg", ignoreCase = true)
        if (start < 0) {
            return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100%\" height=\"100%\">" +
                code.trim() + "</svg>"
        }
        val end = code.lastIndexOf("</svg>", ignoreCase = true)
        return if (end > start) code.substring(start, end + "</svg>".length) else code.substring(start)
    }

    private val SVG_ROOT = Regex("<svg\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val VIEW_BOX = Regex("viewBox\\s*=", RegexOption.IGNORE_CASE)

    /**
     * 没有 viewBox 但有像素宽高的 SVG，缩放会失效（CSS 撑满画布后图形还是原始大小、缩在左上角）。
     * 补一个 `viewBox="0 0 w h"` 就能正常等比缩放；有 viewBox 或拿不到宽高就原样返回。
     */
    private fun addViewBoxIfMissing(svg: String): String {
        val m = SVG_ROOT.find(svg) ?: return svg
        val tag = m.value
        if (VIEW_BOX.containsMatchIn(tag)) return svg
        val w = numberAttr(tag, "width")
        val h = numberAttr(tag, "height")
        if (w == null || h == null || w <= 0f || h <= 0f) return svg
        val patched = tag.dropLast(1).trimEnd() + " viewBox=\"0 0 ${numText(w)} ${numText(h)}\">"
        return svg.replaceRange(m.range, patched)
    }

    private fun numberAttr(tag: String, name: String): Float? {
        // 前面不能紧跟 -/字母：否则 stroke-width 里的 width 会被当成根节点宽度
        val m = Regex("(?<![-\\w])" + name + "\\s*=\\s*[\"']([0-9.]+)(?:px)?[\"']", RegexOption.IGNORE_CASE)
            .find(tag) ?: return null
        return m.groupValues[1].toFloatOrNull()
    }

    private fun numText(v: Float): String =
        if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString()

    private fun ensureViewport(html: String): String {
        if (html.contains("name=\"viewport\"", ignoreCase = true) ||
            html.contains("name='viewport'", ignoreCase = true)
        ) return html
        val headEnd = html.indexOf("</head>", ignoreCase = true)
        if (headEnd >= 0) return html.substring(0, headEnd) + VIEWPORT + html.substring(headEnd)
        val bodyStart = html.indexOf("<body", ignoreCase = true)
        if (bodyStart >= 0) return html.substring(0, bodyStart) + VIEWPORT + html.substring(bodyStart)
        return "<!doctype html><html><head><meta charset=\"utf-8\">" + VIEWPORT +
            "</head><body>" + html + "</body></html>"
    }
}
