package com.starclan.starhub.data

import com.google.gson.Gson

/**
 * 1 bai trong playlist nhac nen.
 *  - source : `content://...` (file Ruby chon trong may) hoac link `http(s)://` (YouTube, TikTok, Facebook...; app tu tach am thanh bang yt-dlp)
 *  - title  : ten hien thi; rong thi hien nguon. Link duoc ghi ten that (tieu de video) sau lan phat dau tien.
 *  - enabled   : false -> bai van nam trong danh sach nhung KHONG duoc phat (Ruby tat tung bai)
 *  - pickOrder : thu tu Ruby BAT bai nay (so cang nho cang phat truoc o che do "theo thu tu chon"); 0 = chua ghi (bai cu / chua chon)
 */
data class Track(val source: String, val title: String = "", val enabled: Boolean = true, val pickOrder: Long = 0L) {
    val isLink: Boolean get() = MusicLinks.isLink(source)

    /** Ten de hien cho Ruby: ten da biet, neu chua co thi ten trang + id ngan cua link, con lai la nguon. */
    val displayName: String
        get() = title.ifBlank { if (isLink) MusicLinks.shortLabel(source) else source }
}

/** Nhan dien va tach link dan vao (co the dan nhieu link 1 lan, moi dong 1 link hoac cach nhau bang khoang trang). */
object MusicLinks {
    private val URL_REGEX = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private const val TRAILING = ".,;:!?)]}。"

    fun isLink(s: String): Boolean {
        val t = s.trim().lowercase()
        return t.startsWith("http://") || t.startsWith("https://")
    }

    /** Lay moi link hop le trong doan chu Ruby dan; bo trung, giu thu tu. */
    fun extract(text: String): List<String> {
        val out = LinkedHashSet<String>()
        for (m in URL_REGEX.findAll(text)) {
            val url = m.value.trimEnd { it in TRAILING }
            if (host(url).contains('.')) out.add(url)
        }
        return out.toList()
    }

    /** Ten mien viet thuong, khong "www."/"m."; rong neu khong tach duoc. */
    fun host(url: String): String {
        val rest = url.trim().substringAfter("://", "")
        val h = rest.substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@').substringBefore(':').lowercase()
        return h.removePrefix("www.").removePrefix("m.")
    }

    /** Ten trang cho de doc: YouTube / TikTok / Facebook, trang khac thi ten mien. */
    fun siteName(url: String): String {
        val h = host(url)
        return when {
            h == "youtu.be" || h.endsWith("youtube.com") || h.endsWith("youtube-nocookie.com") -> "YouTube"
            h.endsWith("tiktok.com") -> "TikTok"
            h == "fb.watch" || h == "fb.com" || h.endsWith("facebook.com") -> "Facebook"
            else -> h
        }
    }

    /** "YouTube · dQw4w9WgXcQ" (id ngan cua link) cho den khi co tieu de that. */
    fun shortLabel(url: String): String {
        val tail = url.trim().substringAfter("://", "").substringAfter('/', "")
            .substringBefore('#').trimEnd('/').let { t ->
                val v = Regex("""[?&]v=([^&]+)""").find(t)?.groupValues?.get(1)
                v ?: t.substringBefore('?').substringAfterLast('/')
            }
        return if (tail.isBlank()) siteName(url) else siteName(url) + " · " + tail.take(24)
    }
}

/** Luu / doc playlist dang JSON (cho SharedPreferences). Doc loi hoac du lieu hong -> danh sach rong, khong nem loi. */
object PlaylistJson {
    private class Stored(val source: String? = null, val title: String? = null, val enabled: Boolean? = null, val pick: Long? = null)
    private val gson = Gson()

    fun encode(tracks: List<Track>): String =
        gson.toJson(tracks.map { Stored(it.source, it.title, it.enabled, it.pickOrder) })

    fun decode(json: String?): List<Track> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = gson.fromJson(json, Array<Stored>::class.java) ?: return emptyList()
            arr.mapNotNull { s ->
                val src = s?.source?.trim().orEmpty()
                if (src.isEmpty()) null else Track(src, s?.title.orEmpty(), s?.enabled ?: true, (s?.pick ?: 0L).coerceAtLeast(0L))
            }.take(MusicConfig.MAX_TRACKS)
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/** Doc playlist da luu, kem chuyen cau hinh CU (1 file duy nhat) len playlist. */
fun loadTracks(json: String?, legacyUri: String?, legacyName: String?): List<Track> {
    if (!json.isNullOrBlank()) return PlaylistJson.decode(json)   // da co playlist (ke ca "[]" do Ruby xoa het) -> khong hoi sinh file cu
    val uri = legacyUri?.trim().orEmpty()
    return if (uri.isEmpty()) emptyList() else listOf(Track(uri, legacyName?.trim().orEmpty()))
}

/** Doc che do phat da luu; chua co thi doi tu cong tac ngau nhien cu (true -> SHUFFLE, con lai -> theo thu tu them). */
fun loadMode(key: String?, legacyShuffle: Boolean): PlayMode =
    if (key.isNullOrBlank()) (if (legacyShuffle) PlayMode.SHUFFLE else PlayMode.ADDED) else PlayMode.fromKey(key)

/** Luat chuyen bai (thuan, de test). */
object PlaylistRules {
    /** Bai ke tiep: tuan tu (het thi quay lai bai dau) hoac ngau nhien khac bai hien tai. -1 neu danh sach rong. */
    fun nextIndex(current: Int, size: Int, shuffle: Boolean, random: (Int) -> Int): Int {
        if (size <= 0) return -1
        if (size == 1) return 0
        val hasCurrent = current in 0 until size
        if (!shuffle) return if (hasCurrent) (current + 1) % size else 0
        // chua phat bai nao: chon trong ca danh sach; dang phat: chon trong (size - 1) bai con lai, bo qua bai hien tai
        if (!hasCurrent) return random(size).coerceIn(0, size - 1)
        var n = random(size - 1).coerceIn(0, size - 2)
        if (n >= current) n++
        return n
    }

    /** Bo cuoc khi da thu tat ca cac bai ma bai nao cung loi lien tiep (tranh lap vo han khi mat mang). */
    fun shouldGiveUp(consecutiveFailures: Int, size: Int): Boolean = size <= 0 || consecutiveFailures >= size
}
