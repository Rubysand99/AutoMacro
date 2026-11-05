package com.starclan.starhub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HubSearchTest {

    private val hub = HubResponse(
        zoo = HubZoo(pets = listOf(
            HubPet(tier = "Common", name = "Cá voi", count = 3),
            HubPet(tier = "Rare", name = "Cá sấu", count = 9),
            HubPet(tier = "Epic", name = "Rồng", count = 1)
        )),
        petDex = HubPetDex(species = listOf(
            HubPetDexEntry(name = "Dragon", rank = "Epic", rankVi = "Sử thi", owned = true, ownedCount = 2, hp = 100, att = 50),
            HubPetDexEntry(name = "Cat", rank = "Common", rankVi = "Thường", owned = false)
        )),
        inventory = HubInventory(items = listOf(
            HubItem(code = "051", name = "Gem hunting", count = 5, isGem = true),
            HubItem(code = "100", name = "Lootbox", count = 12)
        )),
        weapons = HubWeapons(weapons = listOf(
            HubWeapon(id = "A1", typeName = "Sword", quality = 88.0, passiveName = "Burn"),
            HubWeapon(id = "B2", typeName = "Bow", quality = 95.0, passiveName = "Poison")
        ))
    )

    @Test fun short_or_empty_query_gives_nothing() {
        assertTrue(HubSearch.search(hub, "").hits.isEmpty())
        assertTrue(HubSearch.search(hub, "a").hits.isEmpty())
        assertTrue(HubSearch.search(null, "cat").hits.isEmpty())
    }

    @Test fun norm_strips_accents_and_case() {
        assertEquals("ca voi", HubSearch.norm("  Cá   VOI "))
        assertEquals("rong", HubSearch.norm("Rồng"))
        assertEquals("duong", HubSearch.norm("Đường"))
        assertEquals("", HubSearch.norm(null))
    }

    @Test fun typing_without_accents_finds_accented_names_sorted_by_count() {
        val r = HubSearch.search(hub, "ca")
        val zoo = r.hits.filter { it.kind == HubSearch.KIND_ZOO }
        assertEquals(listOf("Cá sấu", "Cá voi"), zoo.map { it.title })   // x9 truoc x3
        assertEquals("Rare · x9", zoo[0].detail)
    }

    @Test fun finds_across_groups() {
        val kinds = HubSearch.search(hub, "ro").hits.map { it.kind }.toSet()
        assertTrue(HubSearch.KIND_ZOO in kinds)   // Rong
        val w = HubSearch.search(hub, "poison").hits
        assertEquals(1, w.size)
        assertEquals(HubSearch.KIND_WEAPON, w[0].kind)
        assertTrue(w[0].detail.contains("95%"))
        assertTrue(w[0].detail.contains("ID B2"))
    }

    @Test fun dex_shows_owned_state_and_stats() {
        val dex = HubSearch.search(hub, "dragon").hits.single { it.kind == HubSearch.KIND_DEX }
        assertTrue(dex.detail.contains("đang có x2"))
        assertTrue(dex.detail.contains("HP 100"))
        val cat = HubSearch.search(hub, "cat").hits.single { it.kind == HubSearch.KIND_DEX }
        assertTrue(cat.detail.contains("chưa có"))
    }

    @Test fun inventory_matches_name_and_code() {
        assertEquals("Gem hunting", HubSearch.search(hub, "gem").hits.single { it.kind == HubSearch.KIND_INV }.title)
        assertEquals("Lootbox", HubSearch.search(hub, "100").hits.single { it.kind == HubSearch.KIND_INV }.title)
    }

    @Test fun long_results_are_capped_per_group() {
        val many = HubResponse(zoo = HubZoo(pets = (1..40).map { HubPet(tier = "Common", name = "Pet$it", count = it.toLong()) }))
        val r = HubSearch.search(many, "pet")
        assertEquals(HubSearch.PER_KIND_LIMIT, r.hits.size)
        assertEquals(40 - HubSearch.PER_KIND_LIMIT, r.hiddenByKind[HubSearch.KIND_ZOO])
    }

    @Test fun missing_sections_do_not_crash() {
        assertTrue(HubSearch.search(HubResponse(), "abc").hits.isEmpty())
    }
}
