package com.starclan.starhub.data

/**
 * Loc lenh nhan tu server owo-tracker TRUOC khi app go vao Discord.
 * Thuan Kotlin (khong dung Android) nen test duoc bang JVM unit test.
 *
 * Vi sao can: app go NGUYEN VAN `command_text` vao Discord cua nguoi dung. Neu token API bi lo (hoac bot bi chiem),
 * ke xau co the bat app go bat ky noi dung nao. Moi lenh hop le cua bot deu la lenh OwO dang `owo ...`
 * (owo hb, owo use, owo dex, owo w, owo weapon, owo zoo...), nen chi cho phep dung dang do.
 */
object CommandGuard {
    const val MAX_COMMAND_LENGTH = 200
    const val MAX_CLICK_TIMES = 100
    const val MIN_CLICK_GAP_MS = 800L
    const val MAX_CLICK_GAP_MS = 10_000L

    private val SAFE_COMMAND = Regex("^owo [\\p{L}\\p{N} _.:<>/\\-]{1,${MAX_COMMAND_LENGTH - 4}}$", RegexOption.IGNORE_CASE)

    /** true neu la lenh OwO 1 dong, khong ky tu dieu khien, khong qua dai. */
    fun isSafeCommand(text: String?): Boolean {
        if (text == null) return false
        val t = text.trim()
        return t.length <= MAX_COMMAND_LENGTH && SAFE_COMMAND.matches(t)
    }

    fun clampClickTimes(raw: Int?): Int = (raw ?: 50).coerceIn(1, MAX_CLICK_TIMES)

    /** Tra ve cap (min, max) da ep vao khoang an toan, min <= max. */
    fun clampClickGap(rawMin: Long?, rawMax: Long?): Pair<Long, Long> {
        val lo = (rawMin ?: 1_800L).coerceIn(MIN_CLICK_GAP_MS, MAX_CLICK_GAP_MS)
        val hi = (rawMax ?: 2_800L).coerceIn(lo, MAX_CLICK_GAP_MS)
        return lo to hi
    }
}
