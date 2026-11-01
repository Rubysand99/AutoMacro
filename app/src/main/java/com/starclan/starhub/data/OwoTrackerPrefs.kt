package com.starclan.starhub.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Luu cai dat ket noi owo-tracker: dia chi API, token, bat/tat, va vi tri
 * (toa do) o nhap tin nhan + nut Gui tren Discord - cai 1 lan duy nhat,
 * dung lai cho MOI lenh sau nay tu owo-tracker gui toi (khong can tao
 * rieng 1 kich ban cho tung loai lenh).
 */
object OwoTrackerPrefs {
    private const val TAG = "OwoTrackerPrefs"
    private const val PREFS_NAME = "owo_tracker_prefs"
    private const val SECURE_PREFS_NAME = "owo_tracker_secure_prefs"
    private const val KEY_API_URL = "api_url"
    private const val KEY_TOKEN = "token"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_MSG_X = "msg_x"
    private const val KEY_MSG_Y = "msg_y"
    private const val KEY_SEND_X = "send_x"
    private const val KEY_SEND_Y = "send_y"
    private const val KEY_DISCORD_LINK = "discord_link"

    data class Settings(
        val enabled: Boolean,
        val apiUrl: String,
        val token: String,
        val messageBoxX: Float,
        val messageBoxY: Float,
        val sendButtonX: Float,
        val sendButtonY: Float,
        /** Link kenh Discord de mo dung cho truoc khi gui lenh (de trong = chi mo app Discord) */
        val discordLink: String = ""
    ) {
        /** True neu Ruby da cham qua wizard "Cai vi tri Discord" it nhat 1 lan */
        val hasDiscordTargets: Boolean
            get() = messageBoxX >= 0f && messageBoxY >= 0f && sendButtonX >= 0f && sendButtonY >= 0f
    }


    @Volatile private var securePrefs: SharedPreferences? = null

    /**
     * Noi luu TOKEN: EncryptedSharedPreferences (khoa nam trong Android Keystore). Neu thiet bi khong tao duoc
     * (loi Keystore hiem gap) thi quay ve SharedPreferences thuong de app van chay duoc.
     */
    private fun secure(context: Context): SharedPreferences {
        securePrefs?.let { return it }
        synchronized(this) {
            securePrefs?.let { return it }
            val app = context.applicationContext
            val created = try {
                val key = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
                EncryptedSharedPreferences.create(
                    app, SECURE_PREFS_NAME, key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                Log.w(TAG, "Khong tao duoc kho ma hoa (${e.message}) - dung kho thuong")
                app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
            securePrefs = created
            return created
        }
    }

    /** Doc token; neu con ban cu o kho thuong thi chuyen sang kho ma hoa roi xoa ban cu. */
    private fun readToken(context: Context): String {
        val plain = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sec = secure(context)
        val old = plain.getString(KEY_TOKEN, null)
        if (old != null && sec !== plain) {
            if (sec.getString(KEY_TOKEN, null).isNullOrEmpty() && old.isNotEmpty()) {
                sec.edit().putString(KEY_TOKEN, old).apply()
            }
            plain.edit().remove(KEY_TOKEN).apply()
        }
        return sec.getString(KEY_TOKEN, "") ?: ""
    }

    fun load(context: Context): Settings {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return Settings(
            enabled = p.getBoolean(KEY_ENABLED, false),
            apiUrl = p.getString(KEY_API_URL, "") ?: "",
            token = readToken(context),
            messageBoxX = p.getFloat(KEY_MSG_X, -1f),
            messageBoxY = p.getFloat(KEY_MSG_Y, -1f),
            sendButtonX = p.getFloat(KEY_SEND_X, -1f),
            sendButtonY = p.getFloat(KEY_SEND_Y, -1f),
            discordLink = p.getString(KEY_DISCORD_LINK, "") ?: ""
        )
    }

    fun saveConnection(context: Context, enabled: Boolean, apiUrl: String, token: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putString(KEY_API_URL, apiUrl)
            .apply()
        val sec = secure(context)
        sec.edit().putString(KEY_TOKEN, token).apply()
        // Neu kho ma hoa khong dung duoc (dang dung kho thuong) thi token da nam o kho thuong roi; neu khong thi xoa ban cu
        val plain = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (sec !== plain) plain.edit().remove(KEY_TOKEN).apply()
    }

    fun saveDiscordLink(context: Context, link: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_DISCORD_LINK, link.trim())
            .apply()
    }

    fun saveDiscordTargets(context: Context, msgX: Float, msgY: Float, sendX: Float, sendY: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_MSG_X, msgX)
            .putFloat(KEY_MSG_Y, msgY)
            .putFloat(KEY_SEND_X, sendX)
            .putFloat(KEY_SEND_Y, sendY)
            .apply()
    }
}
