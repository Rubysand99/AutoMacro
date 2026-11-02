package com.starclan.starhub.data

import com.google.gson.JsonParseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GemTierJsonTest {
    private val sample = """
        {"tiers":["Common","Uncommon","Rare"],
         "slots":[{"slot":"1","name":"Hunting","allowed":["Common","Rare"],"pref":"highest",
                   "stock":[{"tier":"Rare","count":2}]},
                  {"slot":"4","name":"Lucky","allowed":[],"pref":"Rare","stock":[]}]}
    """.trimIndent()

    @Test fun doc_loai_gem_va_tier_duoc_phep() {
        val cfg = GemTierJson.parse(sample)
        assertEquals(listOf("Common", "Uncommon", "Rare"), cfg.tiers)
        assertEquals(listOf("1", "4"), cfg.slots.map { it.slot })
        assertEquals(listOf("Common", "Rare"), cfg.slots[0].allowed)
        assertEquals(2L, cfg.slots[0].stock[0].count)
    }

    @Test fun tat_het_tier_la_danh_sach_rong() = assertTrue(GemTierJson.parse(sample).slots[1].allowed.isEmpty())

    @Test(expected = JsonParseException::class) fun thieu_loai_gem_thi_bao_loi() {
        GemTierJson.parse("""{"tiers":["Common"],"slots":[]}""")
    }

    @Test(expected = JsonParseException::class) fun khong_phai_json_thi_bao_loi() {
        GemTierJson.parse("khong phai json")
    }

    @Test fun body_luu_dung_dang_bot_doc() =
        assertEquals("""{"allowed":{"1":["Common","Rare"],"4":[]}}""",
            GemTierJson.buildSaveBody(linkedMapOf("1" to listOf("Common", "Rare"), "4" to emptyList())))
}
