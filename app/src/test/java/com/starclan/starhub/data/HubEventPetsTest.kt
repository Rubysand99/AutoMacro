package com.starclan.starhub.data

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HubEventPetsTest {
    init { TestTexts.install() }

    private val json = """
        {"event_pets":{"title":"November","ends_epoch":1795939140,"active":true,"found_count":1,"total":2,"pets":[
          {"key":"2026november_pumpkinpie","label":"Pumpkin Pie","found":true,"count":2,"times_found":3,"age_seconds":300,"emoji_id":"55","emoji_animated":true},
          {"key":"2026november_autumnHsin","label":"Autumn Hsin","found":false,"times_found":0}
        ]}}
    """.trimIndent()

    @Test fun parses_event_pets_section() {
        val e = HubParser.parse(json).eventPets!!
        assertEquals("November", e.title)
        assertEquals(1, e.foundCount)
        assertEquals(2, e.pets!!.size)
        assertEquals(true, e.pets!![0].found)
        assertEquals(2L, e.pets!![0].count)
        assertEquals("55", e.pets!![0].emojiId)
        assertEquals(false, e.pets!![1].found)
    }

    @Test fun old_bot_without_event_pets_is_null_not_error() {
        val h = HubParser.parse("{\"daily\":null}")
        assertNull(h.eventPets)
        assertNull(h.sectionErrors)
    }

    @Test fun bad_event_pets_only_breaks_its_own_section() {
        val h = HubParser.parse("{\"event_pets\":{\"found_count\":\"abc\"},\"event_mode\":true}")
        assertNull(h.eventPets)
        assertTrue(h.sectionErrors!!.containsKey("event_pets"))
        assertEquals(true, h.eventMode)
    }

    @Test fun summary_line_tones() {
        val some = HubSummary.eventPetsLine(HubParser.parse(json).eventPets)
        assertEquals("November: đã tìm thấy 1/2 pet sự kiện", some.text)
        assertEquals(Tone.INFO, some.tone)
        val all = HubSummary.eventPetsLine(HubEventPets("November", 1L, true, 2, 2, emptyList<HubEventPet>() + HubEventPet(found = true) + HubEventPet(found = true)))
        assertEquals("November: đã tìm đủ 2/2 pet sự kiện", all.text)
        assertEquals(Tone.OK, all.tone)
        val none = HubSummary.eventPetsLine(HubEventPets("November", 1L, true, 0, 2, listOf(HubEventPet(found = false), HubEventPet(found = false))))
        assertEquals(Tone.WARN, none.tone)   // sự kiện đang chạy mà chưa thấy con nào -> nhắc
        val over = HubSummary.eventPetsLine(HubEventPets("November", 1L, false, 0, 2, listOf(HubEventPet(found = false), HubEventPet(found = false))))
        assertEquals(Tone.IDLE, over.tone)
        assertTrue(over.text.endsWith("(đã kết thúc)"))
        assertEquals(Tone.IDLE, HubSummary.eventPetsLine(null).tone)
        assertEquals(Tone.IDLE, HubSummary.eventPetsLine(HubEventPets()).tone)
    }

    @Test fun pet_title_and_detail() {
        val pets = HubParser.parse(json).eventPets!!.pets!!
        assertEquals("✅ Pumpkin Pie — đã tìm thấy", HubFormat.eventPetTitle(pets[0]))
        assertEquals("đang giữ 2 · thấy 3 lần khi hunt · lần cuối 5 phút trước", HubFormat.eventPetDetail(pets[0]))
        assertEquals("đang giữ 2 · thấy 3 lần khi hunt · lần cuối 6 phút trước", HubFormat.eventPetDetail(pets[0], 60))
        assertEquals("⬜ Autumn Hsin — chưa tìm thấy", HubFormat.eventPetTitle(pets[1]))
        assertEquals("", HubFormat.eventPetDetail(pets[1]))
        assertEquals("⬜ ? — chưa tìm thấy", HubFormat.eventPetTitle(HubEventPet()))
    }

    @Test fun ends_text_uses_given_zone_and_knows_when_over() {
        val e = HubParser.parse(json).eventPets!!
        assertEquals("Sự kiện November kết thúc lúc 29/11 14:59", HubFormat.eventEnds(e, ZoneId.of("Asia/Ho_Chi_Minh"), nowEpoch = 1795000000L))
        assertEquals("Sự kiện November đã kết thúc", HubFormat.eventEnds(e, ZoneId.of("UTC"), nowEpoch = 1795939140L))
        assertEquals("", HubFormat.eventEnds(HubEventPets(title = "X"), ZoneId.of("UTC")))
    }
}
