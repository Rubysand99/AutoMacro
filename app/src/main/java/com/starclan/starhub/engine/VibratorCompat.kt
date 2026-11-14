package com.starclan.starhub.engine

import android.content.Context
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager

/** Lay Vibrator mac dinh: Android 12+ dung VibratorManager (VIBRATOR_SERVICE da bi danh dau deprecated), may cu dung cach cu. */
object VibratorCompat {
    fun get(ctx: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}
