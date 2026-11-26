package com.starclan.starhub.data

/**
 * Chon lenh mo hop theo kho (`inventory` trong /hub): chi go khi kho CON dung loai hop do.
 * Thuan Kotlin de test bang JVM.
 */
object BoxRules {
    /** `item` = ten emoji cua mon trong `owo inv` (bot gui nguyen van). */
    data class Box(val item: String, val command: String, val sharesCrateCooldown: Boolean = false)

    val BOXES: List<Box> = listOf(
        Box("box", "owo lb all"),        // lootbox
        Box("crate", "owo crate all", sharesCrateCooldown = true),   // weapon crate
        Box("bcrate", "owo bwc all", sharesCrateCooldown = true)
    )

    /** Bot xoa mon da het sau khi doc kho khoang 20 giay: du lieu moi hon muc nay co the con lan mon cu. */
    const val SETTLE_SECONDS = 25L

    /** Bu sai so khi doi "tuoi du lieu" ra gio may (lam tron giay + do tre mang). */
    const val CLOCK_SLACK_MS = 5_000L

    /** crate va bcrate dung chung cooldown cua OwO: 2 lenh nay phai cach nhau it nhat chung nay */
    const val CRATE_GAP_MS = 60_000L

    /**
     * Cac lenh nen go luc nay. Toi da 1 lenh trong nhom chung cooldown (uu tien lenh lau chua go nhat).
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
        val ready = BOXES.filter { box ->
            val opened = openedAtMs[box.command]
            box.item in owned && (opened == null || readAtMs > opened + CLOCK_SLACK_MS)
        }
        val lastCrateMs = BOXES.filter { it.sharesCrateCooldown }.mapNotNull { openedAtMs[it.command] }.maxOrNull()
        val crate = ready.filter { it.sharesCrateCooldown }
            .takeIf { lastCrateMs == null || nowMs - lastCrateMs >= CRATE_GAP_MS }
            ?.minByOrNull { openedAtMs[it.command] ?: 0L }
        return ready.filter { !it.sharesCrateCooldown || it == crate }.map { it.command }
    }
}
