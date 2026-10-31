package com.starclan.starhub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandGuardTest {
    @Test fun owo_commands_ok() {
        for (c in listOf("owo hb 100", "owo use 051 052", "owo dex gowl", "owo w 12AB", "owo weapon", "owo zoo", "OWO cash", "  owo inv  "))
            assertTrue(c, CommandGuard.isSafeCommand(c))
    }
    @Test fun other_text_blocked() {
        for (c in listOf("", "   ", "hello", "owo", "owohb", "@everyone owo hb", "rm -rf", "https://evil.example/x", "?tag orb"))
            assertFalse(c, CommandGuard.isSafeCommand(c))
    }
    @Test fun multiline_and_control_blocked() {
        assertFalse(CommandGuard.isSafeCommand("owo hb 1\nsend money"))
        assertFalse(CommandGuard.isSafeCommand("owo hb 1\u0000"))
    }
    @Test fun too_long_blocked() = assertFalse(CommandGuard.isSafeCommand("owo " + "a".repeat(250)))
    @Test fun null_blocked() = assertFalse(CommandGuard.isSafeCommand(null))

    @Test fun click_times_clamped() {
        assertEquals(50, CommandGuard.clampClickTimes(null))
        assertEquals(1, CommandGuard.clampClickTimes(-5))
        assertEquals(CommandGuard.MAX_CLICK_TIMES, CommandGuard.clampClickTimes(999_999))
    }
    @Test fun click_gap_clamped() {
        assertEquals(1_800L to 2_800L, CommandGuard.clampClickGap(null, null))
        assertEquals(CommandGuard.MIN_CLICK_GAP_MS to CommandGuard.MIN_CLICK_GAP_MS, CommandGuard.clampClickGap(1L, 1L))
        val (lo, hi) = CommandGuard.clampClickGap(9_000L, 100L)
        assertTrue(lo <= hi)
    }
}
