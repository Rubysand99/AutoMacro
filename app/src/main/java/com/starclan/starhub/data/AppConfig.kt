package com.starclan.starhub.data

/**
 * Che do phat playlist:
 *  - ADDED   : theo thu tu trong danh sach (= thu tu them vao; Ruby doi cho bang nut len / xuong)
 *  - PICKED  : theo thu tu Ruby BAT (tich chon) tung bai
 *  - SHUFFLE : ngau nhien, sau moi bai chon 1 bai khac
 * `key` la gia tri luu trong SharedPreferences, KHONG doi.
 */
enum class PlayMode(val key: String) {
    ADDED("added"), PICKED("picked"), SHUFFLE("shuffle");

    companion object {
        fun fromKey(k: String?): PlayMode = values().firstOrNull { it.key == k } ?: ADDED
    }
}

/**
 * Cau hinh nhac nen (tab Config) = PLAYLIST nhieu bai. Thuan Kotlin (khong dinh Android) de kiem tra de dang.
 *  - enabled : bat -> tu phat playlist khi bat dau farm, tu dung khi farm ket thuc
 *  - tracks  : cac bai (file trong may HOAC link YouTube/TikTok/Facebook...); bai bi tat (`Track.enabled = false`) khong duoc phat
 *  - mode    : xem [PlayMode]; het danh sach thi lap lai tu dau
 *  - volume  : 0..100 (chi am luong cua nhac nen, khong anh huong chuong bao captcha)
 */
data class MusicConfig(
    val enabled: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val mode: PlayMode = PlayMode.ADDED,
    val volume: Int = DEFAULT_VOLUME
) {
    /** Co bai nao trong danh sach (ke ca bai dang tat). */
    val hasTrack: Boolean get() = tracks.isNotEmpty()

    /** So bai dang BAT (se duoc phat). */
    val playableCount: Int get() = tracks.count { it.enabled }

    val hasPlayable: Boolean get() = tracks.any { it.enabled }

    /** Chi phat khi vua bat cong tac vua co it nhat 1 bai dang bat */
    val shouldPlay: Boolean get() = enabled && hasPlayable

    /** He so am luong 0f..1f cho MediaPlayer.setVolume */
    val gain: Float get() = clampVolume(volume) / 100f

    /**
     * Cac bai se phat (vi tri trong [tracks]) theo thu tu cua che do. ADDED va SHUFFLE: theo danh sach
     * (SHUFFLE chi dung de biet tap bai duoc phat); PICKED: theo `pickOrder`, bang nhau thi theo danh sach.
     */
    fun playOrder(): List<Int> {
        val on = tracks.indices.filter { tracks[it].enabled }
        return if (mode == PlayMode.PICKED) on.sortedWith(compareBy({ tracks[it].pickOrder }, { it })) else on
    }

    /** Thu hang phat (1, 2, 3...) cua bai [index]; 0 neu bai tat hoac dang o che do ngau nhien. */
    fun rankOf(index: Int): Int {
        if (mode == PlayMode.SHUFFLE) return 0
        return playOrder().indexOf(index) + 1
    }

    /**
     * Bai ke tiep sau bai [current] (vi tri trong [tracks], -1 = chua phat bai nao); -1 neu khong co bai nao dang bat.
     * Bai dang phat bi tat giua chung: ADDED di tiep toi bai bat ke sau no trong danh sach, che do khac ve bai dau.
     */
    fun nextIndex(current: Int, random: (Int) -> Int): Int {
        val order = playOrder()
        if (order.isEmpty()) return -1
        val pos = order.indexOf(current)
        if (mode == PlayMode.SHUFFLE) return order[PlaylistRules.nextIndex(pos, order.size, true, random)]
        if (pos >= 0) return order[(pos + 1) % order.size]
        return if (mode == PlayMode.ADDED) (order.firstOrNull { it > current } ?: order[0]) else order[0]
    }

    /** Them bai moi vao cuoi danh sach (dang BAT, thu tu chon = sau cung); bo bai trung nguon, toi da [MAX_TRACKS] bai. */
    fun addTracks(extra: List<Track>): MusicConfig {
        val have = tracks.map { it.source }.toMutableSet()
        val merged = tracks.toMutableList()
        var pick = tracks.maxOfOrNull { it.pickOrder } ?: 0L
        for (t in extra) {
            if (t.source.isBlank() || merged.size >= MAX_TRACKS) continue
            if (have.add(t.source)) merged.add(t.copy(enabled = true, pickOrder = ++pick))
        }
        return copy(tracks = merged)
    }

    /** Bat / tat bai [index]. Bat = xep sau cung trong thu tu chon; tat = bo thu tu chon. Khong doi gi thi tra lai chinh no. */
    fun setTrackEnabled(index: Int, on: Boolean): MusicConfig {
        val t = tracks.getOrNull(index) ?: return this
        if (t.enabled == on) return this
        val updated = if (on) t.copy(enabled = true, pickOrder = (tracks.maxOfOrNull { it.pickOrder } ?: 0L) + 1)
        else t.copy(enabled = false, pickOrder = 0L)
        return copy(tracks = tracks.mapIndexed { i, x -> if (i == index) updated else x })
    }

    fun withMode(m: PlayMode): MusicConfig = if (m == mode) this else copy(mode = m)

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
