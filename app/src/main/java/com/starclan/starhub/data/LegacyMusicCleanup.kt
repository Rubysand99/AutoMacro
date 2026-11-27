package com.starclan.starhub.data

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Xoa du lieu con sot cua tinh nang tai nhac bang link (go o v45): Python + yt-dlp da giai nen va nhac da tai.
 * Cap nhat app khong tu xoa cac thu muc nay. Chay lai nhieu lan vo hai.
 */
object LegacyMusicCleanup {
    const val ENGINE_NAME = "youtubedl-android"   // ten thu muc va SharedPreferences cua thu vien cu

    fun run(noBackupDir: File, filesDir: File) {
        deleteTree(File(noBackupDir, ENGINE_NAME))
        deleteTree(File(filesDir, "music"))
    }

    /** Xoa ca cay; lien ket tuong trung (goi Python co dung) chi xoa chinh no, KHONG di theo. */
    private fun deleteTree(root: File) {
        val path = root.toPath()
        if (!Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }
}
