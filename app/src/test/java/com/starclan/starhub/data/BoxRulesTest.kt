package com.starclan.starhub.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BoxRulesTest {
    private val now = 10_000_000L

    private fun inv(vararg items: Pair<String, Long>, age: Long? = 60L) =
        HubInventory(items = items.map { HubItem(name = it.first, count = it.second) }, ageSeconds = age)

    private fun run(inventory: HubInventory?, opened: Map<String, Long> = emptyMap(), fetched: Long = now) =
        BoxRules.commandsToOpen(inventory, fetched, now, opened)

    @Test fun only_owned_boxes() =
        assertEquals(listOf("owo lb all", "owo bwc all"), run(inv("box" to 2L, "bcrate" to 6L, "cgem1" to 9L)))

    @Test fun all_three() =
        assertEquals(listOf("owo lb all", "owo crate all", "owo bwc all"), run(inv("bcrate" to 1L, "crate" to 1L, "box" to 1L)))

    @Test fun zero_count_is_skipped() = assertEquals(emptyList<String>(), run(inv("box" to 0L, "crate" to 0L)))

    @Test fun no_data_opens_nothing() {
        assertEquals(emptyList<String>(), run(null))
        assertEquals(emptyList<String>(), run(inv("box" to 1L, age = null)))
        assertEquals(emptyList<String>(), run(inv("box" to 1L), fetched = 0L))
    }

    @Test fun waits_until_bot_has_cleaned_inventory() =
        assertEquals(emptyList<String>(), run(inv("box" to 1L, age = 10L)))

    @Test fun not_again_on_old_inventory() =
        assertEquals(listOf("owo crate all"), run(inv("box" to 1L, "crate" to 1L), mapOf("owo lb all" to now - 30_000L)))

    @Test fun again_after_newer_inventory() =
        assertEquals(listOf("owo lb all"), run(inv("box" to 1L), mapOf("owo lb all" to now - 120_000L)))
}
