package app.termora

import app.termora.transfer.resolveSymbolicLink
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/**
 * 符号链接目标解析：用于传输面板中
 * 目录符号链接（双击进入）和文件符号链接（按链接指向的文件传输/下载）。
 */
class ResolveSymbolicLinkTest {

    private fun tempDir(): Path = Files.createTempDirectory("termora-symlink-test")

    @Test
    fun `resolve directory symlink`() {
        val dir = tempDir()
        val realDir = dir.resolve("real-dir").createDirectories()
        val link = dir.resolve("link-dir")
        Files.createSymbolicLink(link, realDir)

        val resolved = resolveSymbolicLink(link)
        assertNotNull(resolved)
        assertEquals(realDir.absolutePathString(), resolved.absolutePathString())
        assertEquals(true, Files.isDirectory(resolved))
    }

    @Test
    fun `resolve file symlink`() {
        val dir = tempDir()
        val realFile = dir.resolve("real-file.txt")
        realFile.writeText("hello")
        val link = dir.resolve("link-file")
        Files.createSymbolicLink(link, realFile)

        val resolved = resolveSymbolicLink(link)
        assertNotNull(resolved)
        assertEquals(realFile.absolutePathString(), resolved.absolutePathString())
        assertEquals(true, Files.isRegularFile(resolved))
    }

    @Test
    fun `resolve relative target`() {
        val dir = tempDir()
        val realDir = dir.resolve("real-dir").createDirectories()
        val link = dir.resolve("link-relative")
        Files.createSymbolicLink(link, Path.of("real-dir"))

        val resolved = resolveSymbolicLink(link)
        assertNotNull(resolved)
        assertEquals(realDir.absolutePathString(), resolved.absolutePathString())
    }

    @Test
    fun `resolve absolute target`() {
        val dir = tempDir()
        val realFile = dir.resolve("real-abs.txt")
        realFile.writeText("x")
        val link = dir.resolve("link-abs")
        Files.createSymbolicLink(link, realFile)

        val resolved = resolveSymbolicLink(link)
        assertNotNull(resolved)
        assertEquals(realFile.absolutePathString(), resolved.absolutePathString())
    }

    @Test
    fun `broken symlink returns target path`() {
        val dir = tempDir()
        val link = dir.resolve("link-broken")
        Files.createSymbolicLink(link, Path.of("no-such-target"))

        val resolved = resolveSymbolicLink(link)
        assertNotNull(resolved)
        assertEquals(false, Files.isDirectory(resolved))
        assertEquals(false, Files.isRegularFile(resolved))
    }

    @Test
    fun `non symlink returns null`() {
        val dir = tempDir()
        val file = dir.resolve("plain.txt")
        file.writeText("x")

        assertNull(resolveSymbolicLink(file))
    }

    @Test
    fun `symlink to symlink is resolved one level`() {
        val dir = tempDir()
        val realDir = dir.resolve("real-dir").createDirectories()
        val mid = dir.resolve("mid")
        Files.createSymbolicLink(mid, Path.of("real-dir"))
        val top = dir.resolve("top")
        Files.createSymbolicLink(top, mid)

        val resolved = resolveSymbolicLink(top)
        assertNotNull(resolved)
        // 解析一级后仍是指向 real-dir 的符号链接
        assertEquals(mid.absolutePathString(), resolved.absolutePathString())
        assertEquals(true, Files.isDirectory(resolved))
    }

    @Test
    fun `local symlink logical type follows target`() {
        val dir = tempDir()
        val realDir = dir.resolve("real").createDirectories()
        val realFile = dir.resolve("f.txt").also { it.writeText("x") }
        val linkDir = dir.resolve("ld")
        val linkFile = dir.resolve("lf")
        Files.createSymbolicLink(linkDir, Path.of("real"))
        Files.createSymbolicLink(linkFile, Path.of("f.txt"))

        // 模拟 getAttributes 本地分支：NOFOLLOW 识别链接本身，再解析目标得到逻辑类型
        fun logicalType(link: Path): Pair<Boolean, Boolean> {
            val basic = Files.readAttributes(link, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            assertEquals(true, basic.isSymbolicLink)
            val resolved = resolveSymbolicLink(link)
            assertNotNull(resolved)
            return Files.isDirectory(resolved) to Files.isRegularFile(resolved)
        }

        // 目录符号链接：类型按链接识别，逻辑类型为目录
        assertEquals(true to false, logicalType(linkDir))
        // 文件符号链接：类型按链接识别，逻辑类型为文件
        assertEquals(false to true, logicalType(linkFile))
    }
}
