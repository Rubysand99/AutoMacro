package com.starclan.starhub.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class LegacyMusicCleanupTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun removes_old_engine_and_downloads_but_nothing_else() {
        val noBackup = tmp.newFolder("no_backup")
        val files = tmp.newFolder("files")
        val engine = File(noBackup, "youtubedl-android/packages/python").apply { mkdirs() }
        File(engine, "libx.so").writeText("x")
        File(files, "music").mkdirs()
        File(files, "music/a.m4a").writeText("x")
        val keep = File(files, "stats.json").apply { writeText("x") }
        val outside = tmp.newFolder("outside")
        val precious = File(outside, "precious").apply { writeText("x") }
        Files.createSymbolicLink(File(engine, "link").toPath(), outside.toPath())

        LegacyMusicCleanup.run(noBackup, files)
        LegacyMusicCleanup.run(noBackup, files)   // chay lai khong loi

        assertFalse(File(noBackup, "youtubedl-android").exists())
        assertFalse(File(files, "music").exists())
        assertTrue(keep.exists())
        assertTrue(precious.exists())
    }
}
