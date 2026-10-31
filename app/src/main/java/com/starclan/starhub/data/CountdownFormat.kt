package com.starclan.starhub.data

import java.util.Locale

/** Dinh dang dem nguoc tren Hub noi. Thuan Kotlin nen test bang JVM duoc. */
object CountdownFormat {
    /** 42_300 ms -> "0:43" (lam tron LEN: dung 0:00 luc het gio). Tu 1 gio tro len -> "1:02:03". */
    fun mmss(ms: Long): String {
        val total = (ms.coerceAtLeast(0L) + 999L) / 1000L
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        return if (h > 0L) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    /** Ti le con lai 0..1 cho thanh tien do (thanh co dan ve 0) */
    fun fraction(remainingMs: Long, totalMs: Long): Float {
        if (totalMs <= 0L) return 0f
        return (remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
    }
}
