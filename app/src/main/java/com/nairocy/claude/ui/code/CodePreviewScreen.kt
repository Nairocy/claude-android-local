package com.nairocy.claude.ui.code

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nairocy.claude.R
import com.nairocy.claude.data.Artifacts
import com.nairocy.claude.data.CodeWorkspace
import com.nairocy.claude.ui.theme.AnthropicSans
import com.nairocy.claude.ui.theme.ClaudeTheme
import java.io.ByteArrayInputStream

/**
 * 工作区预览页：把手机里的工作区当成一个静态站点跑。
 *
 * ⚠️ 不用 `file://`（WebView 对 file 的跨源与相对路径都很挑，而且 allowFileAccess=false 是安全底线）：
 * 用虚拟域名 `https://workspace.example/` 加载，`shouldInterceptRequest` 里把 path 映射到
 * [CodeWorkspace.resolve]，直接把字节回给 WebView —— 这样 `style.css`、`app.js` 这些相对路径天然可用。
 */
private const val TAG = "CodePreview"

/** 虚拟源。`.example` 是保留域名；`.local` 会触发系统的本地网络扫描授权弹窗。 */
private const val BASE_URL = "https://workspace.example"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CodePreviewScreen(
    path: String,
    /** 当前工作区 id：预览只读这个工作区的目录。 */
    wsId: String,
    onBack: () -> Unit,
) {
    val colors = ClaudeTheme.colors
    val ctx = LocalContext.current
    val rootDir = remember(wsId) { com.nairocy.claude.data.CodeWorkspaceStore.dirOf(ctx, wsId) }
    // 真 HTTP 服务：拦截式虚拟源会让 WebView 把文档当「非网络响应」，布局视口高度算成 0，
    // 页面里的 vh / 百分比高度全塌（用户实测：太阳 SVG 整块消失、只剩纯文字）。
    val server = remember(wsId) { com.nairocy.claude.data.WorkspaceServer.start(rootDir) }
    DisposableEffect(server) {
        onDispose { runCatching { server?.stop() } }
    }
    val baseUrl = server?.let { "http://127.0.0.1:${it.port}" }
    // 非网页文件（txt / css / json…）按纯文本渲染；html/svg 交给 WebView
    val isWeb = remember(path) { CodeWorkspace.isPreviewable(path) }
    val textBody = remember(path) {
        if (isWeb) null else runCatching { CodeWorkspace.read(rootDir, path) }.getOrElse {
            "读取失败：" + (it.message ?: it.javaClass.simpleName)
        }
    }
    val document = remember(path, textBody) {
        textBody?.let { Artifacts.previewDocument("", it) }
    }
    val webView = remember { WebView(ctx) }
    val loaded = remember { arrayOfNulls<String>(1) }
    // WebView 的「布局视口 0×0」自愈标记：只重载一次，避免死循环
    val viewportFixed = remember { intArrayOf(0) }
    // 每次点 ↻ 都换一个 query，强制 WebView 重新走 shouldInterceptRequest（拿最新文件内容）
    var reloadTick by remember { mutableStateOf(0) }

    // 缩放：1–8× 捏合（以两指中心为锚点）、放大后单指拖动、双击 2×/还原、1× 不消费单指
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }

    DisposableEffect(webView) {
        onDispose {
            runCatching {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.destroy()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .systemBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(44.dp)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_back_arrow),
                    contentDescription = "返回",
                    tint = colors.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = path.trimStart('/'),
                fontFamily = AnthropicSans,
                fontSize = 17.sp,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 96.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(44.dp)
                    .clickable {
                        reloadTick++
                        loaded[0] = null
                        if (isWeb && webView.width > 0 && baseUrl != null) {
                            webView.loadUrl(baseUrl + path + "?t=" + reloadTick)
                            loaded[0] = "$path#$reloadTick"
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.cds_icon_arrow_clockwise),
                    contentDescription = "刷新",
                    tint = colors.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

                Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .onSizeChanged { box = it }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.01f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2f
                                offset = Offset.Zero
                            }
                        }
                    )
                }
                .pointerInput(box) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var lastDist = 0f
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                val p0 = pressed[0].position
                                val p1 = pressed[1].position
                                val dist = (p0 - p1).getDistance()
                                val centroid = (p0 + p1) / 2f
                                if (lastDist > 0f && dist > 1f) {
                                    val next = (scale * (dist / lastDist)).coerceIn(1f, 8f)
                                    val anchor = centroid - Offset(box.width / 2f, box.height / 2f)
                                    offset = clampZoomOffset(
                                        (offset - anchor) * (next / scale) + anchor,
                                        next,
                                        box,
                                    )
                                    scale = next
                                }
                                lastDist = dist
                                pressed.forEach { it.consume() }
                            } else {
                                lastDist = 0f
                                val change = pressed.first()
                                val delta = change.positionChange()
                                if (scale > 1.01f && (delta.x != 0f || delta.y != 0f)) {
                                    offset = clampZoomOffset(offset + delta, scale, box)
                                    change.consume()
                                }
                            }
                        }
                        if (scale <= 1.01f) {
                            scale = 1f
                            offset = Offset.Zero
                        }
                    }
                },
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    )
                    .onSizeChanged { size ->
                        // 0×0 时加载会把布局视口固化成 0（真机踩过）
                        if (size.width > 0 && size.height > 0 && loaded[0] == null) {
                            loaded[0] = path
                            if (isWeb && baseUrl != null) {
                                webView.loadUrl(baseUrl + path)
                            } else if (document != null) {
                                webView.loadDataWithBaseURL(baseUrl ?: BASE_URL, document, "text/html", "UTF-8", null)
                            }
                        }
                    },
                factory = {
                    webView.apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.loadsImagesAutomatically = true
                        // 宽视口要开：CSS px 必须按 dp 映射（关掉会让页面按 1:1 设备像素排版，字小到看不清）。
                        // 但「概览缩放」关掉 —— 它拿内容宽度反推缩放，正是布局视口算出 0 的帮凶。
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = false
                        settings.textZoom = 100
                        settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                        setBackgroundColor(AndroidColor.WHITE)
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean {
                                Log.d(TAG, "console[${m.messageLevel()}] ${m.message()} @${m.lineNumber()}")
                                return true
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): WebResourceResponse? {
                                val url = request?.url ?: return null
                                // 只接管虚拟源下的工作区文件；其它（data: 等）交给 WebView 自己
                                if (url.host != "workspace.example") return null
                                val p = url.path ?: return null
                                return runCatching {
                                    val file = CodeWorkspace.resolve(rootDir, p)
                                    if (!file.isFile) return@runCatching null
                                    val bytes = file.readBytes()
                                    // ⚠️ mimeType 不能带 `;charset=`：WebView 的 WebResourceResponse 要的是纯 MIME，
                                    // 编码走第三个参数（带参数的 mime 在部分 WebView 版本上会按二进制处理）；
                                    // 二进制资源（png/jpg/woff…）编码必须给 null，否则字节会被当文本转码。
                                    val mime = CodeWorkspace.mimeOf(p).substringBefore(';').trim()
                                    val textLike = mime.startsWith("text/") ||
                                        mime.contains("javascript") || mime.contains("json") ||
                                        mime.contains("xml") || mime.contains("svg")
                                    WebResourceResponse(
                                        mime,
                                        if (textLike) "utf-8" else null,
                                        ByteArrayInputStream(bytes),
                                    )
                                }.getOrElse {
                                    Log.w(TAG, "读取工作区文件失败: $p", it)
                                    null
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                val w = view ?: return
                                // ⚠️ 踩过：文档在某些情况下被 WebView 按「0×0 布局视口」排版，
                                // 页面里 78vh / 88vw / 百分比高度全塌成 0（表现为「只剩一行文字、SVG 消失」）。
                                // 页面加载完量一次真实视口，发现是 0 就重载一次 —— 重载会强制重新测量。
                                // ⚠️ 真机上量到：innerHeight=2881 但 100vh=0、html/body 高度=0 →
                                // WebView 的「布局视口(ICB)高度」是 0，页面里 vh/百分比高度全塌。
                                // 先做一次 1px 尺寸扰动逼它重新测量（比重载快、不闪白），还不行再重载。
                                w.evaluateJavascript(
                                    "(function(){var d=document.createElement('div');" +
                                        "d.style.cssText='position:absolute;height:100vh;width:0';" +
                                        "document.body.appendChild(d);var h=d.getBoundingClientRect().height;d.remove();" +
                                        "return [Math.round(innerWidth),Math.round(innerHeight),Math.round(h)].join(',');})()"
                                ) { vp ->
                                    Log.d(TAG, "pageFinished url=$url probe=$vp index=${viewportFixed[0]}")
                                    val nums = vp?.trim('"')?.split(',')?.mapNotNull { it.toIntOrNull() }.orEmpty()
                                    val vhZero = nums.size == 3 && nums[2] < 10 && nums[1] > 100
                                    if (vhZero && viewportFixed[0] < 2) {
                                        viewportFixed[0]++
                                        if (viewportFixed[0] == 1) {
                                            Log.w(TAG, "100vh=$vp → 1px relayout nudge")
                                            nudgeLayout(w)
                                        } else {
                                            Log.w(TAG, "100vh=$vp after nudge → reload once")
                                            w.postDelayed({ runCatching { w.reload() } }, 120)
                                        }
                                    }
                                }
                            }
                        }
                        post {
                            if (width > 0 && height > 0 && loaded[0] == null) {
                                loaded[0] = path
                                if (isWeb) {
                                    loadUrl(BASE_URL + path)
                                } else if (document != null) {
                                    loadDataWithBaseURL(BASE_URL + "/", document, "text/html", "UTF-8", null)
                                }
                            }
                        }
                    }
                },
                update = { view ->
                    // 重组时不要重复加载：会白屏闪一下
                    if (view.width > 0 && view.height > 0 && loaded[0] == null) {
                        loaded[0] = path
                        if (isWeb && baseUrl != null) {
                            view.loadUrl(baseUrl + path)
                        } else if (document != null) {
                            view.loadDataWithBaseURL(baseUrl ?: BASE_URL, document, "text/html", "UTF-8", null)
                        }
                    }
                },
            )
        }
    }
}

/**
 * 1px 尺寸扰动：WebView 的布局视口高度在首帧被算成 0 之后不会自己恢复，
 * 改一下 layoutParams 再改回去能逼它重新测量（比重载便宜，也不闪白）。
 */
private fun nudgeLayout(view: WebView) {
    val lp = view.layoutParams ?: return
    val h = view.height
    lp.height = if (h > 1) h - 1 else 1
    view.layoutParams = lp
    view.postDelayed({
        lp.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
        view.layoutParams = lp
        view.requestLayout()
    }, 60)
}

/** 把平移量夹在「放大出来的那部分画布」内，避免放大后把内容拖出屏幕再也找不回来。 */
private fun clampZoomOffset(offset: Offset, scale: Float, size: IntSize): Offset {
    val maxX = (size.width * (scale - 1f)) / 2f
    val maxY = (size.height * (scale - 1f)) / 2f
    return Offset(
        offset.x.coerceIn(-maxX, maxX),
        offset.y.coerceIn(-maxY, maxY),
    )
}
