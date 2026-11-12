package com.starclan.starhub.data

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Nhap tay ,petset ,weapontypeset ,passiveset: luat kiem tra phai KHOP voi tools_api.py cua bot (owo-tracker). */
class ManualInputTest {

    private val goodStats = listOf("10", "1", "3", "1", "2", "3")

    private fun bad(r: ManualCheck<*>) = (r as ManualCheck.Bad).field

    // ---------------- pet ----------------

    @Test fun pet_valid_trims_and_reads_stats_in_order() {
        val r = ManualInput.pet(" gowl ", " legendary ", listOf("10", " 1 ", "3", "1", "2", "3"))
        assertEquals(ManualCheck.Ok(ManualPet("gowl", "legendary", 10, 1, 3, 1, 2, 3)), r)
    }

    @Test fun pet_stat_bounds() {
        assertTrue(ManualInput.pet("a", "rare", listOf("0", "0", "0", "0", "0", "100000")) is ManualCheck.Ok)
        assertEquals("pr", bad(ManualInput.pet("a", "rare", listOf("1", "1", "100001", "1", "1", "1"))))
    }

    @Test fun pet_stat_must_be_plain_digits_and_names_the_wrong_field() {
        assertEquals("hp", bad(ManualInput.pet("a", "rare", listOf("", "1", "1", "1", "1", "1"))))
        assertEquals("att", bad(ManualInput.pet("a", "rare", listOf("1", "-1", "1", "1", "1", "1"))))
        assertEquals("wp", bad(ManualInput.pet("a", "rare", listOf("1", "1", "1", "1.5", "1", "1"))))
        assertEquals("mag", bad(ManualInput.pet("a", "rare", listOf("1", "1", "1", "1", "+5", "1"))))
        assertEquals("mr", bad(ManualInput.pet("a", "rare", listOf("1", "1", "1", "1", "1", "abc"))))
        assertEquals("hp", bad(ManualInput.pet("a", "rare", listOf("1234567", "1", "1", "1", "1", "1"))))   // qua 6 chu so
    }

    @Test fun pet_wrong_number_of_stats_is_rejected() {
        assertEquals("body", bad(ManualInput.pet("a", "rare", listOf("1", "2", "3"))))
    }

    @Test fun pet_name_rules() {
        assertEquals("name", bad(ManualInput.pet("   ", "rare", goodStats)))
        assertEquals("name", bad(ManualInput.pet("a\nb", "rare", goodStats)))
        assertEquals("name", bad(ManualInput.pet("x".repeat(61), "rare", goodStats)))
        assertTrue(ManualInput.pet("x".repeat(60), "rare", goodStats) is ManualCheck.Ok)
    }

    @Test fun pet_rank_rules() {
        assertEquals("rank", bad(ManualInput.pet("a", "", goodStats)))
        assertEquals("rank", bad(ManualInput.pet("a", "rare2", goodStats)))
        assertEquals("rank", bad(ManualInput.pet("a", "very rare", goodStats)))
        assertEquals("rank", bad(ManualInput.pet("a", "x".repeat(21), goodStats)))
        assertTrue(ManualInput.pet("a", "x".repeat(20), goodStats) is ManualCheck.Ok)
    }

    // ---------------- desc (loai vu khi / passive) ----------------

    @Test fun desc_valid_keeps_inner_newline_and_trims_edges() {
        val r = ManualInput.desc(" poison dagger ", " Gây Poison \n xuyên giáp ")
        assertEquals(ManualCheck.Ok(ManualDesc("poison dagger", "Gây Poison \n xuyên giáp")), r)
    }

    @Test fun desc_rules() {
        assertEquals("name", bad(ManualInput.desc("", "x")))
        assertEquals("desc", bad(ManualInput.desc("a", "   ")))
        assertEquals("desc", bad(ManualInput.desc("a", "x".repeat(1001))))
        assertTrue(ManualInput.desc("a", "x".repeat(1000)) is ManualCheck.Ok)
        assertEquals("desc", bad(ManualInput.desc("a", "x\u0000y")))
        assertEquals("desc", bad(ManualInput.desc("a", "x\ty")))   // tab la ky tu dieu khien, bot cung tu choi
    }

    @Test fun desc_windows_newline_is_normalised_not_rejected() {
        assertEquals(ManualCheck.Ok(ManualDesc("a", "dòng 1\ndòng 2")), ManualInput.desc("a", "dòng 1\r\ndòng 2"))
    }

    @Test fun length_counts_real_characters_like_the_bot() {
        // 60 emoji = 60 ky tu that (120 don vi UTF-16): phai duoc nhan nhu bot (len() cua Python dem ky tu)
        assertTrue(ManualInput.desc("😀".repeat(60), "x") is ManualCheck.Ok)
        assertEquals("name", bad(ManualInput.desc("😀".repeat(61), "x")))
    }

    // ---------------- body gui len bot ----------------

    @Test fun pet_body_has_integer_stats() {
        val o = JsonParser.parseString(BotToolsJson.petBody(ManualPet("gowl", "legendary", 10, 1, 3, 1, 2, 3))).asJsonObject
        assertEquals("gowl", o.get("name").asString)
        assertEquals("legendary", o.get("rank").asString)
        for ((k, v) in mapOf("hp" to 10, "att" to 1, "pr" to 3, "wp" to 1, "mag" to 2, "mr" to 3)) {
            assertTrue("$k phai la so", o.get(k).asJsonPrimitive.isNumber)
            assertEquals(v, o.get(k).asInt)
        }
        assertEquals(8, o.size())
    }

    @Test fun desc_body_escapes_quotes_and_newlines() {
        val body = BotToolsJson.descBody(ManualDesc("a\"b", "dòng 1\ndòng \"2\""))
        val o = JsonParser.parseString(body).asJsonObject   // phai la JSON hop le du co dau nhay / xuong dong
        assertEquals("a\"b", o.get("name").asString)
        assertEquals("dòng 1\ndòng \"2\"", o.get("desc").asString)
    }

    @Test fun manual_paths_match_the_bot_routes() {
        assertEquals("/manual/pet", ManualKind.PET.path)
        assertEquals("/manual/weapontype", ManualKind.WEAPON_TYPE.path)
        assertEquals("/manual/passive", ManualKind.PASSIVE.path)
    }

    // ---------------- doc tra loi cua bot ----------------

    @Test fun parse_manual_result() {
        assertEquals(BotManualResult(true, "Gowl"), BotToolsJson.parseManual("""{"ok":true,"name":"Gowl"}"""))
        assertEquals(BotManualResult(false, ""), BotToolsJson.parseManual("{}"))              // than trong: khong nhan la da luu
        assertEquals(BotManualResult(false, "x"), BotToolsJson.parseManual("""{"ok":"yes","name":"x"}"""))
        assertNull(BotToolsJson.parseManual("khong phai json"))
        assertNull(BotToolsJson.parseManual("[1]"))
    }

    // ---------------- chu hien thi ----------------

    @Test fun field_errors_name_the_problem() {
        assertTrue(BotToolsText.manualFieldError("name").contains("60"))
        assertTrue(BotToolsText.manualFieldError("rank").contains("chữ cái"))
        assertTrue(BotToolsText.manualFieldError("desc").contains("1000"))
        for (k in ManualInput.STAT_KEYS) {
            val m = BotToolsText.manualFieldError(k)
            assertTrue(m, m.startsWith(k.uppercase())); assertTrue(m, m.contains("100000"))
        }
        assertEquals("Dữ liệu chưa hợp lệ.", BotToolsText.manualFieldError("body"))
    }

    @Test fun saved_message_covers_all_outcomes() {
        assertTrue(BotToolsText.manualSavedMessage(null).contains("/manual"))
        assertEquals("Đã lưu Gowl.", BotToolsText.manualSavedMessage(BotManualResult(true, "Gowl")))
        assertFalse(BotToolsText.manualSavedMessage(BotManualResult(false, "Gowl")).contains("Đã lưu"))
    }
}
