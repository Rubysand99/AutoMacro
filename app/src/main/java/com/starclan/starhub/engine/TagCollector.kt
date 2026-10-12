package com.starclan.starhub.engine

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.starclan.starhub.data.OwoTrackerApi
import com.starclan.starhub.data.OwoTrackerPrefs
import com.starclan.starhub.service.AutoAccessibilityService
import com.starclan.starhub.view.FarmOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Tu hoc loi khuyen vu khi: vao kenh trong server OwO, lan luot go `?tag <vu khi>`, doi OwO tra loi, DOC NGUYEN CHU
 * tren man hinh roi luu lai. Xong het thi gui len bot (POST /weapon-advice); bot tach passive tot cho tung vu khi
 * va gui FILE weapon_advice.json cho Ruby qua Discord.
 *
 * Chi doc chu tren man hinh, khong giai gi het. Chi chay khi KHONG dang farm (de khong go chong cheo).
 */
object TagCollector {
    /** Kenh trong server OwO co gõ duoc `?tag` (Ruby cung cap) */
    const val TAG_CHANNEL_LINK = "https://discord.com/channels/420104212895105044/1504206244515414047"

    /** 17 loai vu khi chua co loi khuyen (da co: sword, hstaff, bow, shield, vstaff, dagger, fstaff, rstaff, scythe, crune, lscythe) */
    val TAGS = listOf(
        "Orb", "Wand", "Estaff", "Sstaff", "Scepter", "Axe", "Banner", "Pstaff", "Ffish",
        "Lrune", "Cstaff", "Stithe", "BHstaff", "Aedge", "Wbow", "Gaze", "Claw"
    )

    data class Progress(
        val running: Boolean = false,
        val done: Int = 0,
        val total: Int = 0,
        val message: String = ""
    )

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    /** tag (chu thuong) -> toan bo chu tren man hinh sau khi OwO tra loi. Giu lai den khi gui len bot thanh cong. */
    private val results = linkedMapOf<String, String>()

    fun isRunning(): Boolean = job?.isActive == true

    fun stop() {
        job?.cancel()
        job = null
        _progress.value = Progress(false, results.size, TAGS.size, "Đã dừng (đã lưu ${results.size}/${TAGS.size})")
    }

    fun start(service: AutoAccessibilityService, context: Context) {
        if (isRunning()) return
        if (!FarmOverlay.isShown()) {
            _progress.value = Progress(false, results.size, TAGS.size, "Hãy bật Hub nổi trước")
            return
        }
        if (FarmController.isRunning()) {
            _progress.value = Progress(false, results.size, TAGS.size, "Hãy dừng farm trước rồi bấm lại")
            return
        }
        val settings = OwoTrackerPrefs.load(context)
        if (settings.apiUrl.isBlank() || settings.token.isBlank()) {
            _progress.value = Progress(false, results.size, TAGS.size, "Chưa nhập địa chỉ API / token ở Cài đặt")
            return
        }
        job = scope.launch { run(service, settings) }
    }

    private fun fail(message: String) {
        _progress.value = Progress(false, results.size, TAGS.size, "⚠️ $message")
    }

    private suspend fun run(service: AutoAccessibilityService, settings: OwoTrackerPrefs.Settings) {
        val sender = DiscordSender(service)
        val tagSettings = settings.copy(discordLink = TAG_CHANNEL_LINK)
        val todo = TAGS.filter { it.lowercase() !in results.keys }
        var first = true
        for (tag in todo) {
            if (!FarmOverlay.isShown()) return   // Hub noi bi tat -> dung ngay
            _progress.value = Progress(true, results.size, TAGS.size, "Đang hỏi ?tag $tag")
            var sent = false
            for (attempt in 1..3) {
                val r = sender.send("?tag $tag", tagSettings, reopenChannel = first)
                if (r == SendResult.OK) {
                    sent = true
                    break
                }
                if (r == SendResult.DISCORD_NOT_OPEN || r == SendResult.NO_INPUT || r == SendResult.CAPTCHA) {
                    fail("Không vào/gõ được trong kênh server OwO ($r)")
                    return
                }
                delay(15_000L) // co the dinh slowmode cua kenh -> doi roi thu lai
            }
            if (!sent) {
                fail("Không gửi được ?tag $tag (kênh có thể chặn gõ hoặc bật slowmode)")
                return
            }
            first = false
            val lines = waitForReply(service, tag)
            results[tag.lowercase()] = lines.joinToString("\n")
            _progress.value = Progress(true, results.size, TAGS.size, "Đã lưu ?tag $tag")
            delay(Random.nextLong(5_000L, 9_000L))
        }

        _progress.value = Progress(true, results.size, TAGS.size, "Đang gửi lên bot…")
        val ok = OwoTrackerApi.uploadWeaponAdvice(settings.apiUrl, settings.token, results.toMap())
        if (ok) {
            results.clear()
            _progress.value = Progress(false, TAGS.size, TAGS.size, "✅ Xong — bot đã gửi file cho bạn qua Discord")
        } else {
            fail("Đã học xong nhưng chưa gửi được lên bot (bot đã cập nhật chưa?). Bấm lại để gửi")
        }
    }

    /** Doi OwO tra loi `?tag <tag>` (toi da ~12 giay), roi doc lai man hinh cho chac chu da ve du */
    private suspend fun waitForReply(service: AutoAccessibilityService, tag: String): List<String> {
        val want = "?tag $tag".lowercase()
        var last: List<String> = emptyList()
        for (i in 1..8) {
            delay(1_500L)
            last = readLines(service)
            val idx = last.indexOfLast { it.trim().lowercase() == want }
            if (idx >= 0 && last.drop(idx + 1).any {
                    it.contains("Recommended Passives", ignoreCase = true) || it.contains("Look for stats", ignoreCase = true)
                }
            ) {
                delay(700L)
                return readLines(service).ifEmpty { last }
            }
        }
        return last
    }

    /** Chu cua cac nut dang hien tren man hinh, theo thu tu tren xuong duoi (khong cat bot, bo o nhap va nut bam) */
    private fun readLines(service: AutoAccessibilityService): List<String> {
        val root = service.rootInActiveWindow ?: return emptyList()
        class Item(val top: Int, val left: Int, val text: String)

        val items = mutableListOf<Item>()

        fun walk(n: AccessibilityNodeInfo) {
            val cls = n.className?.toString().orEmpty()
            if (n.isVisibleToUser && !cls.endsWith("EditText") && !cls.endsWith("Button")) {
                val t = (n.text?.toString() ?: "").replace("\u200b", "").replace("\u2060", "").trim()
                if (t.isNotEmpty()) {
                    val r = Rect()
                    n.getBoundsInScreen(r)
                    items.add(Item(r.top, r.left, t))
                }
            }
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                walk(c)
            }
        }

        walk(root)
        val lines = items.sortedWith(compareBy({ it.top }, { it.left })).map { it.text }
        // bo cac dong lap lien tiep (Discord hay co 2 node cung chu)
        val out = mutableListOf<String>()
        for (l in lines) if (out.isEmpty() || out.last() != l) out.add(l)
        return out
    }
}
