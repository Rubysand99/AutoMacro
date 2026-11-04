package com.starclan.starhub.data

/**
 * Toc do farm: 3 muc. Chi doi NHIP go lenh (do dai 1 vong = cooldown cua cung 1 lenh, tan suat nghi giai lao);
 * giua 2 lenh KHAC nhau (hunt -> battle) khong nghi;
 * gioi han "toi da 3 lenh / 12 giay" va cooldown that cua OwO (~15 giay) KHONG doi o bat ky muc nao.
 * NHANH nhieu hunt/battle hon nhung gan nhip may hon -> rui ro bi OwO nghi ngo / khoa cao hon; CHAM an toan hon.
 */
enum class SpeedMode(
    val id: Int,
    val label: String,
    val cycleMinMs: Long, val cycleMaxMs: Long,      // do dai 1 vong hunt+battle (toi thieu ~ cooldown cua OwO)
    val restEveryMin: Int, val restEveryMax: Int,    // sau bao nhieu vong thi nghi giai lao
    val restMinMs: Long, val restMaxMs: Long
) {
    FAST(1, "Nhanh", 15_800L, 18_500L, 60, 110, 60_000L, 150_000L),
    NORMAL(2, "Thường", 16_500L, 21_000L, 45, 80, 90_000L, 240_000L),
    SLOW(3, "Chậm", 24_000L, 32_000L, 30, 55, 120_000L, 300_000L);

    companion object {
        /** id luu trong SharedPreferences -> muc; id la/loi -> NORMAL */
        fun fromId(id: Int): SpeedMode = values().firstOrNull { it.id == id } ?: NORMAL
    }
}
