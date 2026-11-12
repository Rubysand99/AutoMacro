package com.starclan.starhub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanupStatusTextTest {
    init { TestTexts.install() }   // chu doc tu strings.xml that, nen van so khop dung chu nguoi dung thay

    private val T = 1_000_000_000L   // moc thoi gian tuy y (ms)

    // ---------- line(): moi giai doan deu co chu de Ruby biet app dang cho gi / lam gi ----------

    @Test fun off_shows_nothing_unless_there_is_a_note() {
        assertEquals("", CleanupStatusText.line(CleanupStatus(), T))
        assertEquals("Đã tắt", CleanupStatusText.line(CleanupStatus(CleanupPhase.OFF, detail = "Đã tắt"), T))
    }

    @Test fun exporting_has_a_default_and_a_custom_message() {
        assertTrue(CleanupStatusText.line(CleanupStatus(CleanupPhase.EXPORTING), T).contains("nw inv export"))
        assertTrue(CleanupStatusText.line(CleanupStatus(CleanupPhase.EXPORTING, detail = "Farm đang chạy"), T).contains("Farm đang chạy"))
    }

    @Test fun wait_file_counts_down_then_counts_up() {
        val s = CleanupStatus(CleanupPhase.WAIT_FILE, untilMs = T + 45_000, sinceMs = T)
        assertTrue(CleanupStatusText.line(s, T).contains("còn 45s"))
        assertTrue(CleanupStatusText.line(s, T + 44_001).contains("còn 1s"))   // lam tron len, khong hien "con 0s"
        val after = CleanupStatusText.line(s, T + 60_000)
        assertTrue(after.contains("đã chờ 60s")); assertFalse(after.contains("còn"))
        assertTrue(CleanupStatusText.line(s.copy(detail = "Không hỏi được bot"), T).contains("Không hỏi được bot"))
    }

    @Test fun running_shows_step_command_and_detail() {
        val s = CleanupStatus(CleanupPhase.RUNNING, 3, 12, "owo dismantle rare", "Chờ tin xác nhận của OwO…")
        val t = CleanupStatusText.line(s, T)
        assertTrue(t.contains("Bước 3/12")); assertTrue(t.contains("owo dismantle rare")); assertTrue(t.contains("Chờ tin xác nhận"))
        // chua biet tong -> khong hien "0/0"
        assertEquals("🧹 Đang làm: owo w ABC", CleanupStatusText.line(CleanupStatus(CleanupPhase.RUNNING, 0, 0, "owo w ABC"), T))
    }

    @Test fun waiting_states_say_what_they_wait_for() {
        assertTrue(CleanupStatusText.line(CleanupStatus(CleanupPhase.WAIT_FARM, 2, 9), T).contains("farm đang chạy"))
        assertTrue(CleanupStatusText.line(CleanupStatus(CleanupPhase.WAIT_FARM, 2, 9), T).contains("2/9"))
        assertEquals("⏳ Đã xong 4/9 bước. Chờ bước kế…", CleanupStatusText.line(CleanupStatus(CleanupPhase.WAIT_NEXT, 4, 9), T))
        assertTrue(CleanupStatusText.line(CleanupStatus(CleanupPhase.WAIT_NEXT, 4, 9, detail = "Nghỉ vài giây"), T).endsWith("Nghỉ vài giây"))
    }

    @Test fun finished_states() {
        val done = CleanupStatusText.line(CleanupStatus(CleanupPhase.DONE, 9, 9), T)
        assertTrue(done.contains("9/9")); assertTrue(done.contains("nw inv export"))
        val none = CleanupStatusText.line(CleanupStatus(CleanupPhase.NO_PLAN, detail = "5 cây ở vùng chưa rõ bậc"), T)
        assertTrue(none.contains("không giao bước dọn nào")); assertTrue(none.contains("5 cây"))
        assertTrue(CleanupStatusText.line(CleanupStatus(CleanupPhase.NO_PLAN), T).contains("xem tin của Tom"))
        val failed = CleanupStatusText.line(CleanupStatus(CleanupPhase.FAILED, 3, 12, "owo dismantle rare", "OwO báo 25 cây"), T)
        assertTrue(failed.contains("bước 3/12")); assertTrue(failed.contains("owo dismantle rare")); assertTrue(failed.contains("OwO báo 25 cây"))
        assertTrue(CleanupStatusText.line(CleanupStatus(CleanupPhase.FAILED, 0, 0, "nw inv export", "x"), T).contains("Dừng ở nw inv export"))
        assertTrue(CleanupStatusText.line(CleanupStatus(CleanupPhase.FAILED, detail = "x"), T).contains("lúc bắt đầu"))
    }

    // ---------- busy / finished / headerLine ----------

    @Test fun busy_and_finished_flags() {
        for (p in listOf(CleanupPhase.EXPORTING, CleanupPhase.WAIT_FILE, CleanupPhase.RUNNING, CleanupPhase.WAIT_FARM, CleanupPhase.WAIT_NEXT)) {
            assertTrue("$p busy", CleanupStatus(p).busy); assertFalse("$p finished", CleanupStatus(p).finished)
        }
        for (p in listOf(CleanupPhase.DONE, CleanupPhase.NO_PLAN, CleanupPhase.FAILED)) {
            assertTrue("$p finished", CleanupStatus(p).finished); assertFalse("$p busy", CleanupStatus(p).busy)
        }
        assertFalse(CleanupStatus(CleanupPhase.OFF).busy); assertFalse(CleanupStatus(CleanupPhase.OFF).finished)
    }

    @Test fun header_hides_old_results_but_always_shows_progress() {
        assertTrue(CleanupStatusText.headerLine(CleanupStatus(CleanupPhase.RUNNING, 1, 2, "x", atMs = 0L), T).isNotEmpty())   // dang chay: luon hien
        val done = CleanupStatus(CleanupPhase.DONE, 2, 2, atMs = T)
        assertTrue(CleanupStatusText.headerLine(done, T + 9 * 60_000).isNotEmpty())
        assertEquals("", CleanupStatusText.headerLine(done, T + 10 * 60_000))      // qua 10 phut -> an
        assertEquals("", CleanupStatusText.headerLine(CleanupStatus(), T))
    }

    // ---------- idleStatus(): sau 45 giay cho, bot dang o dau? ----------

    private val sent = T
    private val afterHold = T + 60_000
    private val prevWait = CleanupStatus(CleanupPhase.WAIT_FILE, untilMs = T + 45_000, sinceMs = T)
    private fun clean(active: Boolean = true, cursor: Int = 0, total: Int = 5, atMs: Long = T + 20_000, reason: String = "") =
        BotClean(active, cursor, total, atMs, reason)

    @Test fun idle_pending_steps_run_unless_farming() {
        assertNull(CleanupStatusText.idleStatus(null, sent, afterHold, false, 3, prevWait))   // se tu dat trang thai khi chay buoc
        val w = CleanupStatusText.idleStatus(null, sent, afterHold, true, 3, CleanupStatus(CleanupPhase.WAIT_NEXT, 2, 9))!!
        assertEquals(CleanupPhase.WAIT_FARM, w.phase); assertEquals(2, w.step); assertEquals(9, w.total)
    }

    @Test fun idle_cannot_reach_bot_keeps_waiting() {
        val a = CleanupStatusText.idleStatus(null, sent, afterHold, false, 0, prevWait)!!
        assertEquals(CleanupPhase.WAIT_FILE, a.phase); assertTrue(a.detail.contains("Không hỏi được bot"))
        val b = CleanupStatusText.idleStatus(null, sent, afterHold, false, 0, CleanupStatus(CleanupPhase.RUNNING, 2, 9))!!
        assertEquals(CleanupPhase.WAIT_NEXT, b.phase); assertEquals(2, b.step)
    }

    @Test fun idle_old_plan_means_tom_has_not_received_the_file_yet() {
        val old = clean(atMs = T - 3_600_000)   // ke hoach cua 1 tieng truoc
        val a = CleanupStatusText.idleStatus(old, sent, afterHold, false, 0, prevWait)!!
        assertEquals(CleanupPhase.WAIT_FILE, a.phase); assertEquals(T + 45_000, a.untilMs); assertEquals(T, a.sinceMs)
        // qua 3 phut van khong co -> that bai, noi ro phai xem tin cua Tom
        val f = CleanupStatusText.idleStatus(old, sent, T + 181_000, false, 0, prevWait)!!
        assertEquals(CleanupPhase.FAILED, f.phase); assertTrue(f.detail.contains("Tom không nhận được file"))
        // dung 3 phut chua that bai
        assertEquals(CleanupPhase.WAIT_FILE, CleanupStatusText.idleStatus(old, sent, T + 180_000, false, 0, prevWait)!!.phase)
    }

    @Test fun idle_clock_skew_tolerance() {
        // ke hoach moi hon luc go export 10 giay nhung dong ho may chu lech: van la ke hoach MOI
        assertEquals(CleanupPhase.WAIT_NEXT, CleanupStatusText.idleStatus(clean(atMs = T - 10_000), sent, afterHold, false, 0, prevWait)!!.phase)
        // cu hon 30 giay thi la ke hoach CU
        assertEquals(CleanupPhase.WAIT_FILE, CleanupStatusText.idleStatus(clean(atMs = T - 31_000), sent, afterHold, false, 0, prevWait)!!.phase)
    }

    @Test fun idle_new_plan_outcomes() {
        // khong co buoc: neu ly do (nhu con cay chua ro bac)
        val none = CleanupStatusText.idleStatus(clean(active = false, total = 0, reason = "5 cây chưa rõ bậc (C06P72)"), sent, afterHold, false, 0, prevWait)!!
        assertEquals(CleanupPhase.NO_PLAN, none.phase); assertTrue(none.detail.contains("C06P72")); assertTrue(none.finished)
        assertTrue(CleanupStatusText.idleStatus(clean(active = false, total = 0), sent, afterHold, false, 0, prevWait)!!.detail.contains("xem tin của Tom"))
        // xong het
        val done = CleanupStatusText.idleStatus(clean(active = false, cursor = 5, total = 5), sent, afterHold, false, 0, prevWait)!!
        assertEquals(CleanupPhase.DONE, done.phase); assertEquals(5, done.step); assertEquals(5, done.total)
        // phien bi dung giua chung (,cleanstop / buoc loi)
        val stopped = CleanupStatusText.idleStatus(clean(active = false, cursor = 2, total = 5), sent, afterHold, false, 0, prevWait)!!
        assertEquals(CleanupPhase.FAILED, stopped.phase); assertEquals(2, stopped.step); assertTrue(stopped.detail.contains("cleanstop"))
        // dang chay, chua co buoc nao cho -> doi Tom giao buoc ke
        val next = CleanupStatusText.idleStatus(clean(cursor = 3, total = 5), sent, afterHold, false, 0, prevWait)!!
        assertEquals(CleanupPhase.WAIT_NEXT, next.phase); assertEquals(3, next.step); assertEquals(5, next.total)
    }

    // ---------- mismatchReason ----------

    @Test fun mismatch_reasons_are_specific() {
        assertTrue(CleanupStatusText.mismatchReason("tier", null, 54, null, null).contains("không đọc được số cây"))
        val exact = CleanupStatusText.mismatchReason("tier", 25, 24, null, null)
        assertTrue(exact.contains("25 cây")); assertTrue(exact.contains("đúng 24")); assertTrue(exact.contains("Cancel"))
        assertTrue(CleanupStatusText.mismatchReason("tier", 99, null, 24, null).contains("1–24"))
        assertTrue(CleanupStatusText.mismatchReason("id", 1, null, null, "C06P72").contains("C06P72"))
        assertNotNull(CleanupStatusText.mismatchReason("lạ", null, null, null, null))
    }
}
