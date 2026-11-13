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

    /** Nhan nut ma server duoc phep bat app bam: lat trang cua `owo weapon` ("forward") va nut chi tiet cua bot NeonUtil ("details"). */
    private val ALLOWED_CLICK_LABELS = setOf("forward", "details")

    /** Lenh duy nhat khong phai `owo ...`: xuat kho cua NeonUtil, chi bot gui o cuoi phien don kho (clean_export). */
    const val EXPORT_COMMAND = "nw inv export"

    private val SAFE_COMMAND = Regex(
        "^owo (?:" +
            "(?:cash|inv|quest|ws|zoo|weapon)" +                              // khong tham so
            "|hb(?: \\d{1,7}[hHmMdD]?(?: [A-Za-z0-9]{3,12})?)?" +             // owo hb [24h [ma]]
            "|use(?: \\d{1,4}){1,12}" +                                        // owo use 051 052
            "|dex [A-Za-z0-9_]{1,32}" +                                        // owo dex <ten pet>
            "|w(?: [A-Za-z0-9]{4,8})?" +                                     // owo w (danh sach kho) hoac owo w <ID vu khi>
            "|dismantle (?:common|uncommon|rare|epic|mythical|legendary|fabled|(?!(?:epic|mythic|mythical|legend|legendary|fabled|hidden|special|gem|all)(?![A-Za-z0-9]))[A-Za-z0-9]{4,8})" +          // don kho tu dong: ra theo bac hoac 1 ID (xem CleanupRunner)
            ")$",
        RegexOption.IGNORE_CASE
    )

    /** true neu la 1 trong cac lenh OwO ma bot that su gui: 1 dong, dung dong tu + dung dang tham so, khong qua dai. */
    fun isSafeCommand(text: String?): Boolean {
        if (text == null) return false
        val t = text.trim()
        return t.length <= MAX_COMMAND_LENGTH && (SAFE_COMMAND.matches(t) || t.equals(EXPORT_COMMAND, ignoreCase = true))
    }

    /** true neu nhan nut nam trong danh sach cho phep (so khop sau khi cat khoang trang + chu thuong). */
    fun isAllowedClickLabel(label: String?): Boolean {
        if (label == null) return false
        return label.trim().lowercase() in ALLOWED_CLICK_LABELS
    }

    fun clampClickTimes(raw: Int?): Int = (raw ?: 50).coerceIn(1, MAX_CLICK_TIMES)

    /** Thoi gian cho truoc khi bam nut dau tien (de bot khac kip tra loi): 3s..15s. */
    fun clampClickWait(raw: Long?): Long = (raw ?: 3_000L).coerceIn(3_000L, 15_000L)

    /** Tra ve cap (min, max) da ep vao khoang an toan, min <= max. */
    fun clampClickGap(rawMin: Long?, rawMax: Long?): Pair<Long, Long> {
        val lo = (rawMin ?: 1_800L).coerceIn(MIN_CLICK_GAP_MS, MAX_CLICK_GAP_MS)
        val hi = (rawMax ?: 2_800L).coerceIn(lo, MAX_CLICK_GAP_MS)
        return lo to hi
    }

    // ----------------- DON KHO TU DONG (CleanupRunner) -----------------

    /** Chu co dung ma vu khi `id` (khong phai doan giua cua chuoi dai hon). Dung de chac tin tren man hinh la cua dung cay. */
    fun textHasId(text: String?, id: String?): Boolean {
        if (text.isNullOrBlank() || id.isNullOrBlank()) return false
        return Regex("(?<![A-Za-z0-9])" + Regex.escape(id) + "(?![A-Za-z0-9])", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }

    fun isCleanAction(action: String?): Boolean = action != null && action.startsWith("clean_")

    private val COUNT_RE = Regex("(\\d[\\d,.]*)\\s+(?:[A-Za-z]+\\s+){0,2}weapons?", RegexOption.IGNORE_CASE)

    /** So cay trong tin xac nhan dismantle cua OwO ("... 49 rare weapons ..."); null neu khong doc duoc (khi do KHONG duoc bam Dismantle). */
    fun parseWeaponCount(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val m = COUNT_RE.find(text) ?: return null
        return m.groupValues[1].replace(",", "").replace(".", "").toIntOrNull()
    }

    /**
     * Co duoc bam Dismantle khong? tier: so cay doc duoc phai bang `expect` (hoac 1..expectMax); id: tin xac nhan phai co dung ID
     * (neu doc duoc so cay thi phai la 1). Khong doc duoc -> false (se bam Cancel).
     */
    fun confirmMatches(mode: String, text: String?, expect: Int?, expectMax: Int?, expectId: String?, expectMin: Int? = null): Boolean {
        if (text.isNullOrBlank()) return false
        return when (mode) {
            "tier" -> {
                val n = parseWeaponCount(text) ?: return false
                // expect = dung so; expectMin..expectMax = khoang (cay Bo o vung chua ro bac); chi expectMax = 1..expectMax
                if (expect != null) n == expect else expectMax != null && n in maxOf(1, expectMin ?: 1)..expectMax
            }
            "id" -> {
                if (expectId.isNullOrBlank()) return false
                if (!textHasId(text, expectId)) return false
                val n = parseWeaponCount(text)
                n == null || n == 1
            }
            else -> false
        }
    }
}
