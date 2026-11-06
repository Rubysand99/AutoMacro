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
    @Test fun every_command_the_bot_sends_is_ok() {
        // Khop dung cac dang owo-tracker gui (queue_automacro_command / auto_refresh).
        for (c in listOf(
            "owo hb", "owo hb 24h", "owo hb 100", "owo hb 24h abc12", "owo hb 7d XyZ9",
            "owo use 051", "owo use 051 052 053", "owo dex gowl", "owo dex big_cat2", "owo w A1B2C3",
            "owo weapon", "owo w", "owo cash", "owo inv", "owo quest", "owo ws", "owo zoo"
        )) assertTrue(c, CommandGuard.isSafeCommand(c))
    }
    @Test fun dangerous_owo_commands_blocked() {
        for (c in listOf(
            "owo give 123456789012345678 1000", "owo sell all", "owo sell gowl", "owo pay @someone 50",
            "owo sacrifice gowl", "owo trade", "owo battle", "owo hunt", "owo daily", "owo team",
            "owo cash give", "owo zoo all", "owo weapon 12AB", "owo inv sell"
        )) assertFalse(c, CommandGuard.isSafeCommand(c))
    }
    @Test fun bad_arguments_blocked() {
        for (c in listOf(
            "owo hb 24h abc def", "owo hb abc", "owo hb 99999999", "owo hb 24h ab",
            "owo use", "owo use abc", "owo use 051; owo give", "owo use " + "1 ".repeat(13).trim(),
            "owo dex", "owo dex gowl owl", "owo dex <@123>", "owo dex " + "a".repeat(33),
            "owo w AB", "owo w ABCDEFGHI", "owo w AB-12"
        )) assertFalse(c, CommandGuard.isSafeCommand(c))
    }
    @Test fun click_label_allowlist() {
        assertTrue(CommandGuard.isAllowedClickLabel("forward"))
        assertTrue(CommandGuard.isAllowedClickLabel("  Forward "))
        for (c in listOf(null, "", " ", "fast_forward", "fast forward", "Accept", "Confirm", "Delete", "Join", "forward forward"))
            assertFalse(c.toString(), CommandGuard.isAllowedClickLabel(c))
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
