package com.nairocy.claude

import com.nairocy.claude.data.Artifacts
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** WebView 预览文档的拼装规则：svg 包装、viewport 注入、纯文本兜底。 */
class ArtifactsPreviewTest {

    @Test
    fun svgGetsCenteredWrapper() {
        val doc = Artifacts.previewDocument("svg", "<svg viewBox=\"0 0 10 10\"><circle cx=\"5\" cy=\"5\" r=\"4\"/></svg>")
        assertTrue(doc.contains("display: flex"))
        assertTrue(doc.contains("max-height: 100%"))
        assertTrue(doc.contains("<svg viewBox=\"0 0 10 10\">"))
    }

    @Test
    fun htmlGetsViewportWhenMissing() {
        val doc = Artifacts.previewDocument("html", "<!doctype html><html><head><title>t</title></head><body>x</body></html>")
        assertTrue(doc.contains("name=\"viewport\""))
        assertTrue(doc.indexOf("name=\"viewport\"") < doc.indexOf("</head>"))
    }

    @Test
    fun existingViewportIsKeptOnce() {
        val html = "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width\"></head><body>x</body></html>"
        val doc = Artifacts.previewDocument("html", html)
        assertTrue(doc.split("name=\"viewport\"").size == 2)
    }

    @Test
    fun fragmentGetsWrapped() {
        val doc = Artifacts.previewDocument("html", "<h1>hi</h1>")
        assertTrue(doc.contains("<body>"))
        assertTrue(doc.contains("<h1>hi</h1>"))
    }

    @Test
    fun previewSupportIsHonest() {
        assertTrue(Artifacts.canPreview("html", "<div>x</div>"))
        assertTrue(Artifacts.canPreview("svg", "<svg></svg>"))
        assertTrue(Artifacts.canPreview("", "<!doctype html><html></html>"))
        assertFalse(Artifacts.canPreview("kotlin", "fun main() {}"))
        assertFalse(Artifacts.canPreview("mermaid", "graph TD; A-->B"))
    }

    @Test
    fun languageTagAloneMarksArtifactWhileStreaming() {
        // 流式刚开始（代码还没到 40 个字符）也要认出 artifact，否则卡片不出现
        assertTrue(Artifacts.isArtifact("html", "<!doc"))
        assertTrue(Artifacts.isArtifact("svg", "<svg"))
        assertFalse(Artifacts.isArtifact("kotlin", "fun"))
    }

    /* ── SVG 预览：裸 SVG / 百分比宽高 / XML 声明 / 缺 viewBox ── */

    @Test
    fun percentSizedSvgGetsFullCanvasCss() {
        // 根节点 width/height=100% 的 SVG 放进 flex 容器会算出 0 尺寸（整页空白），
        // 必须靠 CSS 的 width/height:100% 撑满画布
        val code = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100%\" height=\"100%\" " +
            "viewBox=\"0 0 100 100\"><circle cx=\"50\" cy=\"50\" r=\"40\"/></svg>"
        val doc = Artifacts.previewDocument("svg", code)
        assertTrue(doc.startsWith("<!doctype html>"))
        assertTrue(doc.contains("width: 100%; height: 100%"))
    }

    @Test
    fun bareSvgInsideHtmlFenceIsWrapped() {
        // 模型常把 SVG 写在 ```html 里：也要套壳，否则裸 SVG 的百分比高度解析成 0
        val code = "<svg viewBox=\"0 0 10 10\"><rect width=\"10\" height=\"10\"/></svg>"
        val doc = Artifacts.previewDocument("html", code)
        assertTrue(doc.contains("display: flex"))
        assertTrue(doc.contains("<body><svg"))
    }

    @Test
    fun svgWrapperUsesFixedBodyForWebViewViewport() {
        // 真机（Android WebView 156）上 html{height:100%} 与 100vh 都会解析成 0，
        // 只有 position:fixed 的 body 才能拿到真实视口高度；这条 CSS 就是修复本身，别改回去。
        val doc = Artifacts.previewDocument("svg", "<svg viewBox=\"0 0 10 10\"></svg>")
        assertTrue(doc.contains("position: fixed"))
        assertTrue(doc.contains("inset: 0"))
        assertTrue(doc.contains("width: 100%; height: 100%"))
    }

    @Test
    fun xmlPrologueIsStripped() {
        val doc = Artifacts.previewDocument("svg", "<?xml version=\"1.0\"?><svg viewBox=\"0 0 10 10\"></svg>")
        assertFalse(doc.contains("<?xml"))
        assertTrue(doc.contains("<svg viewBox=\"0 0 10 10\">"))
    }

    @Test
    fun missingViewBoxIsDerivedFromPixelSize() {
        val doc = Artifacts.previewDocument("svg", "<svg width=\"24\" height=\"24\"><circle cx=\"12\" cy=\"12\" r=\"10\"/></svg>")
        assertTrue(doc.contains("viewBox=\"0 0 24 24\""))
    }

    @Test
    fun strokeWidthIsNotMistakenForRootWidth() {
        val doc = Artifacts.previewDocument("svg", "<svg stroke-width=\"3\" width=\"10\" height=\"20\"></svg>")
        assertTrue(doc.contains("viewBox=\"0 0 10 20\""))
    }
}
