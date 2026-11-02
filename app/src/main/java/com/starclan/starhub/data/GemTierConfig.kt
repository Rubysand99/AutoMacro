package com.starclan.starhub.data

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.annotations.SerializedName

/** Gem con trong kho (1 tier). */
data class GemStock(val tier: String? = null, val count: Long? = null)

/** 1 loai gem (Hunting / Empowering / Lucky / Special) va cac tier DUOC PHEP dung khi bot nho app thay gem. */
data class GemTierSlot(
    val slot: String = "",
    val name: String = "",
    val allowed: List<String> = emptyList(),
    val pref: String? = null,
    val stock: List<GemStock> = emptyList()
)

/** Tra loi cua GET/POST /gemtier. `tiers` = moi tier tu thap -> cao. */
data class GemTierConfig(
    val tiers: List<String> = emptyList(),
    val slots: List<GemTierSlot> = emptyList()
)

/** Nhung gi Ruby vua doi trong hop thoai: tier duoc phep va/hoac uu tien, chi gom cac loai co thay doi. */
data class GemTierChanges(
    val allowed: Map<String, List<String>> = emptyMap(),
    val pref: Map<String, String> = emptyMap()
) {
    fun isEmpty() = allowed.isEmpty() && pref.isEmpty()
}

/** Cac lua chon "uu tien": cao nhat / thap nhat / 1 tier cu the. Gia tri gui bot: highest | lowest | ten tier. */
object GemPref {
    const val HIGHEST = "highest"
    const val LOWEST = "lowest"

    /** Danh sach gia tri theo thu tu hien trong o chon: [highest, lowest, tier1, tier2, ...]. */
    fun options(tiers: List<String>): List<String> = listOf(HIGHEST, LOWEST) + tiers

    /** Vi tri cua `pref` trong options(); gia tri la/null thi ve "cao nhat" (giong bot). */
    fun indexOf(pref: String?, tiers: List<String>): Int =
        options(tiers).indexOf(pref).let { if (it < 0) 0 else it }
}

/** Doc / tao JSON cho /gemtier. THUAN Kotlin (khong dung Android) nen test bang JVM duoc. */
object GemTierJson {
    private val gson = Gson()

    /** Nem JsonParseException neu JSON khong dung dang hoac thieu danh sach loai gem/tier. */
    fun parse(body: String): GemTierConfig {
        val cfg = gson.fromJson(body, GemTierConfig::class.java) ?: throw JsonParseException("Dữ liệu tier gem trống")
        if (cfg.tiers.isEmpty() || cfg.slots.isEmpty()) throw JsonParseException("Dữ liệu tier gem thiếu loại gem hoặc tier")
        return cfg
    }

    /** {"allowed":{"1":["Common","Rare"],"4":[]},"pref":{"1":"highest"}} — chi gom phan da doi (rong thi bo khoa). */
    fun buildSaveBody(changes: GemTierChanges): String {
        val body = linkedMapOf<String, Any>()
        if (changes.allowed.isNotEmpty()) body["allowed"] = changes.allowed
        if (changes.pref.isNotEmpty()) body["pref"] = changes.pref
        return gson.toJson(body)
    }
}
