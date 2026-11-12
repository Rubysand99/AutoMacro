package com.starclan.starhub.data

/** 1 dong ket qua tra cuu. kind = nhom (Pet trong zoo / Dex / Kho / Vu khi) de man hinh gom nhom. */
data class SearchHit(val kind: String, val title: String, val detail: String)

/**
 * Tra cuu tren du lieu Hub da co (khong goi mang): pet trong zoo, dex tung loai, kho do, vu khi.
 * Tuong duong cac lenh ,zoo [tim] ,pets [tim] ,inv [tim] ,myweapons [tim] ben bot.
 * Thuan Kotlin de kiem tra de dang.
 */
object HubSearch {
    val KIND_ZOO: String get() = Texts.get("search_kind_zoo")
    val KIND_DEX: String get() = Texts.get("search_kind_dex")
    val KIND_INV: String get() = Texts.get("search_kind_inv")
    val KIND_WEAPON: String get() = Texts.get("search_kind_weapon")

    /** So ket qua toi da moi nhom (nhieu qua thi hien "con N ket qua nua") */
    const val PER_KIND_LIMIT = 15

    data class Result(val hits: List<SearchHit>, val hiddenByKind: Map<String, Int>)

    /** Bo dau tieng Viet + chu thuong + bo khoang trang thua, de go "ca" van ra "cá" */
    fun norm(s: String?): String {
        if (s == null) return ""
        val d = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
        return d.replace(Regex("\\p{M}+"), "").replace('đ', 'd').trim().replace(Regex("\\s+"), " ")
    }

    private fun matches(query: String, vararg fields: String?): Boolean =
        fields.any { norm(it).contains(query) }

    fun search(hub: HubResponse?, rawQuery: String): Result {
        val q = norm(rawQuery)
        if (hub == null || q.length < 2) return Result(emptyList(), emptyMap())
        val groups = LinkedHashMap<String, List<SearchHit>>()

        val zoo = (hub.zoo?.pets.orEmpty()).filter { matches(q, it.name, it.tier) }
            .sortedWith(compareByDescending<HubPet> { it.count ?: 0L }.thenBy { it.name ?: "" })
            .map { SearchHit(KIND_ZOO, it.name ?: "?", "${it.tier ?: "?"} · x${it.count ?: 0}") }
        groups[KIND_ZOO] = zoo

        val dex = (hub.petDex?.species.orEmpty()).filter { matches(q, it.name, it.rank, it.rankVi) }
            .sortedBy { it.name ?: "" }
            .map { d ->
                val stats = listOfNotNull(
                    d.hp?.let { "HP $it" }, d.att?.let { "ATT $it" }, d.pr?.let { "PR $it" },
                    d.wp?.let { "WP $it" }, d.mag?.let { "MAG $it" }, d.mr?.let { "MR $it" }
                ).joinToString(" ")
                val own = if (d.owned == true) Texts.get("search_owned", d.ownedCount ?: 0) else Texts.get("search_not_owned")
                SearchHit(KIND_DEX, d.name ?: "?", listOfNotNull(d.rankVi ?: d.rank, own, stats.ifEmpty { null }).joinToString(" · "))
            }
        groups[KIND_DEX] = dex

        val inv = (hub.inventory?.items.orEmpty()).filter { matches(q, it.name, it.code) }
            .sortedByDescending { it.count ?: 0L }
            .map { SearchHit(KIND_INV, it.name ?: it.code ?: "?", "x${it.count ?: 0}" + (it.code?.let { c -> " · " + Texts.get("search_code", c) } ?: "")) }
        groups[KIND_INV] = inv

        val weapons = (hub.weapons?.weapons.orEmpty()).filter { matches(q, it.typeName, it.passiveName, it.id) }
            .sortedByDescending { it.quality ?: 0.0 }
            .map { w ->
                val quality = w.quality?.let { "${java.lang.String.format(java.util.Locale.US, "%.0f", it)}%" }
                SearchHit(KIND_WEAPON, w.typeName ?: "?",
                    listOfNotNull(w.id?.let { "ID $it" }, quality, w.passiveName).joinToString(" · "))
            }
        groups[KIND_WEAPON] = weapons

        val hits = ArrayList<SearchHit>()
        val hidden = LinkedHashMap<String, Int>()
        for ((kind, list) in groups) {
            hits += list.take(PER_KIND_LIMIT)
            if (list.size > PER_KIND_LIMIT) hidden[kind] = list.size - PER_KIND_LIMIT
        }
        return Result(hits, hidden)
    }
}
