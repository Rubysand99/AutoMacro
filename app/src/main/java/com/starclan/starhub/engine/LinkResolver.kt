package com.starclan.starhub.engine

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest

/**
 * Lay LINK PHAT AM THANH truc tiep tu link YouTube / TikTok / Facebook... bang yt-dlp chay ngay trong app
 * (thu vien youtubedl-android). Dien thoai tu tai nen link phat khop IP cua chinh may.
 *
 * - Ham [resolve] CHAN luong (lan dau con giai nen python + yt-dlp, mat vai giay) -> chi goi tu luong nen.
 * - Link truc tiep cua YouTube het han sau vai gio nen KHONG luu, moi lan phat bai lai resolve lai.
 * - Trang doi cach phat lam yt-dlp hong: loi lan dau thi tu cap nhat yt-dlp 1 lan (moi lan mo app) roi thu lai.
 */
object LinkResolver {
    private const val TAG = "LinkResolver"

    /** [url] link phat truc tiep, [title] tieu de video (co the rong), [headers] header can gui kem khi tai. */
    class Resolved(val url: String, val title: String, val headers: Map<String, String>, val ext: String = "")

    class ResolveException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /** Ly do ngan gon de hien cho Ruby: dong "ERROR..." cua yt-dlp neu co, khong thi 200 ky tu dau cua thong bao. */
    fun shortReason(e: Throwable): String {
        val msg = (e.message ?: e.javaClass.simpleName).trim()
        val err = msg.lines().map { it.trim() }.lastOrNull { it.startsWith("ERROR", ignoreCase = true) }
        return (err ?: msg).take(200)
    }

    private val lock = Any()
    private var initialized = false
    private var updateTried = false

    fun resolve(context: Context, link: String): Resolved {
        val app = context.applicationContext
        ensureInit(app)
        return try {
            fetch(app, link)
        } catch (first: ResolveException) {
            // Co the yt-dlp ban cu khong doc duoc trang nua -> cap nhat 1 lan roi thu lai
            if (!tryUpdateOnce(app)) throw first
            fetch(app, link)
        }
    }

    private fun ensureInit(app: Context) {
        synchronized(lock) {
            if (initialized) return
            try {
                YoutubeDL.getInstance().init(app)
                initialized = true
            } catch (e: Exception) {
                Log.w(TAG, "Khong khoi dong duoc yt-dlp: ${e.message}")
                throw ResolveException("khong khoi dong duoc yt-dlp: ${e.message}", e)
            }
        }
    }

    private fun tryUpdateOnce(app: Context): Boolean {
        synchronized(lock) {
            if (updateTried) return false
            updateTried = true
        }
        return try {
            val st = YoutubeDL.getInstance().updateYoutubeDL(app)
            Log.i(TAG, "Cap nhat yt-dlp: $st")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Cap nhat yt-dlp loi: ${e.message}")
            false
        }
    }

    /** Thu muc tai am thanh tam; chi giu [KEEP] file moi nhat de khong day bo nho. */
    private const val KEEP = 15

    private fun cacheDir(app: Context): java.io.File =
        java.io.File(app.cacheDir, "music").apply { mkdirs() }

    private fun hash(link: String): String = Integer.toHexString(link.hashCode()) + "_" + link.length

    private fun findCached(dir: java.io.File, key: String): java.io.File? =
        dir.listFiles { f -> f.isFile && f.name.startsWith("$key.") && !f.name.endsWith(".part") && f.length() > 0 }
            ?.firstOrNull()

    private fun prune(dir: java.io.File) {
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(KEEP).forEach { it.delete() }
    }

    private fun fetch(app: Context, link: String): Resolved {
        try {
            val req = YoutubeDLRequest(link)
            // Uu tien AAC (m4a) vi MediaPlayer cua Android phat on dinh nhat; khong co thi lay am thanh khac, cuoi cung ban tot nhat (am thanh van phat)
            req.addOption("-f", "bestaudio[ext=m4a]/bestaudio[acodec^=mp4a]/bestaudio/best")
            req.addOption("--no-playlist")          // dan link video nam trong danh sach phat thi chi lay video do
            req.addOption("--socket-timeout", 15)
            val info = YoutubeDL.getInstance().getInfo(req)
            val direct = info.url?.takeIf { it.isNotBlank() }
                ?: info.requestedFormats?.firstOrNull { !it.url.isNullOrBlank() }?.url
            if (direct.isNullOrBlank()) throw ResolveException("khong co link phat")
            val title = (info.title ?: info.fulltitle).orEmpty().trim()
            // Link truc tiep cua YouTube hay bi 403 khi trinh phat tu tai -> de yt-dlp tai file ve (no tai tung doan nho, khong bi chan)
            val dir = cacheDir(app)
            val key = hash(link)
            var file = findCached(dir, key)
            if (file == null) {
                val dl = YoutubeDLRequest(link)
                dl.addOption("-f", "bestaudio[ext=m4a]/bestaudio[acodec^=mp4a]/bestaudio/best")
                dl.addOption("--no-playlist")
                dl.addOption("--socket-timeout", 15)
                dl.addOption("-o", dir.absolutePath + "/" + key + ".%(ext)s")
                YoutubeDL.getInstance().execute(dl)
                file = findCached(dir, key) ?: throw ResolveException("tai xong nhung khong thay file am thanh")
            }
            file.setLastModified(System.currentTimeMillis())
            prune(dir)
            return Resolved(
                url = android.net.Uri.fromFile(file).toString(),
                title = title,
                headers = emptyMap(),
                ext = file.extension
            )
        } catch (e: ResolveException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Khong lay duoc am thanh: ${e.message}")
            throw ResolveException(e.message ?: "loi yt-dlp", e)
        }
    }
}
