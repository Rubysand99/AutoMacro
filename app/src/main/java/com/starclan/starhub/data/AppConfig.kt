package com.starclan.starhub.data

/**
 * Cau hinh nhac nen (tab Config) = PLAYLIST nhieu bai. Thuan Kotlin (khong dinh Android) de kiem tra de dang.
 *  - enabled : bat -> tu phat playlist khi bat dau farm, tu dung khi farm ket thuc
 *  - tracks  : cac bai theo thu tu phat (file trong may HOAC link YouTube/TikTok/Facebook...), het danh sach thi lap lai tu dau
 *  - shuffle : true -> sau moi bai chon ngau nhien 1 bai khac
 *  - volume  : 0..100 (chi am luong cua nhac nen, khong anh huong chuong bao captcha)
 */
data class MusicConfig(
    val enabled: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val shuffle: Boolean = false,
    val volume: Int = DEFAULT_VOLUME
) {
    val hasTrack: Boolean get() = tracks.isNotEmpty()

    /** Chi phat khi vua bat cong tac vua co it nhat 1 bai */
    val shouldPlay: Boolean get() = enabled && hasTrack

    /** He so am luong 0f..1f cho MediaPlayer.setVolume */
    val gain: Float get() = clampVolume(volume) / 100f

    /** Them bai moi vao cuoi danh sach; bo bai trung nguon, toi da [MAX_TRACKS] bai. */
    fun addTracks(extra: List<Track>): MusicConfig {
        val have = tracks.map { it.source }.toMutableSet()
        val merged = tracks.toMutableList()
        for (t in extra) {
            if (t.source.isBlank() || merged.size >= MAX_TRACKS) continue
            if (have.add(t.source)) merged.add(t)
        }
        return copy(tracks = merged)
    }

    fun removeAt(index: Int): MusicConfig =
        if (index !in tracks.indices) this else copy(tracks = tracks.filterIndexed { i, _ -> i != index })

    /** Doi cho bai [index] len (delta = -1) hoac xuong (delta = +1); ra ngoai danh sach thi giu nguyen. */
    fun move(index: Int, delta: Int): MusicConfig {
        val to = index + delta
        if (index !in tracks.indices || to !in tracks.indices || delta == 0) return this
        val list = tracks.toMutableList()
        val t = list.removeAt(index)
        list.add(to, t)
        return copy(tracks = list)
    }

    /** Ghi ten that (tieu de video) cho bai co nguon [source]; khong co bai do thi giu nguyen. */
    fun withTitle(source: String, title: String): MusicConfig {
        if (title.isBlank() || tracks.none { it.source == source && it.title != title }) return this
        return copy(tracks = tracks.map { if (it.source == source) it.copy(title = title) else it })
    }

    companion object {
        const val DEFAULT_VOLUME = 50
        const val MAX_TRACKS = 100
        fun clampVolume(v: Int): Int = v.coerceIn(0, 100)
    }
}

/**
 * Lua chon cua tab Cong cu luu lai tren may de lan sau mo tab thay ngay, khong phai cho tai / bam lai.
 * `captchaActive` khong luu (la trang thai tam thoi cua bot).
 */
object ToolsCache {
    fun forStorage(s: BotSettings): BotSettings = s.copy(captchaActive = false)
}
