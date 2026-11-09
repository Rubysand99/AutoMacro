package com.starclan.starhub.data

/** Giai doan cua "Don kho tu dong" (de Ruby thay app dang cho gi / lam gi). */
enum class CleanupPhase {
    OFF,         // tat (hoac chua bat)
    EXPORTING,   // chuan bi / dang go `nw inv export`
    WAIT_FILE,   // doi NeonUtil gui file + Tom lap ke hoach
    RUNNING,     // dang lam 1 buoc
    WAIT_FARM,   // co buoc cho nhung dang farm
    WAIT_NEXT,   // xong 1 buoc, doi nghi / doi Tom giao buoc ke
    DONE,        // xong het
    NO_PLAN,     // Tom khong giao buoc nao (vd con cay chua ro bac)
    FAILED       // dung vi loi
}

/**
 * Trang thai hien thi. step: RUNNING = buoc dang lam (1..total); WAIT_NEXT / WAIT_FARM / DONE = so buoc DA xong; FAILED = buoc bi loi.
 * untilMs / sinceMs: chi cho WAIT_FILE (het gio cho 45 giay / luc go export). atMs: luc doi trang thai (de an dong cu).
 */
data class CleanupStatus(
    val phase: CleanupPhase = CleanupPhase.OFF,
    val step: Int = 0,
    val total: Int = 0,
    val command: String = "",
    val detail: String = "",
    val untilMs: Long = 0L,
    val sinceMs: Long = 0L,
    val atMs: Long = 0L
) {
    /** Dang cho / dang lam: man hinh nen cap nhat moi giay. */
    val busy: Boolean
        get() = phase == CleanupPhase.EXPORTING || phase == CleanupPhase.WAIT_FILE || phase == CleanupPhase.RUNNING ||
            phase == CleanupPhase.WAIT_FARM || phase == CleanupPhase.WAIT_NEXT

    /** Da ket thuc (xong / khong co ke hoach / loi): app tu tat cong tac, dong ket qua van hien de Ruby doc. */
    val finished: Boolean
        get() = phase == CleanupPhase.DONE || phase == CleanupPhase.NO_PLAN || phase == CleanupPhase.FAILED
}

/** Chu hien thi + quyet dinh trang thai luc ranh. Thuan Kotlin (khong dung Android) de test duoc. */
object CleanupStatusText {
    /** Sau bao lau ma Tom van chua nhan file moi thi coi la that bai. */
    const val FILE_WAIT_TIMEOUT_MS = 180_000L

    /** Ke hoach cu hon luc go export hon muc nay thi coi la CU (chenh lech dong ho dien thoai / may chu). */
    const val CLOCK_SKEW_MS = 30_000L

    /** Dong ket qua (xong / loi) chi hien o dau menu trong chung nay. */
    const val FINISHED_HEADER_MS = 10 * 60_000L

    fun line(s: CleanupStatus, nowMs: Long): String = when (s.phase) {
        CleanupPhase.OFF -> s.detail
        CleanupPhase.EXPORTING ->
            "⏳ " + s.detail.ifBlank { "Đang gõ nw inv export vào Discord…" }
        CleanupPhase.WAIT_FILE -> {
            val left = (s.untilMs - nowMs + 999) / 1000
            val base = if (left > 0) {
                "⏳ Chờ NeonUtil gửi file và Tom lập kế hoạch… còn ${left}s"
            } else {
                val waited = if (s.sinceMs > 0) ((nowMs - s.sinceMs) / 1000).coerceAtLeast(0) else 0
                "⏳ Chờ Tom nhận file và lập kế hoạch… đã chờ ${waited}s"
            }
            if (s.detail.isBlank()) base else base + "\n   " + s.detail
        }
        CleanupPhase.RUNNING -> {
            val head = if (s.total > 0) "🧹 Bước ${s.step}/${s.total}: ${s.command}" else "🧹 Đang làm: ${s.command}"
            if (s.detail.isBlank()) head else head + "\n   " + s.detail
        }
        CleanupPhase.WAIT_FARM ->
            "⏸ Đang chờ vì farm đang chạy — bấm Dừng để app dọn kho (đã xong ${s.step}/${s.total} bước)"
        CleanupPhase.WAIT_NEXT ->
            "⏳ Đã xong ${s.step}/${s.total} bước. " + s.detail.ifBlank { "Chờ bước kế…" }
        CleanupPhase.DONE ->
            "✅ Đã dọn xong ${s.total}/${s.total} bước. Gõ nw inv export lần nữa để kiểm tra kho còn lại."
        CleanupPhase.NO_PLAN ->
            "ℹ️ Tom không giao bước dọn nào: " + s.detail.ifBlank { "xem tin của Tom trong Discord." }
        CleanupPhase.FAILED -> {
            val where = when {
                s.total > 0 && s.command.isNotBlank() -> "bước ${s.step}/${s.total} (${s.command})"
                s.total > 0 -> "bước ${s.step}/${s.total}"
                s.command.isNotBlank() -> s.command
                else -> "lúc bắt đầu"
            }
            "⛔ Dừng ở $where: ${s.detail}"
        }
    }

    /** Dong hien o dau menu (moi tab thay duoc): dang chay thi luon hien; xong / loi chi hien 10 phut; tat thi khong hien. */
    fun headerLine(s: CleanupStatus, nowMs: Long): String {
        val show = s.busy || (s.finished && nowMs - s.atMs < FINISHED_HEADER_MS)
        return if (show) line(s, nowMs) else ""
    }

    /**
     * Sau khi het 45 giay cho: bot dang o dau? Tra trang thai moi, hoac null = khong doi (co buoc sap chay, buoc se tu dat trang thai).
     * clean = GET /clean (null = khong hoi duoc bot); pendingClean = so buoc don kho dang cho trong /commands/pending.
     */
    fun idleStatus(
        clean: BotClean?, exportSentMs: Long, nowMs: Long, farmRunning: Boolean, pendingClean: Int, prev: CleanupStatus
    ): CleanupStatus? {
        if (pendingClean > 0) {
            return if (farmRunning) prev.copy(phase = CleanupPhase.WAIT_FARM, command = "", detail = "") else null
        }
        if (clean == null) {
            val phase = if (prev.phase == CleanupPhase.WAIT_FILE) CleanupPhase.WAIT_FILE else CleanupPhase.WAIT_NEXT
            return prev.copy(phase = phase, detail = "Không hỏi được bot, đang thử lại…")
        }
        if (clean.atMs < exportSentMs - CLOCK_SKEW_MS) {
            // Tom chua nhan file moi (phien trong bot van la cua lan truoc)
            if (nowMs - exportSentMs > FILE_WAIT_TIMEOUT_MS) {
                return CleanupStatus(
                    CleanupPhase.FAILED, 0, 0, "nw inv export",
                    "Tom không nhận được file từ NeonUtil sau ${FILE_WAIT_TIMEOUT_MS / 60_000} phút. " +
                        "Xem tin báo của Tom trong Discord (⚠️ nếu Tom bỏ qua file, tin đó nói rõ lý do).",
                    atMs = nowMs
                )
            }
            return CleanupStatus(
                CleanupPhase.WAIT_FILE, 0, 0, "", "", untilMs = exportSentMs + 45_000L, sinceMs = exportSentMs, atMs = nowMs
            )
        }
        return when {
            clean.total <= 0 -> CleanupStatus(
                CleanupPhase.NO_PLAN, 0, 0, "",
                clean.emptyReason.ifBlank { "xem tin của Tom trong Discord." }, atMs = nowMs
            )
            clean.cursor >= clean.total -> CleanupStatus(CleanupPhase.DONE, clean.total, clean.total, "", "", atMs = nowMs)
            !clean.active -> CleanupStatus(
                CleanupPhase.FAILED, clean.cursor, clean.total, "",
                "Tom đã dừng phiên dọn (gõ ,cleanstop hoặc một bước trước đó thất bại).", atMs = nowMs
            )
            else -> CleanupStatus(
                CleanupPhase.WAIT_NEXT, clean.cursor, clean.total, "", "Chờ Tom giao bước kế…", atMs = nowMs
            )
        }
    }

    /** Vi sao tin xac nhan Dismantle khong khop (de ghi vao dong loi). count = so cay doc duoc tu tin (null = khong doc duoc). */
    fun mismatchReason(mode: String, count: Int?, expect: Int?, expectMax: Int?, expectId: String?): String = when (mode) {
        "tier" -> when {
            count == null -> "không đọc được số cây trong tin xác nhận của OwO (đã bấm Cancel)"
            expect != null -> "OwO báo $count cây nhưng kế hoạch cần đúng $expect cây (đã bấm Cancel)"
            expectMax != null -> "OwO báo $count cây, ngoài khoảng 1–$expectMax của kế hoạch (đã bấm Cancel)"
            else -> "tin xác nhận không khớp kế hoạch (đã bấm Cancel)"
        }
        "id" -> "tin xác nhận không đúng cây ${expectId.orEmpty()} (đã bấm Cancel)"
        else -> "tin xác nhận không khớp kế hoạch (đã bấm Cancel)"
    }
}
