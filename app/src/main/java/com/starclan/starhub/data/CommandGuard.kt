package com.starclan.starhub.data

/**
 * Loc lenh nhan tu server owo-tracker TRUOC khi app go vao Discord.
 * Thuan Kotlin (khong dung Android) nen test duoc bang JVM unit test.
 *
 * Vi sao can: app go NGUYEN VAN `command_text` vao Discord cua nguoi dung. Neu token API bi lo (hoac bot bi chiem),
 * ke xau co the bat app go bat ky noi dung nao. Chi dang `owo ...` la chua du (van lot `owo give`, `owo sell`...),
 * nen day la DANH SACH CHO PHEP theo tung dong tu + dang tham so ma owo-tracker that su gui:
 *   owo hb [<so>[h|m|d] [<ma>]] · owo use <ma> [<ma>...] · owo dex <ten pet> · owo w [<ID vu khi>]
 *   owo weapon · owo cash · owo inv · owo quest · owo ws · owo zoo
 * Them lenh moi o bot thi PHAI them dung 1 dong o day (va 1 test), neu khong app se tu choi lenh do.
 */
object CommandGuard {
    const val MAX_COMMAND_LENGTH = 200
    const val MAX_CLICK_TIMES = 100
    const val MIN_CLICK_GAP_MS = 800L
    const val MAX_CLICK_GAP_MS = 10_000L

    /** Nhan nut ma server duoc phep bat app bam (chi nut lat trang cua `owo weapon`). */
    private val ALLOWED_CLICK_LABELS = setOf("forward")

    private val SAFE_COMMAND = Regex(
        "^owo (?:" +
            "(?:cash|inv|quest|ws|zoo|weapon)" +                              // khong tham so
            "|hb(?: \\d{1,7}[hHmMdD]?(?: [A-Za-z0-9]{3,12})?)?" +             // owo hb [24h [ma]]
            "|use(?: \\d{1,4}){1,12}" +                                        // owo use 051 052
            "|dex [A-Za-z0-9_]{1,32}" +                                        // owo dex <ten pet>
            "|w(?: [A-Za-z0-9]{4,8})?" +                                     // owo w (danh sach kho) hoac owo w <ID vu khi>
            ")$",
        RegexOption.IGNORE_CASE
    )

    /** true neu la 1 trong cac lenh OwO ma bot that su gui: 1 dong, dung dong tu + dung dang tham so, khong qua dai. */
    fun isSafeCommand(text: String?): Boolean {
        if (text == null) return false
        val t = text.trim()
        return t.length <= MAX_COMMAND_LENGTH && SAFE_COMMAND.matches(t)
    }

    /** true neu nhan nut nam trong danh sach cho phep (so khop sau khi cat khoang trang + chu thuong). */
    fun isAllowedClickLabel(label: String?): Boolean {
        if (label == null) return false
        return label.trim().lowercase() in ALLOWED_CLICK_LABELS
    }

    fun clampClickTimes(raw: Int?): Int = (raw ?: 50).coerceIn(1, MAX_CLICK_TIMES)

    /** Tra ve cap (min, max) da ep vao khoang an toan, min <= max. */
    fun clampClickGap(rawMin: Long?, rawMax: Long?): Pair<Long, Long> {
        val lo = (rawMin ?: 1_800L).coerceIn(MIN_CLICK_GAP_MS, MAX_CLICK_GAP_MS)
        val hi = (rawMax ?: 2_800L).coerceIn(lo, MAX_CLICK_GAP_MS)
        return lo to hi
    }
}
