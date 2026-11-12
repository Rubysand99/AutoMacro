package com.starclan.starhub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BotToolsTextTest {
    init { TestTexts.install() }   // chu doc tu strings.xml that

    @Test fun alt_id_validation_matches_the_bot() {
        assertTrue(BotToolsText.isValidAltId("1238687916176183409"))
        assertTrue(BotToolsText.isValidAltId("  123456789012345678  "))   // cat khoang trang nhu bot
        assertTrue(BotToolsText.isValidAltId("1".repeat(15)))
        assertTrue(BotToolsText.isValidAltId("1".repeat(25)))
        assertFalse(BotToolsText.isValidAltId("1".repeat(14)))
        assertFalse(BotToolsText.isValidAltId("1".repeat(26)))
        assertFalse(BotToolsText.isValidAltId("12345678901234567x"))
        assertFalse(BotToolsText.isValidAltId("12345678 9012345678"))
        assertFalse(BotToolsText.isValidAltId(""))     // o trong KHONG la ID hop le (tat acc phu co nut rieng)
        assertFalse(BotToolsText.isValidAltId("   "))
    }

    @Test fun alt_status_covers_all_states() {
        assertTrue(BotToolsText.altStatus(null).contains("/alt"))
        assertTrue(BotToolsText.altStatus(BotAlt("")).contains("TẮT"))
        assertTrue(BotToolsText.altStatus(BotAlt("   ")).contains("TẮT"))
        assertTrue(BotToolsText.altStatus(BotAlt("123456789012345678")).contains("123456789012345678"))
    }

    @Test fun alt_test_message_covers_all_outcomes() {
        assertTrue(BotToolsText.altTestMessage(null).contains("bot"))
        assertTrue(BotToolsText.altTestMessage(BotAltTest(true, "123456789012345678", "")).contains("123456789012345678"))
        assertTrue(BotToolsText.altTestMessage(BotAltTest(false, "", "no_alt")).contains("Chưa đặt"))
        val failed = BotToolsText.altTestMessage(BotAltTest(false, "123456789012345678", "dm_failed"))
        assertTrue(failed.contains("123456789012345678")); assertTrue(failed.contains("chung server"))
        // ly do la / rong cung roi vao nhanh "khong DM duoc" (khong bao thanh cong nham)
        assertFalse(BotToolsText.altTestMessage(BotAltTest(false, "1", "")).contains("Đã gửi"))
    }

    @Test fun unlocked_text_lists_tiers_with_counts() {
        val u = BotUnlocked(3, listOf(BotUnlockedTier("common", 2, listOf("bee", "cat")), BotUnlockedTier("rare", 1, listOf("wolf"))))
        assertEquals("Đã mở khóa 3 con\n\ncommon (2)\nbee, cat\n\nrare (1)\nwolf", BotToolsText.unlockedText(u))
    }

    @Test fun unlocked_text_when_empty_tells_what_to_do() {
        assertTrue(BotToolsText.unlockedText(BotUnlocked(0, emptyList())).contains("owo zoo"))
        assertTrue(BotToolsText.unlockedText(BotUnlocked(5, emptyList())).contains("owo zoo"))   // total > 0 nhung khong co bac = du lieu hong
    }
}
