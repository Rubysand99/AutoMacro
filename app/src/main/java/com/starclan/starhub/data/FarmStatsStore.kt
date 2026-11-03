package com.starclan.starhub.data

import android.content.Context

/** Doc / ghi thong ke farm vao SharedPreferences rieng (khong chua token). Loi gi cung chi bo qua. */
object FarmStatsStore {
    private const val PREFS = "starhub_stats"
    private const val KEY = "days"

    fun load(context: Context): StatsBook = try {
        StatsBook.fromJson(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))
    } catch (e: Exception) {
        StatsBook()
    }

    fun save(context: Context, book: StatsBook) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, book.toJson()).apply()
            book.markSaved()
        } catch (e: Exception) {
            // thong ke khong quan trong bang farm: bo qua
        }
    }
}
