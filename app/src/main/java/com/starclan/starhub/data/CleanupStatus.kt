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
            "⏳ " + s.detail.ifBlank { Texts.get("cleanst_exporting_default") }
        CleanupPhase.WAIT_FILE -> {
            val left = (s.untilMs - nowMs + 999) / 1000
            val base = if (left > 0) {
                "⏳ " + Texts.get("cleanst_wait_file_left", left)
            } else {
                val waited = if (s.sinceMs > 0) ((nowMs - s.sinceMs) / 1000).coerceAtLeast(0) else 0
                "⏳ " + Texts.get("cleanst_wait_file_waited", waited)
            }
            if (s.detail.isBlank()) base else base + "\n   " + s.detail
        }
        CleanupPhase.RUNNING -> {
            val head = "🧹 " + if (s.total > 0) Texts.get("cleanst_running_step", s.step, s.total, s.command)
            else Texts.get("cleanst_running_nototal", s.command)
            if (s.detail.isBlank()) head else head + "\n   " + s.detail
        }
        CleanupPhase.WAIT_FARM ->
            "⏸ " + Texts.get("cleanst_wait_farm", s.step, s.total)
        CleanupPhase.WAIT_NEXT ->
            "⏳ " + Texts.get("cleanst_wait_next", s.step, s.total) + " " + s.detail.ifBlank { Texts.get("cleanst_wait_next_default") }
        CleanupPhase.DONE ->
            "✅ " + Texts.get("cleanst_done", s.total)
        CleanupPhase.NO_PLAN ->
            "ℹ️ " + Texts.get("cleanst_no_plan") + " " + s.detail.ifBlank { Texts.get("cleanst_no_plan_default") }
        CleanupPhase.FAILED -> {
            val where = when {
                s.total > 0 && s.command.isNotBlank() -> Texts.get("cleanst_failed_at_step_cmd", s.step, s.total, s.command)
                s.total > 0 -> Texts.get("cleanst_failed_at_step", s.step, s.total)
                s.command.isNotBlank() -> s.command
                else -> Texts.get("cleanst_failed_at_start")
            }
            "⛔ " + Texts.get("cleanst_failed", where, s.detail)
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
            return prev.copy(phase = phase, detail = Texts.get("cleanst_bot_unreachable"))
        }
        if (clean.atMs < exportSentMs - CLOCK_SKEW_MS) {
            // Tom chua nhan file moi (phien trong bot van la cua lan truoc)
            if (nowMs - exportSentMs > FILE_WAIT_TIMEOUT_MS) {
                return CleanupStatus(
                    CleanupPhase.FAILED, 0, 0, "nw inv export",
                    Texts.get("cleanst_file_timeout", FILE_WAIT_TIMEOUT_MS / 60_000),
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
                clean.emptyReason.ifBlank { Texts.get("cleanst_no_plan_default") }, atMs = nowMs
            )
            clean.cursor >= clean.total -> CleanupStatus(CleanupPhase.DONE, clean.total, clean.total, "", "", atMs = nowMs)
            !clean.active -> CleanupStatus(
                CleanupPhase.FAILED, clean.cursor, clean.total, "",
                Texts.get("cleanst_stopped"), atMs = nowMs
            )
            else -> CleanupStatus(
                CleanupPhase.WAIT_NEXT, clean.cursor, clean.total, "", Texts.get("cleanst_waiting_tom"), atMs = nowMs
            )
        }
    }

    /** Vi sao tin xac nhan Dismantle khong khop (de ghi vao dong loi). count = so cay doc duoc tu tin (null = khong doc duoc). */
    fun mismatchReason(mode: String, count: Int?, expect: Int?, expectMax: Int?, expectId: String?): String = when (mode) {
        "tier" -> when {
            count == null -> Texts.get("cleanst_mm_tier_unreadable")
            expect != null -> Texts.get("cleanst_mm_tier_exact", count, expect)
            expectMax != null -> Texts.get("cleanst_mm_tier_range", count, expectMax)
            else -> Texts.get("cleanst_mm_default")
        }
        "id" -> Texts.get("cleanst_mm_id", expectId.orEmpty())
        else -> Texts.get("cleanst_mm_default")
    }
}
