package com.starclan.starhub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BotToolsJsonTest {
    init { TestTexts.install() }   // chu doc tu strings.xml that

    @Test fun settings_parse_all_fields() {
        val s = BotToolsJson.parseSettings(
            """{"auto_check":false,"hb_auto":false,"hb_alert":true,"event_mode":true,"app_on":false,"captcha_active":true}""")!!
        assertFalse(s.autoCheck); assertFalse(s.hbAuto); assertTrue(s.hbAlert)
        assertTrue(s.eventMode); assertFalse(s.appOn); assertTrue(s.captchaActive)
    }

    @Test fun settings_missing_or_wrong_type_use_bot_defaults() {
        val s = BotToolsJson.parseSettings("""{"hb_alert":"yes"}""")!!
        assertEquals(BotSettings(), s)
        assertTrue(s.autoCheck); assertTrue(s.hbAuto); assertFalse(s.hbAlert); assertTrue(s.appOn)
    }

    @Test fun settings_garbage_gives_null() {
        assertNull(BotToolsJson.parseSettings("khong phai json"))
        assertNull(BotToolsJson.parseSettings("[1,2]"))
        assertNull(BotToolsJson.parseSettings(""))
    }

    @Test fun notes_parse_and_skip_broken_rows() {
        val n = BotToolsJson.parseNotes(
            """{"notes":[{"id":2,"text":"mua gem","done":false},{"text":"thieu id"},{"id":5,"text":"xong roi","done":true}]}""")!!
        assertEquals(listOf(BotNote(2, "mua gem", false), BotNote(5, "xong roi", true)), n)
        assertEquals(emptyList<BotNote>(), BotToolsJson.parseNotes("""{"notes":[]}"""))
        assertNull(BotToolsJson.parseNotes("""{"x":1}"""))
        assertNull(BotToolsJson.parseNotes("loi"))
    }

    @Test fun request_bodies_match_what_the_bot_expects() {
        assertEquals("""{"key":"auto_check","enabled":true}""", BotToolsJson.toggleBody(BotToggle.AUTO_CHECK, true))
        assertEquals("""{"key":"hb_alert","enabled":false}""", BotToolsJson.toggleBody(BotToggle.HB_ALERT, false))
        assertEquals("""{"action":"done","id":7}""", BotToolsJson.doneNoteBody(7))
        // noi dung co dau nhay / xuong dong van phai la JSON hop le
        val body = BotToolsJson.addNoteBody("mua \"gem\"\nngay mai")
        val o = com.google.gson.JsonParser.parseString(body).asJsonObject
        assertEquals("add", o.get("action").asString)
        assertEquals("mua \"gem\"\nngay mai", o.get("text").asString)
        assertNotNull(o)
    }

    @Test fun toggle_keys_are_unique() {
        assertEquals(BotToggle.values().size, BotToggle.values().map { it.key }.toSet().size)
    }

    @Test fun teamcheck_parse() {
        val t = BotToolsJson.parseTeamCheck("""{"text":"Chấm đội hình — passive hợp: 3/3","members":3}""")!!
        assertEquals(BotTeamCheck("Chấm đội hình — passive hợp: 3/3", 3), t)
        // thieu members -> 0; thieu text / sai dang -> null
        assertEquals(0, BotToolsJson.parseTeamCheck("""{"text":"Chưa có đội hình"}""")!!.members)
        assertNull(BotToolsJson.parseTeamCheck("""{"members":3}"""))
        assertNull(BotToolsJson.parseTeamCheck("""{"text":5}"""))
        assertNull(BotToolsJson.parseTeamCheck("[1]"))
        assertNull(BotToolsJson.parseTeamCheck("loi"))
        assertNull(BotToolsJson.parseTeamCheck(""))
    }

    @Test fun zoodiff_parse() {
        val d = BotToolsJson.parseZooDiff("""{"ok":true,"kind":"current","text":"Zoo Points: +500"}""")!!
        assertEquals(BotZooDiff(true, "current", "Zoo Points: +500"), d)
        // chua co lich su: kind = null (JSON null) van doc duoc, ok = false
        val none = BotToolsJson.parseZooDiff("""{"ok":false,"kind":null,"text":"Chưa có lịch sử zoo."}""")!!
        assertFalse(none.ok); assertNull(none.kind)
        // thieu ok -> false (than trong), thieu text -> null
        assertFalse(BotToolsJson.parseZooDiff("""{"text":"x"}""")!!.ok)
        assertNull(BotToolsJson.parseZooDiff("""{"ok":true}"""))
        assertNull(BotToolsJson.parseZooDiff("loi"))
    }

    @Test fun unlocked_parse() {
        val u = BotToolsJson.parseUnlocked(
            """{"total":3,"tiers":[{"tier":"common","count":2,"pets":["bee","cat"]},{"tier":"rare","count":1,"pets":["wolf"]}]}""")!!
        assertEquals(3, u.total)
        assertEquals(listOf(BotUnlockedTier("common", 2, listOf("bee", "cat")), BotUnlockedTier("rare", 1, listOf("wolf"))), u.tiers)
        assertEquals(BotUnlocked(0, emptyList()), BotToolsJson.parseUnlocked("""{"total":0,"tiers":[]}"""))
    }

    @Test fun unlocked_parse_is_tolerant_of_missing_fields() {
        // bac thieu ten bi bo; count thieu -> so ten pet; pet khong phai chuoi bi bo; total thieu -> cong cac bac
        val u = BotToolsJson.parseUnlocked(
            """{"tiers":[{"pets":["x"]},{"tier":"epic","pets":["dragon",7,null]},{"tier":"fabled"}]}""")!!
        assertEquals(listOf(BotUnlockedTier("epic", 1, listOf("dragon")), BotUnlockedTier("fabled", 0, emptyList())), u.tiers)
        assertEquals(1, u.total)
        assertNull(BotToolsJson.parseUnlocked("""{"total":1}"""))
        assertNull(BotToolsJson.parseUnlocked("""{"tiers":"x"}"""))
        assertNull(BotToolsJson.parseUnlocked("loi"))
    }

    @Test fun alt_parse_and_body() {
        assertEquals(BotAlt("123456789012345678"), BotToolsJson.parseAlt("""{"alt_user_id":"123456789012345678"}"""))
        assertEquals(BotAlt(""), BotToolsJson.parseAlt("""{"alt_user_id":""}"""))   // rong = da tat, van hop le
        assertNull(BotToolsJson.parseAlt("""{}"""))
        assertNull(BotToolsJson.parseAlt("""{"alt_user_id":123}"""))
        assertNull(BotToolsJson.parseAlt("loi"))
        assertEquals("""{"user_id":"123456789012345678"}""", BotToolsJson.altBody("123456789012345678"))
        assertEquals("""{"user_id":""}""", BotToolsJson.altBody(""))
    }

    @Test fun clean_parse() {
        // at_epoch tinh bang GIAY -> atMs = ms
        assertEquals(BotClean(true, 2, 5, 1794204000_000L, ""),
            BotToolsJson.parseClean("""{"active":true,"cursor":2,"total":5,"at_epoch":1794204000,"empty_reason":"","pending":1}"""))
        assertEquals(BotClean(false, 0, 0, 0L, "5 cây chưa rõ bậc"),
            BotToolsJson.parseClean("""{"active":false,"cursor":0,"total":0,"at_epoch":0,"empty_reason":"5 cây chưa rõ bậc"}"""))
        // thieu khoa -> mac dinh an toan; sai dang / sai kieu -> null hoac mac dinh, khong nem loi
        assertEquals(BotClean(false, 0, 0, 0L, ""), BotToolsJson.parseClean("{}"))
        assertEquals(0L, BotToolsJson.parseClean("""{"at_epoch":"abc"}""")!!.atMs)
        assertNull(BotToolsJson.parseClean("loi"))
        assertNull(BotToolsJson.parseClean("[1]"))
    }

    @Test fun alt_test_parse() {
        assertEquals(BotAltTest(true, "123456789012345678", ""),
            BotToolsJson.parseAltTest("""{"ok":true,"alt_user_id":"123456789012345678","reason":""}"""))
        assertEquals(BotAltTest(false, "", "no_alt"), BotToolsJson.parseAltTest("""{"ok":false,"alt_user_id":"","reason":"no_alt"}"""))
        assertEquals(BotAltTest(false, "", ""), BotToolsJson.parseAltTest("""{}"""))   // thieu ok -> false
        assertNull(BotToolsJson.parseAltTest("loi"))
    }

    @Test fun app_config_body_lists_disabled_ids() {
        assertEquals("""{"disabled":["inv","zoo"]}""", BotToolsJson.appConfigBody(listOf("inv", "zoo")))
        assertEquals("""{"disabled":[]}""", BotToolsJson.appConfigBody(emptyList()))
    }
}
