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
    private val dlLock = Any()
    private var initialized = false
    private var updateTried = false

    fun resolve(context: Context, link: String): Resolved {
        val app = context.applicationContext
        // Bai da tai roi thi phat luon, khong dung toi mang / yt-dlp
        val dir = cacheDir(app)
        findCached(dir, hash(link))?.let { return fromFile(it, "") }
        // Tai tuan tu: bai dang tai truoc (prefetch) xong roi bai can phat moi vao, khong ghi chong len cung 1 file
        synchronized(dlLock) {
            findCached(dir, hash(link))?.let { return fromFile(it, "") }
            ensureInit(app)
            return try {
                fetch(app, link)
            } catch (first: ResolveException) {
                // Co the yt-dlp ban cu khong doc duoc trang nua -> cap nhat 1 lan roi thu lai
                if (!tryUpdateOnce(app)) throw first
                fetch(app, link)
            }
        }
    }

    /** Bai nay da tai ve may chua (de bo qua tai truoc). */
    fun isCached(context: Context, link: String): Boolean =
        findCached(cacheDir(context.applicationContext), hash(link)) != null

    private fun fromFile(f: java.io.File, title: String): Resolved {
        f.setLastModified(System.currentTimeMillis())
        return Resolved(android.net.Uri.fromFile(f).toString(), title, emptyMap(), f.extension)
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
    private const val KEEP = 30

    private fun cacheDir(app: Context): java.io.File =
        java.io.File(app.filesDir, "music").apply { mkdirs() }   // filesDir: Android khong tu xoa khi thieu bo nho nhu cacheDir

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
            val dir = cacheDir(app)
            val key = hash(link)
            // MOT lan goi yt-dlp duy nhat: vua tai am thanh vua in ten video (truoc day tach link roi tai = doc trang 2 lan)
            val req = YoutubeDLRequest(link)
            // Uu tien AAC (m4a) vi phat on dinh nhat; khong co thi lay am thanh khac, cuoi cung ban tot nhat
            req.addOption("-f", "bestaudio[ext=m4a]/bestaudio[acodec^=mp4a]/bestaudio/best")
            req.addOption("--no-playlist")
            req.addOption("--socket-timeout", 15)
            req.addOption("--print", "%(title)s")
            req.addOption("--no-simulate")
            req.addOption("-o", dir.absolutePath + "/" + key + ".%(ext)s")
            val resp = YoutubeDL.getInstance().execute(req)
            val title = resp.out.orEmpty().lines().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
            val file = findCached(dir, key) ?: throw ResolveException("tai xong nhung khong thay file am thanh")
            prune(dir)
            return fromFile(file, title)
        } catch (e: ResolveException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Khong lay duoc am thanh: ${e.message}")
            throw ResolveException(e.message ?: "loi yt-dlp", e)
        }
    }
}
