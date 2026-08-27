package app.termora.transfer

import app.termora.transfer.PathWalker.EmptyBasicFileAttributes.Companion.INSTANCE
import org.apache.sshd.sftp.client.SftpClient
import org.apache.sshd.sftp.client.fs.SftpFileSystem
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import kotlin.io.path.absolutePathString

object PathWalker {

    fun walkFileTree(path: Path, visitor: FileVisitor<Path>) {
        if (path.fileSystem is SftpFileSystem) {
            val fileSystem = path.fileSystem as SftpFileSystem
            fileSystem.client.use { walkFileTree(path, it, visitor) }
        } else {
            Files.walkFileTree(path, visitor)
        }

    }

    private fun walkFileTree(path: Path, sftpClient: SftpClient, visitor: FileVisitor<Path>): Boolean {
        if (visitor.preVisitDirectory(path, INSTANCE) == FileVisitResult.TERMINATE) {
            return false
        }
        for (e in sftpClient.readDir(path.absolutePathString())) {
            if (e.filename == ".." || e.filename == ".") {
                continue
            }
            if (e.attributes.isDirectory) {
                if (walkFileTree(path.resolve(e.filename), sftpClient, visitor).not()) {
                    return false
                }
            } else {
                if (visitor.visitFile(path.resolve(e.filename), INSTANCE) == FileVisitResult.TERMINATE) {
                    return false
                }
            }
        }
        return visitor.postVisitDirectory(path, null) == FileVisitResult.CONTINUE
    }


    private class EmptyBasicFileAttributes : BasicFileAttributes {
        companion object {
            val INSTANCE = EmptyBasicFileAttributes()
        }

        override fun lastModifiedTime(): FileTime {
            TODO("Not yet implemented")
        }

        override fun lastAccessTime(): FileTime {
            TODO("Not yet implemented")
        }

        override fun creationTime(): FileTime {
            TODO("Not yet implemented")
        }

        override fun isRegularFile(): Boolean {
            TODO("Not yet implemented")
        }

        override fun isDirectory(): Boolean {
            TODO("Not yet implemented")
        }

        override fun isSymbolicLink(): Boolean {
            TODO("Not yet implemented")
        }

        override fun isOther(): Boolean {
            TODO("Not yet implemented")
        }

        override fun size(): Long {
            TODO("Not yet implemented")
        }

        override fun fileKey(): Any {
            TODO("Not yet implemented")
        }

    }

}

/**
 * 解析符号链接的目标路径（跟随链接）。
 *
 * 符号链接的目标可能是相对路径（如 `real-dir` 或 `../foo`），也可能是绝对路径。
 *
 * @param path 符号链接路径
 * @return 链接目标的 [Path]；如果无法解析或读取失败则返回 null
 */
internal fun resolveSymbolicLink(path: Path): Path? {
    return try {
        val target = Files.readSymbolicLink(path)
        if (target.isAbsolute) {
            path.fileSystem.getPath(target.toString())
        } else {
            path.resolveSibling(target)
        }
    } catch (_: Exception) {
        null
    }
}
