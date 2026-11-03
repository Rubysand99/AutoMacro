package com.starclan.starhub.data

import com.google.gson.Gson
import java.time.Instant
import java.time.ZoneId

/**
 * Thong ke farm theo NGAY (gio may). Thuan Kotlin (khong dung Android) nen test duoc bang JVM unit test.
 * Luu o may (SharedPreferences, xem FarmStatsStore), khong gui len bot.
 */
data class DayStats(
    val date: String = "",             // yyyy-MM-dd theo gio may
    val hunts: Int = 0,
    val battles: Int = 0,
    val captchas: Int = 0,
    val newPets: Int = 0,
    val sessions: Int = 0,             // so lan bam Bat dau (farm thuc su chay)
    val seconds: Long = 0L,            // tong thoi gian farm dang chay (ke ca luc nghi giua cac lenh)
    val maxSlowdown30m: Int = 0,       // so lan OwO bao "Slow down" trong 30 phut, cao nhat thay duoc trong ngay
    val cashFirst: Long? = null,       // cowoncy o lan DOC dau tien trong ngay (owo cash)
    val cashLast: Long? = null,
    val cashLastAtMs: Long = 0L        // thoi diem doc cashLast (epoch ms) de bo qua lan doc cu hon
)

/**
 * So thong ke nhieu ngay. Moi ham ghi nhan nhan `nowMs` (epoch ms) de test duoc, giu toi da [MAX_DAYS] ngay gan nhat.
 * KHONG an toan luong: noi goi tu khoa (synchronized) khi dung nhieu luong.
 */
class StatsBook(
    private val zone: ZoneId = ZoneId.systemDefault(),
    days: List<DayStats> = emptyList()
) {
    companion object {
        const val MAX_DAYS = 14
        private val gson = Gson()

        /** JSON hong / rong -> so trong (thong ke khong bao gio lam app loi) */
        fun fromJson(json: String?, zone: ZoneId = ZoneId.systemDefault()): StatsBook {
            if (json.isNullOrBlank()) return StatsBook(zone)
            return try {
                StatsBook(zone, gson.fromJson(json, Array<DayStats>::class.java)?.toList().orEmpty())
            } catch (e: Exception) {
                StatsBook(zone)
            }
        }
    }

    private val byDate = LinkedHashMap<String, DayStats>()
    private var activeSinceMs = 0L

    /** Co thay doi chua luu */
    var dirty = false
        private set

    init {
        for (d in days) if (d.date.isNotBlank()) byDate[d.date] = d
        prune()
    }

    fun dateKey(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString()

    private fun prune() {
        if (byDate.size <= MAX_DAYS) return
        val keep = byDate.keys.sortedDescending().take(MAX_DAYS).toSet()
        byDate.keys.retainAll(keep)
    }

    private fun change(ms: Long, f: (DayStats) -> DayStats) {
        val k = dateKey(ms)
        byDate[k] = f(byDate[k] ?: DayStats(date = k))
        prune()
        dirty = true
    }

    // ---------- phien farm ----------

    /** Bat dau tinh gio chay. Goi lai khi dang chay thi cong don phan da chay truoc roi tinh tiep. */
    fun startSession(nowMs: Long) {
        flush(nowMs)
        activeSinceMs = nowMs
        change(nowMs) { it.copy(sessions = it.sessions + 1) }
    }

    /** Dung tinh gio chay (an toan neu chua bat dau / goi 2 lan). */
    fun endSession(nowMs: Long) {
        flush(nowMs)
        activeSinceMs = 0L
    }

    /** Cong thoi gian chay tu lan truoc vao NGAY HIEN TAI (qua nua dem thi phan sau nua dem tinh sang ngay moi). */
    fun flush(nowMs: Long) {
        if (activeSinceMs <= 0L) return
        val s = ((nowMs - activeSinceMs) / 1000L).coerceAtLeast(0L)
        if (s <= 0L) return
        change(nowMs) { it.copy(seconds = it.seconds + s) }
        activeSinceMs += s * 1000L
    }

    // ---------- su kien ----------

    fun addHunt(nowMs: Long) { flush(nowMs); change(nowMs) { it.copy(hunts = it.hunts + 1) } }

    fun addBattle(nowMs: Long) { flush(nowMs); change(nowMs) { it.copy(battles = it.battles + 1) } }

    fun addCaptcha(nowMs: Long) { flush(nowMs); change(nowMs) { it.copy(captchas = it.captchas + 1) } }

    fun addNewPets(nowMs: Long, count: Int) {
        if (count <= 0) return
        change(nowMs) { it.copy(newPets = it.newPets + count) }
    }

    /** So lan "Slow down" trong 30 phut ma bot bao: chi nho gia tri cao nhat trong ngay. */
    fun observeSlowdown(nowMs: Long, count30m: Int) {
        if (count30m <= 0) return
        if (count30m <= (byDate[dateKey(nowMs)]?.maxSlowdown30m ?: 0)) return
        change(nowMs) { it.copy(maxSlowdown30m = count30m) }
    }

    /**
     * Cowoncy bot doc duoc (owo cash). Tinh vao ngay cua LAN DOC (nowMs - tuoi cua so lieu), nen so lieu cu tu hom truoc
     * khong bi tinh la "lan doc dau tien cua hom nay". Lan doc cu hon lan da ghi thi bo qua.
     */
    fun observeCash(nowMs: Long, amount: Long, ageSeconds: Long?) {
        if (amount < 0) return
        val readAt = nowMs - (ageSeconds ?: 0L).coerceAtLeast(0L) * 1000L
        val k = dateKey(readAt)
        val cur = byDate[k] ?: DayStats(date = k)
        if (readAt <= cur.cashLastAtMs) return
        if (cur.cashFirst != null && cur.cashLast == amount) return
        byDate[k] = cur.copy(cashFirst = cur.cashFirst ?: amount, cashLast = amount, cashLastAtMs = readAt)
        prune()
        dirty = true
    }

    // ---------- doc ----------

    /** Thong ke hom nay (da cong gio dang chay den bay gio). Ngay chua co gi thi tra ve so 0. */
    fun today(nowMs: Long): DayStats {
        flush(nowMs)
        val k = dateKey(nowMs)
        return byDate[k] ?: DayStats(date = k)
    }

    /** Toi da [limit] ngay gan nhat co so lieu, MOI NHAT truoc (gom ca hom nay neu co). */
    fun recent(nowMs: Long, limit: Int = 7): List<DayStats> {
        flush(nowMs)
        return byDate.values.sortedByDescending { it.date }.take(limit)
    }

    fun clear() {
        byDate.clear()
        activeSinceMs = if (activeSinceMs > 0L) System.currentTimeMillis() else 0L
        dirty = true
    }

    fun toJson(): String = gson.toJson(byDate.values.toList())

    fun markSaved() { dirty = false }
}

/** Dinh dang chu cho man hinh thong ke (thuan, co test) */
object StatsFormat {
    /** 3125 -> "52 phút"; 18720 -> "5h12" */
    fun duration(seconds: Long): String {
        val m = (seconds.coerceAtLeast(0L)) / 60L
        return if (m < 60L) "$m phút" else "${m / 60L}h${(m % 60L).toString().padStart(2, '0')}"
    }

    /** "2026-11-03" -> "03/11" (khong dung duoc thi tra lai nguyen chuoi) */
    fun shortDate(iso: String): String {
        val p = iso.split("-")
        return if (p.size == 3) "${p[2]}/${p[1]}" else iso
    }

    /** Chenh lech co dau: 1250 -> "+1,250"; -300 -> "-300"; 0 -> "0" */
    fun signed(delta: Long): String = when {
        delta > 0L -> "+" + HubFormat.num(delta)
        delta < 0L -> "-" + HubFormat.num(-delta)
        else -> "0"
    }
}
