package com.nairocy.claude

import com.nairocy.claude.data.WorkspaceServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 工作区预览用的本地 HTTP 服务：真网络响应，`vh`/相对路径才正常。
 *
 * 必须盯住两条：能拿到文件字节；`..` 这类越界路径拿不到东西（走的是同一套沙箱）。
 */
class WorkspaceServerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var root: File
    private var server: WorkspaceServer? = null

    @Before
    fun setUp() {
        root = tmp.newFolder("ws")
        File(root, "index.html").writeText("<!doctype html><h1>太阳</h1>", Charsets.UTF_8)
        File(root, "assets").mkdirs()
        File(root, "assets/app.css").writeText("body{color:#fff}", Charsets.UTF_8)
        server = WorkspaceServer.start(root)
    }

    @After
    fun tearDown() {
        server?.stop()
    }

    private fun get(path: String): Pair<Int, String> {
        val port = server!!.port
        val conn = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        conn.connectTimeout = 2000
        conn.readTimeout = 2000
        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        return code to body
    }

    @Test
    fun servesHtmlWithRealResponse() {
        val (code, body) = get("/index.html")
        assertEquals(200, code)
        assertTrue(body.contains("太阳"))
    }

    @Test
    fun servesNestedAsset() {
        val (code, body) = get("/assets/app.css")
        assertEquals(200, code)
        assertTrue(body.contains("body{color:#fff}"))
    }

    @Test
    fun missingFileIs404() {
        val (code, _) = get("/nope.html")
        assertEquals(404, code)
    }

    @Test
    fun traversalRejected() {
        val (code, _) = get("/../secret.txt")
        assertEquals(404, code)
    }

    @Test
    fun contentTypeIsCorrect() {
        val port = server!!.port
        val conn = URL("http://127.0.0.1:$port/index.html").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connect()
        assertTrue(conn.contentType.startsWith("text/html"))
        conn.disconnect()
    }
}
