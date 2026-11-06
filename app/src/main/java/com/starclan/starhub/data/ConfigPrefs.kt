package com.starclan.starhub.data

import android.content.Context

/**
 * Noi luu cac lua chon cua Ruby tren may (khong chua bi mat nen dung kho thuong, khac token o OwoTrackerPrefs):
 *  - nhac nen (tab Config)
 *  - ban sao cac cong tac bot + o tim kiem cua tab Cong cu, de mo tab la thay lai lua chon cu ngay
 */
object ConfigPrefs {
    private const val NAME = "star_config"

    private const val K_MUSIC_ON = "music_on"
    private const val K_MUSIC_URI = "music_uri"
    private const val K_MUSIC_NAME = "music_name"
    private const val K_MUSIC_VOL = "music_vol"

    private const val K_TOOLS_SAVED = "tools_saved"
    private const val K_T_AUTO = "tools_auto_check"
    private const val K_T_HBAUTO = "tools_hb_auto"
    private const val K_T_HBALERT = "tools_hb_alert"
    private const val K_T_EVENT = "tools_event"
    private const val K_T_POWER = "tools_power"
    private const val K_T_SEARCH = "tools_search"

    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun loadMusic(ctx: Context): MusicConfig {
        val s = p(ctx)
        return MusicConfig(
            enabled = s.getBoolean(K_MUSIC_ON, false),
            uri = s.getString(K_MUSIC_URI, "") ?: "",
            name = s.getString(K_MUSIC_NAME, "") ?: "",
            volume = MusicConfig.clampVolume(s.getInt(K_MUSIC_VOL, MusicConfig.DEFAULT_VOLUME))
        )
    }

    fun saveMusic(ctx: Context, m: MusicConfig) {
        p(ctx).edit()
            .putBoolean(K_MUSIC_ON, m.enabled)
            .putString(K_MUSIC_URI, m.uri)
            .putString(K_MUSIC_NAME, m.name)
            .putInt(K_MUSIC_VOL, MusicConfig.clampVolume(m.volume))
            .apply()
    }

    /** null = chua tung luu lan nao (lan dau mo tab Cong cu) */
    fun loadToolsSettings(ctx: Context): BotSettings? {
        val s = p(ctx)
        if (!s.getBoolean(K_TOOLS_SAVED, false)) return null
        return BotSettings(
            autoCheck = s.getBoolean(K_T_AUTO, true),
            hbAuto = s.getBoolean(K_T_HBAUTO, true),
            hbAlert = s.getBoolean(K_T_HBALERT, false),
            eventMode = s.getBoolean(K_T_EVENT, false),
            appOn = s.getBoolean(K_T_POWER, true),
            captchaActive = false
        )
    }

    fun saveToolsSettings(ctx: Context, st: BotSettings) {
        val v = ToolsCache.forStorage(st)
        p(ctx).edit()
            .putBoolean(K_TOOLS_SAVED, true)
            .putBoolean(K_T_AUTO, v.autoCheck)
            .putBoolean(K_T_HBAUTO, v.hbAuto)
            .putBoolean(K_T_HBALERT, v.hbAlert)
            .putBoolean(K_T_EVENT, v.eventMode)
            .putBoolean(K_T_POWER, v.appOn)
            .apply()
    }

    fun loadToolsSearch(ctx: Context): String = p(ctx).getString(K_T_SEARCH, "") ?: ""

    fun saveToolsSearch(ctx: Context, q: String) {
        p(ctx).edit().putString(K_T_SEARCH, q).apply()
    }
}
