package com.nairocy.claude.ui.artifacts

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.util.Log
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nairocy.claude.R
import com.nairocy.claude.data.Artifacts
import com.nairocy.claude.ui.theme.AnthropicSans
import com.nairocy.claude.ui.theme.ClaudeTheme

/**
 * Artifact 渲染页：html / svg 直接丢给 WebView 跑。
 *
 * - 白底固定：网页自带背景色时由页面自己覆盖，不该被 App 的深色主题染成黑底黑字；
 * - 不放「返回」以外的多余按钮，回退走 ← 与系统返回键；
 * - 不加载 file://，也不允许 content://（只跑模型给的字符串）。
 */
private const val TAG = "ArtifactView"

/**
 * 页面的 base URL。
 * ⚠️ 不能用 null：WebView 156 上 `loadDataWithBaseURL(null, …)` 会直接得到 about:blank（真机实测）。
 * 也不用 `.local`（会触发系统的本地网络扫描授权弹窗）；`.example` 是保留域名，只用于给文档一个源。
 */
private const val BASE_URL = "https://artifact.example/"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ArtifactViewScreen(
    title: String,
    lang: String,
    code: String,
    onBack: () -> Unit,
) {
    val colors = ClaudeTheme.colors
    val ctx = LocalContext.current
    val document = remember(lang, code) { Artifacts.previewDocument(lang, code) }
    val webView = remember { WebView(ctx) }
    val loaded = remember { arrayOfNulls<String>(1) }

    // 预览缩放状态：1× 起、最大 8×；offset 是平移量（仅放大后生效）
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
                text = title,
                fontFamily = AnthropicSans,
                fontSize = 17.sp,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 56.dp),
            )
        }

        // 预览区：双指捏合缩放 1–8×、放大后单指拖动、双击 2× / 再双击还原。
        // 1× 时不消费单指事件 —— HTML artifact 里的滚动与点击仍然照常。
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
                                    // 以捏合中心为锚点：手指按住的那一点在屏幕上保持不动
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
                // 等控件量出真实尺寸再 load（0×0 时加载的页面在 WebView 里布局视口高度会是 0）
                .onSizeChanged { size ->
                    if (size.width > 0 && size.height > 0 && loaded[0] != document) {
                        loaded[0] = document
                        webView.loadDataWithBaseURL(BASE_URL, document, "text/html", "UTF-8", null)
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
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    setBackgroundColor(AndroidColor.WHITE)
                    // 控制台/加载日志进 logcat：artifact 渲染出问题时能直接看到原因
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean {
                            Log.d(TAG, "console[${m.messageLevel()}] ${m.message()} @${m.lineNumber()}")
                            return true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            Log.d(TAG, "pageFinished url=$url len=${document.length}")
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?,
                            error: android.webkit.WebResourceError?,
                        ) {
                            Log.e(TAG, "webview error ${error?.errorCode} ${error?.description} ${request?.url}")
                        }
                    }
                    // 兜底：万一 onSizeChanged 没触发，至少等这一帧排版完再 load
                    post {
                        if (width > 0 && height > 0 && loaded[0] != document) {
                            loaded[0] = document
                            loadDataWithBaseURL(BASE_URL, document, "text/html", "UTF-8", null)
                        }
                    }
                }
            },
            update = { view ->
                // 重组时不要重复 loadData：会白屏闪一下
                if (view.width > 0 && view.height > 0 && loaded[0] != document) {
                    loaded[0] = document
                    view.loadDataWithBaseURL(BASE_URL, document, "text/html", "UTF-8", null)
                }
            },
        )
        }
    }
}

/**
 * 把平移量夹在「放大出来的那部分画布」内，避免放大后把内容拖出屏幕再也找不回来。
 */
private fun clampZoomOffset(offset: Offset, scale: Float, size: IntSize): Offset {
    val maxX = (size.width * (scale - 1f)) / 2f
    val maxY = (size.height * (scale - 1f)) / 2f
    return Offset(
        offset.x.coerceIn(-maxX, maxX),
        offset.y.coerceIn(-maxY, maxY),
    )
}
