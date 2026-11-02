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

/** Doc / tao JSON cho /gemtier. THUAN Kotlin (khong dung Android) nen test bang JVM duoc. */
object GemTierJson {
    private val gson = Gson()

    /** Nem JsonParseException neu JSON khong dung dang hoac thieu danh sach loai gem/tier. */
    fun parse(body: String): GemTierConfig {
        val cfg = gson.fromJson(body, GemTierConfig::class.java) ?: throw JsonParseException("Dữ liệu tier gem trống")
        if (cfg.tiers.isEmpty() || cfg.slots.isEmpty()) throw JsonParseException("Dữ liệu tier gem thiếu loại gem hoặc tier")
        return cfg
    }

    /** {"allowed":{"1":["Common","Rare"],"4":[]}} — gui cac loai da chinh. */
    fun buildSaveBody(allowedBySlot: Map<String, List<String>>): String =
        gson.toJson(mapOf("allowed" to allowedBySlot))
}
