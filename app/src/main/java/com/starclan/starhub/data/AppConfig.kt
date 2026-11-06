package com.starclan.starhub.data

/**
 * Cau hinh nhac nen (tab Config). Thuan Kotlin (khong dinh Android) de kiem tra de dang.
 *  - enabled : bat -> tu phat nhac khi bat dau farm, tu dung khi farm ket thuc
 *  - uri     : file nhac Ruby chon (content:// cua may), rong = chua chon
 *  - name    : ten file de hien cho Ruby
 *  - volume  : 0..100 (chi am luong cua nhac nen, khong anh huong chuong bao captcha)
 */
data class MusicConfig(
    val enabled: Boolean = false,
    val uri: String = "",
    val name: String = "",
    val volume: Int = DEFAULT_VOLUME
) {
    val hasTrack: Boolean get() = uri.isNotBlank()

    /** Chi phat khi vua bat cong tac vua co file nhac */
    val shouldPlay: Boolean get() = enabled && hasTrack

    /** He so am luong 0f..1f cho MediaPlayer.setVolume */
    val gain: Float get() = clampVolume(volume) / 100f

    companion object {
        const val DEFAULT_VOLUME = 50
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
