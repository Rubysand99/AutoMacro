package com.starclan.starhub.data

import com.google.gson.JsonParseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GemTierJsonTest {
    init { TestTexts.install() }   // chu doc tu strings.xml that
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

    @Test fun body_chi_co_allowed() =
        assertEquals("""{"allowed":{"1":["Common","Rare"],"4":[]}}""",
            GemTierJson.buildSaveBody(GemTierChanges(allowed = linkedMapOf("1" to listOf("Common", "Rare"), "4" to emptyList()))))

    @Test fun body_chi_co_uu_tien() =
        assertEquals("""{"pref":{"1":"Rare","4":"lowest"}}""",
            GemTierJson.buildSaveBody(GemTierChanges(pref = linkedMapOf("1" to "Rare", "4" to "lowest"))))

    @Test fun body_co_ca_hai() =
        assertEquals("""{"allowed":{"3":["Epic"]},"pref":{"3":"Epic"}}""",
            GemTierJson.buildSaveBody(GemTierChanges(mapOf("3" to listOf("Epic")), mapOf("3" to "Epic"))))

    @Test fun khong_doi_gi_thi_rong() = assertTrue(GemTierChanges().isEmpty())

    @Test fun o_chon_uu_tien_cao_nhat_thap_nhat_roi_tung_tier() {
        val tiers = listOf("Common", "Uncommon", "Rare")
        assertEquals(listOf("highest", "lowest", "Common", "Uncommon", "Rare"), GemPref.options(tiers))
        assertEquals(0, GemPref.indexOf("highest", tiers))
        assertEquals(1, GemPref.indexOf("lowest", tiers))
        assertEquals(4, GemPref.indexOf("Rare", tiers))
    }

    @Test fun uu_tien_la_hoac_null_thi_ve_cao_nhat() {
        val tiers = listOf("Common", "Rare")
        assertEquals(0, GemPref.indexOf(null, tiers))
        assertEquals(0, GemPref.indexOf("khong-biet", tiers))
    }
}
