package com.starclan.starhub.data

import com.google.gson.JsonParser

/** Cong tac phia bot (GET/POST /settings) — cung khoa voi ,autocheck ,hbauto ,hbalert ,eventmode ,off ,captcha */
data class BotSettings(
    val autoCheck: Boolean = true,
    val hbAuto: Boolean = true,
    val hbAlert: Boolean = false,
    val eventMode: Boolean = false,
    val appOn: Boolean = true,
    val captchaActive: Boolean = false
)

/** 1 ghi chu (lenh ,note) */
data class BotNote(val id: Int, val text: String, val done: Boolean)

/** GET /teamcheck (lenh ,teamcheck). `text` bot da bo ky hieu Discord, hien thang duoc. members = 0: chua co doi hinh. */
data class BotTeamCheck(val text: String, val members: Int)

/** GET /zoodiff (lenh ,zoodiff). ok = false: chua du 2 lan ghi, `text` noi ro phai lam gi. kind: "current" (dang giu) | "all" (da bat) | null. */
data class BotZooDiff(val ok: Boolean, val kind: String?, val text: String)

/** 1 bac cua GET /unlocked (lenh ,unlocked): ten pet da mo khoa, khong kem so luong. */
data class BotUnlockedTier(val tier: String, val count: Int, val pets: List<String>)

data class BotUnlocked(val total: Int, val tiers: List<BotUnlockedTier>)

/** GET/POST /alt (lenh ,setalt). userId rong = da tat DM acc phu (tin nhac huntbot gui acc chinh). */
data class BotAlt(val userId: String)

/** POST /alt/test (lenh ,testalt). reason: "" (on) | "no_alt" (chua dat) | "dm_failed" (khong DM duoc). */
data class BotAltTest(val ok: Boolean, val userId: String, val reason: String)

/** Cong tac co the bat/tat qua POST /settings. `key` la ten bot hieu. */
enum class BotToggle(val key: String) {
    AUTO_CHECK("auto_check"),
    HB_AUTO("hb_auto"),
    HB_ALERT("hb_alert")
}

/** Doc JSON tra ve tu bot. Thieu / sai kieu -> dung gia tri mac dinh (giong phia bot), khong nem loi. */
object BotToolsJson {
    private fun bool(o: com.google.gson.JsonObject, k: String, def: Boolean): Boolean {
        val e = o.get(k) ?: return def
        return if (e.isJsonPrimitive && e.asJsonPrimitive.isBoolean) e.asBoolean else def
    }

    fun parseSettings(json: String): BotSettings? = try {
        val o = JsonParser.parseString(json).asJsonObject
        BotSettings(
            autoCheck = bool(o, "auto_check", true),
            hbAuto = bool(o, "hb_auto", true),
            hbAlert = bool(o, "hb_alert", false),
            eventMode = bool(o, "event_mode", false),
            appOn = bool(o, "app_on", true),
            captchaActive = bool(o, "captcha_active", false)
        )
    } catch (e: Exception) {
        null
    }

    fun parseNotes(json: String): List<BotNote>? = try {
        val arr = JsonParser.parseString(json).asJsonObject.getAsJsonArray("notes")
        arr.mapNotNull { el ->
            val o = el.asJsonObject
            val id = o.get("id")?.takeIf { it.isJsonPrimitive }?.asInt ?: return@mapNotNull null
            BotNote(id, o.get("text")?.takeIf { it.isJsonPrimitive }?.asString ?: "", bool(o, "done", false))
        }
    } catch (e: Exception) {
        null
    }

    private fun str(o: com.google.gson.JsonObject, k: String): String? {
        val e = o.get(k) ?: return null
        return if (e.isJsonPrimitive && e.asJsonPrimitive.isString) e.asString else null
    }

    private fun int(o: com.google.gson.JsonObject, k: String): Int? {
        val e = o.get(k) ?: return null
        return if (e.isJsonPrimitive && e.asJsonPrimitive.isNumber) e.asInt else null
    }

    /** Thieu `text` (khong dung dang) -> null; thieu `members` -> 0. */
    fun parseTeamCheck(json: String): BotTeamCheck? {
        return try {
            val o = JsonParser.parseString(json).asJsonObject
            val text = str(o, "text") ?: return null
            BotTeamCheck(text, int(o, "members") ?: 0)
        } catch (e: Exception) {
            null
        }
    }

    /** Thieu `text` -> null; thieu `ok` -> false (than trong: khong nhan la co ket qua so). */
    fun parseZooDiff(json: String): BotZooDiff? {
        return try {
            val o = JsonParser.parseString(json).asJsonObject
            val text = str(o, "text") ?: return null
            BotZooDiff(bool(o, "ok", false), str(o, "kind"), text)
        } catch (e: Exception) {
            null
        }
    }

    /** Thieu `tiers` -> null. Bac thieu ten bi bo; `count` thieu thi lay so ten pet; `total` thieu thi cong cac bac. */
    fun parseUnlocked(json: String): BotUnlocked? {
        return try {
            val o = JsonParser.parseString(json).asJsonObject
            val arr = o.getAsJsonArray("tiers") ?: return null
            val tiers = arr.mapNotNull { el ->
                val t = el.asJsonObject
                val name = str(t, "tier") ?: return@mapNotNull null
                val pets = t.getAsJsonArray("pets")
                    ?.mapNotNull { p -> if (p.isJsonPrimitive && p.asJsonPrimitive.isString) p.asString else null }
                    ?: emptyList()
                BotUnlockedTier(name, int(t, "count") ?: pets.size, pets)
            }
            BotUnlocked(int(o, "total") ?: tiers.sumOf { it.count }, tiers)
        } catch (e: Exception) {
            null
        }
    }

    /** `alt_user_id` rong la hop le (= da tat); thieu han -> null. */
    fun parseAlt(json: String): BotAlt? {
        return try {
            val o = JsonParser.parseString(json).asJsonObject
            BotAlt(str(o, "alt_user_id") ?: return null)
        } catch (e: Exception) {
            null
        }
    }

    /** Thieu `ok` -> false; thieu `reason` / `alt_user_id` -> chuoi rong. */
    fun parseAltTest(json: String): BotAltTest? {
        return try {
            val o = JsonParser.parseString(json).asJsonObject
            BotAltTest(bool(o, "ok", false), str(o, "alt_user_id") ?: "", str(o, "reason") ?: "")
        } catch (e: Exception) {
            null
        }
    }

    /** Body POST /alt. userId rong = tat. Dung Gson de ID luon la chuoi JSON hop le. */
    fun altBody(userId: String) = com.google.gson.JsonObject().apply {
        addProperty("user_id", userId)
    }.toString()

    fun toggleBody(toggle: BotToggle, enabled: Boolean) = """{"key":"${toggle.key}","enabled":$enabled}"""

    fun addNoteBody(text: String) = com.google.gson.JsonObject().apply {
        addProperty("action", "add"); addProperty("text", text)
    }.toString()

    fun appConfigBody(disabledIds: List<String>) = com.google.gson.JsonObject().apply {
        add("disabled", com.google.gson.JsonArray().also { a -> disabledIds.forEach { a.add(it) } })
    }.toString()

    fun doneNoteBody(id: Int) = """{"action":"done","id":$id}"""
}

/**
 * Chu hien thi + kiem tra dau vao cho cac muc moi cua tab Cong cu (doi hinh / zoo / pet da mo khoa / acc phu).
 * Thuan Kotlin (khong dung Android) de test duoc; ToolsFragment chi viec hien.
 */
object BotToolsText {
    private val ALT_ID = Regex("^\\d{15,25}$")

    /** Giong kiem tra o bot (POST /alt): day 15-25 chu so. Rong KHONG hop le o day — muon tat thi bam nut Tat. */
    fun isValidAltId(raw: String): Boolean = ALT_ID.matches(raw.trim())

    fun altStatus(alt: BotAlt?): String = when {
        alt == null -> "Không hỏi được bot — kiểm tra mạng, token, hoặc cập nhật bot (cần đường /alt)."
        alt.userId.isBlank() -> "Đang TẮT — tin nhắc huntbot gửi thẳng cho acc chính."
        else -> "Đang gửi DM nhắc huntbot cho: ${alt.userId}"
    }

    fun altTestMessage(r: BotAltTest?): String = when {
        r == null -> "Không gửi được yêu cầu tới bot (kiểm tra mạng / token / cập nhật bot)."
        r.ok -> "Đã gửi DM thử cho ${r.userId}. Vào acc phụ kiểm tra giúp."
        r.reason == "no_alt" -> "Chưa đặt acc phụ — nhập ID rồi bấm Lưu."
        else -> "Không DM được ${r.userId}. Acc phụ phải chung server với bot và bật " +
            "\"Cho phép tin nhắn trực tiếp từ thành viên máy chủ\" (Cài đặt quyền riêng tư)."
    }

    fun unlockedText(u: BotUnlocked): String {
        if (u.total <= 0 || u.tiers.isEmpty()) return "Chưa có dữ liệu zoo. Gõ owo zoo trong kênh farm một lần."
        val body = u.tiers.joinToString("\n\n") { "${it.tier} (${it.count})\n${it.pets.joinToString(", ")}" }
        return "Đã mở khóa ${u.total} con\n\n$body"
    }
}
