package com.starclan.starhub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BotToolsJsonTest {

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
}
