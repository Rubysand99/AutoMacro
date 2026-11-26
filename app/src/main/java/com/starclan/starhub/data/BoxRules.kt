package com.starclan.starhub.data

/**
 * Chon lenh mo hop theo kho (`inventory` trong /hub): chi go khi kho CON dung loai hop do.
 * Thuan Kotlin de test bang JVM.
 */
object BoxRules {
    /** `item` = ten emoji cua mon trong `owo inv` (bot gui nguyen van). */
    data class Box(val item: String, val command: String)

    val BOXES: List<Box> = listOf(
        Box("box", "owo lb all"),        // lootbox
        Box("crate", "owo crate all"),   // weapon crate
        Box("bcrate", "owo bwc all")
    )

    /** Bot xoa mon da het sau khi doc kho khoang 20 giay: du lieu moi hon muc nay co the con lan mon cu. */
    const val SETTLE_SECONDS = 25L

    /** Bu sai so khi doi "tuoi du lieu" ra gio may (lam tron giay + do tre mang). */
    const val CLOCK_SLACK_MS = 5_000L

    /**
     * Cac lenh nen go luc nay.
     * @param fetchedAtMs gio may luc lay /hub
     * @param openedAtMs lenh -> gio may lan go thanh cong gan nhat; chi go lai khi bot da doc kho MOI HON lan do
     */
    fun commandsToOpen(
        inventory: HubInventory?,
        fetchedAtMs: Long,
        nowMs: Long,
        openedAtMs: Map<String, Long>
    ): List<String> {
        val age = inventory?.ageSeconds ?: return emptyList()
        if (fetchedAtMs <= 0L) return emptyList()
        val readAtMs = fetchedAtMs - age * 1000
        if ((nowMs - readAtMs) / 1000 < SETTLE_SECONDS) return emptyList()
        val owned = inventory.items.orEmpty()
            .filter { (it.count ?: 0L) > 0L }
            .mapNotNull { it.name?.lowercase() }
            .toSet()
        return BOXES.filter { box ->
            val opened = openedAtMs[box.command]
            box.item in owned && (opened == null || readAtMs > opened + CLOCK_SLACK_MS)
        }.map { it.command }
    }
}
