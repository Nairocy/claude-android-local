package com.nairocy.claude.data

import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * 工作区的**真·本地 HTTP 服务**（只监听 127.0.0.1 的随机端口）。
 *
 * 为什么不用 `shouldInterceptRequest` 虚拟源：那条路返回的文档在 WebView 里被当成
 * 「非网络响应」，布局视口高度会算成 0 —— `vh` / `%` 高度全部塌成 0，页面只剩纯文字
 * （用户实测：太阳 SVG 整块不显示、文字排版错乱）。真起一个 HTTP 服务，文档就是正常
 * 网络响应，`vh`、相对路径、`fetch` 全部与浏览器一致。
 *
 * 约束：
 * - 只绑回环地址，局域网也访问不到；
 * - 路径仍走 [CodeWorkspace.resolve] 的沙箱（`..`、盘符、非法字符一律拒）；
 * - 单请求单响应，`Connection: close`，够预览用；
 * - 用 [start] / [stop] 成对调用（预览页的 DisposableEffect）。
 */
class WorkspaceServer private constructor(
    private val root: File,
    private val socket: ServerSocket,
) {

    val port: Int get() = socket.localPort

    @Volatile
    private var running = true

    private val thread = Thread({
        while (running) {
            val client = runCatching { socket.accept() }.getOrNull() ?: break
            runCatching { serve(client) }
            runCatching { client.close() }
        }
    }, "workspace-http").apply { isDaemon = true }

    private fun serve(client: Socket) {
        val input = client.getInputStream().bufferedReader(Charsets.UTF_8)
        val requestLine = input.readLine() ?: return
        // 只认 GET/HEAD；顺手把剩余请求头读掉（不读会让某些客户端 reset）
        while (true) {
            val line = input.readLine() ?: break
            if (line.isEmpty()) break
        }
        val parts = requestLine.split(' ')
        if (parts.size < 2) return
        val method = parts[0]
        val rawPath = parts[1].substringBefore('?').substringBefore('#')
        val path = runCatching { URLDecoder.decode(rawPath, "UTF-8") }.getOrDefault(rawPath)

        val out = BufferedOutputStream(client.getOutputStream())
        val file = runCatching { CodeWorkspace.resolve(root, path) }.getOrNull()
        if (method != "GET" && method != "HEAD") {
            writeHead(out, "405 Method Not Allowed", "text/plain", 0)
            out.flush()
            return
        }
        if (file == null || !file.isFile) {
            val body = "404 Not Found: $path".toByteArray(Charsets.UTF_8)
            writeHead(out, "404 Not Found", "text/plain; charset=utf-8", body.size)
            if (method == "GET") out.write(body)
            out.flush()
            return
        }
        val bytes = file.readBytes()
        writeHead(out, "200 OK", CodeWorkspace.mimeOf(path), bytes.size)
        if (method == "GET") out.write(bytes)
        out.flush()
    }

    private fun writeHead(out: java.io.OutputStream, status: String, mime: String, length: Int) {
        val w = OutputStreamWriter(out, Charsets.US_ASCII)
        w.write("HTTP/1.1 $status\r\n")
        w.write("Content-Type: $mime\r\n")
        w.write("Content-Length: $length\r\n")
        w.write("Cache-Control: no-store\r\n")
        w.write("Connection: close\r\n\r\n")
        w.flush()
    }

    fun stop() {
        running = false
        runCatching { socket.close() }
    }

    companion object {
        /** 起在 127.0.0.1 的随机空闲端口；失败返回 null（调用方退回虚拟源）。 */
        fun start(root: File): WorkspaceServer? = runCatching {
            val s = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
            WorkspaceServer(root, s).also { it.thread.start() }
        }.getOrNull()
    }
}
