package com.nairocy.claude

import com.nairocy.claude.data.CodeWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 工作区沙箱：模型给的路径不能逃出 `files/users/<uid>/workspace/`。
 *
 * 这条路是「模型写文件」的唯一入口，写错一条就等于把 App 私有目录开放给模型 ——
 * 所以每条越界路径都单独钉一个用例。
 */
class CodeWorkspaceSandboxTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun root(): File = tmp.newFolder("workspace")

    @Test
    fun plainPathsResolve() {
        val r = root()
        assertEquals(File(r, "index.html").canonicalFile, CodeWorkspace.resolveIn(r, "/index.html"))
        assertEquals(File(r, "index.html").canonicalFile, CodeWorkspace.resolveIn(r, "index.html"))
        assertEquals(File(r, "assets/app.css").canonicalFile, CodeWorkspace.resolveIn(r, "assets/app.css"))
    }

    @Test
    fun windowsSeparatorsTreatedAsSlash() {
        val r = root()
        assertEquals(File(r, "assets/app.css").canonicalFile, CodeWorkspace.resolveIn(r, "assets\\app.css"))
    }

    @Test
    fun dotSegmentsIgnored() {
        val r = root()
        assertEquals(File(r, "a/b.txt").canonicalFile, CodeWorkspace.resolveIn(r, "./a/./b.txt"))
    }

    @Test
    fun parentTraversalRejected() {
        val r = root()
        assertThrows(IllegalArgumentException::class.java) { CodeWorkspace.resolveIn(r, "../secret.txt") }
        assertThrows(IllegalArgumentException::class.java) { CodeWorkspace.resolveIn(r, "/a/../../secret.txt") }
    }

    @Test
    fun absolutePathsRejected() {
        val r = root()
        assertThrows(IllegalArgumentException::class.java) { CodeWorkspace.resolveIn(r, "C:/Windows/win.ini") }
        assertThrows(IllegalArgumentException::class.java) { CodeWorkspace.resolveIn(r, "c:secret.txt") }
    }

    @Test
    fun blankOrRootRejected() {
        val r = root()
        assertThrows(IllegalArgumentException::class.java) { CodeWorkspace.resolveIn(r, "") }
        assertThrows(IllegalArgumentException::class.java) { CodeWorkspace.resolveIn(r, "   ") }
        assertThrows(IllegalArgumentException::class.java) { CodeWorkspace.resolveIn(r, "/") }
    }

    @Test
    fun illegalCharactersRejected() {
        val r = root()
        assertThrows(IllegalArgumentException::class.java) { CodeWorkspace.resolveIn(r, "a:b.txt") }
        assertThrows(IllegalArgumentException::class.java) { CodeWorkspace.resolveIn(r, "a?b.txt") }
        assertThrows(IllegalArgumentException::class.java) { CodeWorkspace.resolveIn(r, "a*b.txt") }
    }

    @Test
    fun resolvedFileAlwaysInsideRoot() {
        val r = root().canonicalFile
        val f = CodeWorkspace.resolveIn(r, "/deep/nested/page.html")
        assertEquals(true, f.path.startsWith(r.path + File.separator))
    }
}
