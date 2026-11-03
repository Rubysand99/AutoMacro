package com.starclan.starhub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FarmStatsTest {
    private val zone = ZoneId.of("UTC")
    private fun book() = StatsBook(zone)
    private fun ms(date: String, h: Int = 12, m: Int = 0, s: Int = 0): Long =
        LocalDate.parse(date).atTime(h, m, s).atZone(zone).toInstant().toEpochMilli()

    @Test fun counts_events_per_day() {
        val b = book()
        b.addHunt(ms("2026-11-03", 10)); b.addHunt(ms("2026-11-03", 11)); b.addBattle(ms("2026-11-03", 11))
        b.addCaptcha(ms("2026-11-03", 12)); b.addNewPets(ms("2026-11-03", 12), 2); b.addNewPets(ms("2026-11-03", 13), 0)
        b.addHunt(ms("2026-11-04", 1))
        val d3 = b.today(ms("2026-11-03", 20))
        assertEquals(2, d3.hunts); assertEquals(1, d3.battles); assertEquals(1, d3.captchas); assertEquals(2, d3.newPets)
        assertEquals(1, b.today(ms("2026-11-04", 2)).hunts)
        assertEquals(0, b.today(ms("2026-11-05", 2)).hunts)
    }

    @Test fun session_seconds_accumulate_and_stop() {
        val b = book()
        b.startSession(ms("2026-11-03", 10, 0))
        b.addHunt(ms("2026-11-03", 10, 30))
        assertEquals(1800L, b.today(ms("2026-11-03", 10, 30)).seconds)
        b.endSession(ms("2026-11-03", 11, 0))
        assertEquals(3600L, b.today(ms("2026-11-03", 15, 0)).seconds)   // het phien thi khong tinh them
        assertEquals(1, b.today(ms("2026-11-03", 15, 0)).sessions)
        b.endSession(ms("2026-11-03", 16, 0))                           // goi 2 lan khong sao
        assertEquals(3600L, b.today(ms("2026-11-03", 17, 0)).seconds)
    }

    @Test fun session_across_midnight_splits_between_days() {
        val b = book()
        b.startSession(ms("2026-11-03", 23, 0))
        b.addHunt(ms("2026-11-03", 23, 59))
        b.addHunt(ms("2026-11-04", 0, 1))
        assertEquals(3540L, b.today(ms("2026-11-03", 23, 59)).seconds)
        assertEquals(120L, b.today(ms("2026-11-04", 0, 1)).seconds)
    }

    @Test fun slowdown_keeps_max_only() {
        val b = book()
        val t = ms("2026-11-03")
        b.observeSlowdown(t, 0); b.observeSlowdown(t, 3); b.observeSlowdown(t, 2)
        assertEquals(3, b.today(t).maxSlowdown30m)
    }

    @Test fun cash_first_and_last_of_day() {
        val b = book()
        b.observeCash(ms("2026-11-03", 9), 1000L, 0L)
        b.observeCash(ms("2026-11-03", 12), 1800L, 0L)
        val d = b.today(ms("2026-11-03", 13))
        assertEquals(1000L, d.cashFirst); assertEquals(1800L, d.cashLast)
    }

    @Test fun stale_cash_reading_counts_for_the_day_it_was_read() {
        val b = book()
        // 8h sang 04/11 bot van cho so lieu doc luc 22h toi 03/11 (cu 10 tieng)
        b.observeCash(ms("2026-11-04", 8), 1000L, 10 * 3600L)
        assertEquals(1000L, b.today(ms("2026-11-03", 23)).cashFirst)
        assertNull(b.today(ms("2026-11-04", 8)).cashFirst)
        b.observeCash(ms("2026-11-04", 8, 5), 1500L, 60L)
        assertEquals(1500L, b.today(ms("2026-11-04", 9)).cashFirst)
    }

    @Test fun cash_same_reading_is_not_dirty_and_older_reading_ignored() {
        val b = book()
        b.observeCash(ms("2026-11-03", 12), 1000L, 0L)
        b.markSaved()
        b.observeCash(ms("2026-11-03", 12, 0, 15), 1000L, 0L)        // cung so, doc lai
        assertFalse(b.dirty)
        b.observeCash(ms("2026-11-03", 12, 5), 900L, 3600L)          // so lieu cu hon lan da ghi
        assertEquals(1000L, b.today(ms("2026-11-03", 13)).cashLast)
    }

    @Test fun keeps_only_max_days() {
        val b = book()
        for (i in 1..20) b.addHunt(ms("2026-10-%02d".format(i)))
        val all = b.recent(ms("2026-10-20"), 100)
        assertEquals(StatsBook.MAX_DAYS, all.size)
        assertEquals("2026-10-20", all.first().date)
        assertEquals("2026-10-07", all.last().date)
    }

    @Test fun recent_is_newest_first_and_limited() {
        val b = book()
        for (d in listOf("2026-11-01", "2026-11-03", "2026-11-02")) b.addHunt(ms(d))
        assertEquals(listOf("2026-11-03", "2026-11-02"), b.recent(ms("2026-11-03", 20), 2).map { it.date })
    }

    @Test fun json_roundtrip_and_bad_json() {
        val b = book()
        b.addHunt(ms("2026-11-03")); b.observeCash(ms("2026-11-03"), 500L, 0L)
        val back = StatsBook.fromJson(b.toJson(), zone)
        val d = back.today(ms("2026-11-03", 13))
        assertEquals(1, d.hunts); assertEquals(500L, d.cashFirst)
        for (bad in listOf(null, "", "   ", "not json", "{\"a\":1}", "[1,2,3]"))
            assertTrue(bad.toString(), StatsBook.fromJson(bad, zone).recent(ms("2026-11-03")).isEmpty())
    }

    @Test fun clear_removes_everything() {
        val b = book()
        b.addHunt(ms("2026-11-03")); b.clear()
        assertTrue(b.recent(ms("2026-11-03")).isEmpty())
    }

    @Test fun format_helpers() {
        assertEquals("0 phút", StatsFormat.duration(0)); assertEquals("52 phút", StatsFormat.duration(3125))
        assertEquals("1h00", StatsFormat.duration(3600)); assertEquals("5h12", StatsFormat.duration(18720))
        assertEquals("0 phút", StatsFormat.duration(-5))
        assertEquals("03/11", StatsFormat.shortDate("2026-11-03")); assertEquals("abc", StatsFormat.shortDate("abc"))
        assertEquals("+1,250", StatsFormat.signed(1250)); assertEquals("-300", StatsFormat.signed(-300)); assertEquals("0", StatsFormat.signed(0))
    }
}
