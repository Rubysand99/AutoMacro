package com.starclan.starhub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HubDexQueryTest {
    init { TestTexts.install() }   // chu doc tu strings.xml that

    private fun pet(
        name: String?,
        owned: Boolean? = null,
        rank: String? = null,
        rankVi: String? = null,
        hp: Long? = null, att: Long? = null, pr: Long? = null,
        wp: Long? = null, mag: Long? = null, mr: Long? = null
    ) = HubPetDexEntry(
        name = name, owned = owned, rank = rank, rankVi = rankVi,
        hp = hp, att = att, pr = pr, wp = wp, mag = mag, mr = mr
    )

    private fun names(list: List<HubPetDexEntry>) = list.map { it.name }

    // ---- total ----

    @Test fun total_sums_six_stats_and_treats_missing_as_zero() {
        assertEquals(21L, HubDexQuery.total(pet("x", hp = 1, att = 2, pr = 3, wp = 4, mag = 5, mr = 6)))
        assertEquals(7L, HubDexQuery.total(pet("x", hp = 3, mr = 4)))
        assertEquals(0L, HubDexQuery.total(pet("x")))
    }

    // ---- hang (rank) ----

    @Test fun rankOf_prefers_vietnamese_trims_and_falls_back() {
        assertEquals("Hiếm", HubDexQuery.rankOf(pet("x", rank = "rare", rankVi = " Hiếm ")))
        assertEquals("rare", HubDexQuery.rankOf(pet("x", rank = "rare")))
        assertNull(HubDexQuery.rankOf(pet("x")))
        assertNull(HubDexQuery.rankOf(pet("x", rank = "   ")))
    }

    @Test fun ranks_are_distinct_and_keep_first_seen_order() {
        val list = listOf(
            pet("a", rankVi = "Hiếm"),
            pet("b", rankVi = "Thường"),
            pet("c", rankVi = "Hiếm"),
            pet("d")
        )
        assertEquals(listOf("Hiếm", "Thường"), HubDexQuery.ranks(list))
    }

    // ---- loc ----

    @Test fun filter_owned_and_missing() {
        val list = listOf(
            pet("have", owned = true),
            pet("no", owned = false),
            pet("unknown", owned = null)
        )
        assertEquals(listOf("have", "no", "unknown"), names(HubDexQuery.apply(list, DexSort.DEFAULT, DexFilter.ALL, null)))
        assertEquals(listOf("have"), names(HubDexQuery.apply(list, DexSort.DEFAULT, DexFilter.OWNED, null)))
        // owned = null (bot chua biet) tinh la CHUA CO
        assertEquals(listOf("no", "unknown"), names(HubDexQuery.apply(list, DexSort.DEFAULT, DexFilter.MISSING, null)))
    }

    @Test fun filter_by_rank_combines_with_owned_filter() {
        val list = listOf(
            pet("a", owned = true, rankVi = "Hiếm"),
            pet("b", owned = false, rankVi = "Hiếm"),
            pet("c", owned = true, rankVi = "Thường")
        )
        assertEquals(listOf("a", "b"), names(HubDexQuery.apply(list, DexSort.DEFAULT, DexFilter.ALL, "Hiếm")))
        assertEquals(listOf("a"), names(HubDexQuery.apply(list, DexSort.DEFAULT, DexFilter.OWNED, "Hiếm")))
        assertEquals(emptyList<String?>(), names(HubDexQuery.apply(list, DexSort.DEFAULT, DexFilter.ALL, "Không có")))
    }

    // ---- sap xep ----

    @Test fun default_sort_keeps_bot_order() {
        val list = listOf(pet("z", hp = 1), pet("a", hp = 99), pet("m", hp = 50))
        assertEquals(listOf("z", "a", "m"), names(HubDexQuery.apply(list, DexSort.DEFAULT, DexFilter.ALL, null)))
    }

    @Test fun sort_by_stat_descending_missing_last_then_owned_then_name() {
        val list = listOf(
            pet("Bee", owned = true, hp = 10),
            pet("Ant", owned = false, hp = 10),
            pet("Cat", owned = true, hp = null),
            pet("Dog", owned = null, hp = 30)
        )
        // Dog (30) -> hoa 10: Bee (dang co) truoc Ant -> Cat thieu HP nam cuoi
        assertEquals(
            listOf("Dog", "Bee", "Ant", "Cat"),
            names(HubDexQuery.apply(list, DexSort.HP, DexFilter.ALL, null))
        )
    }

    @Test fun sort_by_total_puts_pets_without_any_stat_last_but_zero_stats_before_them() {
        val list = listOf(
            pet("none"),
            pet("zero", hp = 0),
            pet("ten", hp = 5, att = 5)
        )
        assertEquals(
            listOf("ten", "zero", "none"),
            names(HubDexQuery.apply(list, DexSort.TOTAL, DexFilter.ALL, null))
        )
    }

    @Test fun ties_are_broken_by_name_ignoring_case() {
        val list = listOf(pet("beta", hp = 5), pet("Alpha", hp = 5))
        assertEquals(listOf("Alpha", "beta"), names(HubDexQuery.apply(list, DexSort.HP, DexFilter.ALL, null)))
    }

    @Test fun sort_by_name_is_case_insensitive_and_null_name_first() {
        val list = listOf(pet("banana"), pet("Apple"), pet(null))
        assertEquals(
            listOf(null, "Apple", "banana"),
            names(HubDexQuery.apply(list, DexSort.NAME, DexFilter.ALL, null))
        )
    }

    // ---- dong chi so ----

    @Test fun statsLine_default_order_and_missing_shows_question_mark() {
        val p = pet("x", hp = 1, att = 2, pr = 3, wp = 4, mag = 5)
        assertEquals("HP 1 · ATT 2 · PR 3 · WP 4 · MAG 5 · MR ?", HubDexQuery.statsLine(p, DexSort.DEFAULT))
        assertEquals("HP 1 · ATT 2 · PR 3 · WP 4 · MAG 5 · MR ?", HubDexQuery.statsLine(p, DexSort.NAME))
    }

    @Test fun statsLine_moves_sorted_stat_to_front_with_marker() {
        val p = pet("x", hp = 1, att = 2, pr = 3, wp = 4, mag = 5, mr = 6)
        assertEquals("▸ ATT 2 · HP 1 · PR 3 · WP 4 · MAG 5 · MR 6", HubDexQuery.statsLine(p, DexSort.ATT))
        assertEquals("▸ MR 6 · HP 1 · ATT 2 · PR 3 · WP 4 · MAG 5", HubDexQuery.statsLine(p, DexSort.MR))
    }

    @Test fun statsLine_total_appends_sum() {
        val p = pet("x", hp = 1, att = 2, pr = 3, wp = 4, mag = 5, mr = 6)
        assertEquals("HP 1 · ATT 2 · PR 3 · WP 4 · MAG 5 · MR 6  (tổng 21)", HubDexQuery.statsLine(p, DexSort.TOTAL))
    }
}
