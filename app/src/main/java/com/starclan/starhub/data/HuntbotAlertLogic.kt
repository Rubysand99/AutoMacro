package com.starclan.starhub.data

/**
 * Khi nao nen bao "huntbot sap ve". THUAN Kotlin (khong dung Android) nen test bang JVM duoc.
 * Cung quy tac voi huntbot_alert_due() ben owo-tracker (auto_refresh.py).
 *
 * secondsLeft la so giay CON LAI do BOT tinh (khong so voi dong ho dien thoai, tranh lech gio).
 * readyEpoch (giay tu 1970) chi dung lam "dau van tay" cua chuyen san de khong bao lap.
 */
object HuntbotAlertLogic {
    const val ALERT_SECONDS = 600L              // bao khi con <= 10 phut
    const val MIN_LEFT_SECONDS = 60L            // con chua toi 1 phut thi thoi (bao muon vo nghia)
    const val SAME_HUNT_WINDOW_SECONDS = 1800L  // gio ve lech < 30 phut = van la CUNG 1 chuyen (OwO chi bao theo phut)

    fun shouldNotify(
        hunting: Boolean,
        secondsLeft: Long?,
        readyEpoch: Long?,
        lastNotifiedEpoch: Long?
    ): Boolean {
        if (!hunting || secondsLeft == null || readyEpoch == null) return false
        if (secondsLeft <= MIN_LEFT_SECONDS || secondsLeft > ALERT_SECONDS) return false
        if (lastNotifiedEpoch != null &&
            kotlin.math.abs(readyEpoch - lastNotifiedEpoch) < SAME_HUNT_WINDOW_SECONDS
        ) return false
        return true
    }

    /** So phut de hien thi, lam tron, it nhat 1. */
    fun minutesText(secondsLeft: Long): Long = maxOf(1L, (secondsLeft + 30L) / 60L)
}
