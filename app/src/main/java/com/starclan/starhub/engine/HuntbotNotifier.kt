package com.starclan.starhub.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.starclan.starhub.MainActivity
import com.starclan.starhub.R
import com.starclan.starhub.data.HuntbotAlertLogic

/**
 * Thong bao tren dien thoai "huntbot con ~10 phut nua ve". Moi chuyen san chi bao 1 lan
 * (nho gio ve cua chuyen da bao trong SharedPreferences nen khoi dong lai app van khong bao lap).
 */
object HuntbotNotifier {
    private const val CHANNEL_ID = "huntbot_alert"
    private const val PREFS = "huntbot_alert"
    private const val KEY_LAST_READY = "last_ready_epoch"
    private const val NOTIFICATION_ID = 4101

    /**
     * Goi dinh ky voi du lieu huntbot bot tra ve. Tra ve true neu VUA gui thong bao.
     * Chua cap quyen thong bao -> khong gui va KHONG danh dau da bao, nen neu Ruby cap quyen trong 10 phut
     * do thi lan hoi sau van kip bao.
     */
    fun maybeNotify(context: Context, hunting: Boolean, secondsLeft: Long?, readyEpoch: Long?): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = if (prefs.contains(KEY_LAST_READY)) prefs.getLong(KEY_LAST_READY, 0L) else null
        if (!HuntbotAlertLogic.shouldNotify(hunting, secondsLeft, readyEpoch, last)) return false
        val left = secondsLeft ?: return false
        val ready = readyEpoch ?: return false

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.areNotificationsEnabled()) return false

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.huntbot_alert_channel),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val minutes = HuntbotAlertLogic.minutesText(left)
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_huntbot)
            .setContentTitle(context.getString(R.string.huntbot_alert_title))
            .setContentText(context.getString(R.string.huntbot_alert_text, minutes))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        prefs.edit().putLong(KEY_LAST_READY, ready).apply()
        return true
    }
}
