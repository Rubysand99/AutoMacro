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
    class Resolved(val url: String, val title: String, val headers: Map<String, String>)

    class ResolveException(message: String, cause: Throwable? = null) : Exception(message, cause)

    private val lock = Any()
    private var initialized = false
    private var updateTried = false

    fun resolve(context: Context, link: String): Resolved {
        val app = context.applicationContext
        ensureInit(app)
        return try {
            fetch(link)
        } catch (first: ResolveException) {
            // Co the yt-dlp ban cu khong doc duoc trang nua -> cap nhat 1 lan roi thu lai
            if (!tryUpdateOnce(app)) throw first
            fetch(link)
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
                throw ResolveException("init: ${e.message}", e)
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

    private fun fetch(link: String): Resolved {
        try {
            val req = YoutubeDLRequest(link)
            req.addOption("-f", "bestaudio/best")   // uu tien chi am thanh; trang nao khong tach rieng thi lay ban tot nhat (am thanh van phat)
            req.addOption("--no-playlist")          // dan link video nam trong danh sach phat thi chi lay video do
            req.addOption("--socket-timeout", 15)
            val info = YoutubeDL.getInstance().getInfo(req)
            val direct = info.url?.takeIf { it.isNotBlank() }
                ?: info.requestedFormats?.firstOrNull { !it.url.isNullOrBlank() }?.url
            if (direct.isNullOrBlank()) throw ResolveException("khong co link phat")
            return Resolved(
                url = direct,
                title = (info.title ?: info.fulltitle).orEmpty().trim(),
                headers = info.httpHeaders.orEmpty()
            )
        } catch (e: ResolveException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Khong lay duoc am thanh: ${e.message}")
            throw ResolveException(e.message ?: "loi yt-dlp", e)
        }
    }
}
