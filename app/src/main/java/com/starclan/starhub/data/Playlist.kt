package com.starclan.starhub.data

import com.google.gson.Gson

/**
 * 1 bai trong playlist nhac nen.
 *  - source : `content://...` (file Ruby chon trong may)
 *  - title  : ten hien thi; rong thi hien nguon
 *  - enabled   : false -> bai van nam trong danh sach nhung KHONG duoc phat (Ruby tat tung bai)
 *  - pickOrder : thu tu Ruby BAT bai nay (so cang nho cang phat truoc o che do "theo thu tu chon"); 0 = chua ghi (bai cu / chua chon)
 */
data class Track(val source: String, val title: String = "", val enabled: Boolean = true, val pickOrder: Long = 0L) {
    /** Ten de hien cho Ruby: ten da biet, neu chua co thi la nguon. */
    val displayName: String
        get() = title.ifBlank { source }
}

/** Bai tu link web (tinh nang da go o v45): ban cu co the con luu trong playlist nen phai loai khi doc. */
private fun isWebLink(source: String): Boolean =
    source.startsWith("http://", ignoreCase = true) || source.startsWith("https://", ignoreCase = true)

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
                if (src.isEmpty() || isWebLink(src)) null else Track(src, s?.title.orEmpty(), s?.enabled ?: true, (s?.pick ?: 0L).coerceAtLeast(0L))
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
