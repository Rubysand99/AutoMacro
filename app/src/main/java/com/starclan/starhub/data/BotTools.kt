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

    fun toggleBody(toggle: BotToggle, enabled: Boolean) = """{"key":"${toggle.key}","enabled":$enabled}"""

    fun addNoteBody(text: String) = com.google.gson.JsonObject().apply {
        addProperty("action", "add"); addProperty("text", text)
    }.toString()

    fun doneNoteBody(id: Int) = """{"action":"done","id":$id}"""
}
