package com.starclan.starhub.engine

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.starclan.starhub.service.AutoAccessibilityService
import com.starclan.starhub.view.FarmOverlay
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Bam di bam lai 1 nut co nhan co dinh (vd "forward" o danh sach `owo weapon` nhieu trang), moi lan cach nhau
 * vai giay, den khi nut het sang (trang cuoi) hoac het so lan. Khop NHAN CHINH XAC: "forward" khong khop
 * "fast_forward" (nut nhay toi trang cuoi). Chon nut nam THAP NHAT tren man hinh = danh sach moi nhat.
 */
object ButtonClicker {
    private const val TAG = "ButtonClicker"
    private val NON_TEXT = Regex("[^\\p{L}\\p{N} ]")
    private val SPACES = Regex("\\s+")

    private fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return SPACES.replace(NON_TEXT.replace(raw.lowercase(), " "), " ").trim()
    }

    private fun exactLabel(node: AccessibilityNodeInfo, target: String): Boolean {
        for (c in listOf(node.text?.toString(), node.contentDescription?.toString())) {
            var s = normalize(c)
            if (s.endsWith(" button")) s = s.removeSuffix(" button").trim()
            if (s == target) return true
        }
        return false
    }

    private fun effectivelyEnabled(node: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = node
        while (cur != null) {
            if (!cur.isEnabled) return false
            if (cur.isClickable) return true
            cur = cur.parent
        }
        return true
    }

    private fun findBottomMost(service: AutoAccessibilityService, target: String): AccessibilityNodeInfo? {
        val root = service.discordRoot() ?: return null
        // Chi bam trong Discord: nguoi dung chuyen sang app khac giua chung thi DUNG, khong bam nham nut app do
        if (root.packageName?.toString() != DiscordSender.DISCORD_PACKAGE) return null
        val nodes = NodeTools.collect(root, maxNodes = 1500) { NodeTools.onScreen(it) && exactLabel(it, target) }
        val clickable = nodes.filter { it.isClickable }
        val pool = if (clickable.isNotEmpty()) clickable else nodes
        return pool.maxByOrNull { n ->
            val r = Rect()
            n.getBoundsInScreen(r)
            r.top
        }
    }

    /** So lan da bam. Dung khi khong thay nut / nut het sang / loi bam. */
    suspend fun clickRepeatedly(
        service: AutoAccessibilityService,
        label: String,
        times: Int,
        minDelayMs: Long,
        maxDelayMs: Long,
        initialWaitMs: Long = 3_000L
    ): Int {
        val target = normalize(label)
        var clicked = 0
        delay(initialWaitMs) // doi OwO (hoac bot khac) tra loi truoc khi bam
        for (i in 1..times) {
            if (!FarmOverlay.isShown()) {
                Log.i(TAG, "Hub noi da tat - dung bam sau $clicked lan")
                break
            }
            val node = findBottomMost(service, target)
            if (node == null) {
                Log.i(TAG, "Khong thay nut '$target' (hoac khong con o Discord) - dung sau $clicked lan")
                break
            }
            if (!effectivelyEnabled(node)) {
                Log.i(TAG, "Nut '$target' het sang (trang cuoi) sau $clicked lan")
                break
            }
            if (!NodeTools.clickNode(service, node)) {
                Log.w(TAG, "Bam nut '$target' that bai sau $clicked lan")
                break
            }
            clicked++
            delay(Random.nextLong(minDelayMs, maxOf(minDelayMs + 1, maxDelayMs)))
        }
        return clicked
    }

    // ----------------- Cho CleanupRunner: bam 1 lan, doc chu tren man hinh -----------------

    private fun enabledBottomMost(service: AutoAccessibilityService, label: String): AccessibilityNodeInfo? {
        val n = findBottomMost(service, normalize(label)) ?: return null
        return if (effectivelyEnabled(n)) n else null
    }

    /** Co nut (dang sang) co nhan `label` o Discord khong. */
    fun has(service: AutoAccessibilityService, label: String): Boolean = enabledBottomMost(service, label) != null

    /** Bam 1 lan nut thap nhat co nhan `label`. false neu khong thay / het sang / bam loi. */
    suspend fun clickOnce(service: AutoAccessibilityService, label: String): Boolean {
        val n = enabledBottomMost(service, label) ?: return false
        return NodeTools.clickNode(service, n)
    }

    /** Chu cua cac o nam ngay TREN nut `label` (toi da ~900px) = noi dung tin xac nhan cua OwO. null neu khong thay nut. */
    fun textAbove(service: AutoAccessibilityService, label: String, requireEnabled: Boolean = true): String? {
        val btn = (if (requireEnabled) enabledBottomMost(service, label) else findBottomMost(service, normalize(label))) ?: return null
        return textAboveNode(service, btn)
    }

    fun textAboveNode(service: AutoAccessibilityService, btn: AccessibilityNodeInfo): String? {
        val br = Rect(); btn.getBoundsInScreen(br)
        val root = service.discordRoot() ?: return null
        val nodes = NodeTools.collect(root, maxNodes = 1500) {
            val t = (it.text?.toString()?.takeIf { s -> s.isNotBlank() } ?: it.contentDescription?.toString())
            if (t.isNullOrBlank() || !NodeTools.onScreen(it)) false else {
                val r = Rect(); it.getBoundsInScreen(r)
                r.bottom <= br.top + 8 && r.top >= br.top - 900
            }
        }
        return nodes.joinToString("\n") { (it.text?.toString()?.takeIf { s -> s.isNotBlank() } ?: it.contentDescription?.toString()).orEmpty() }
    }

    private fun labelOf(n: AccessibilityNodeInfo): String =
        normalize(n.contentDescription?.toString()?.takeIf { it.isNotBlank() } ?: n.text?.toString().orEmpty())

    /** Nhan nut sao: "star_outline_dim" = CHUA sao; nhan khac bat dau bang "star" (vd star_yellow) = DA sao. */
    private fun isStarLabel(n: AccessibilityNodeInfo): Boolean {
        val l = labelOf(n)
        return l == "star" || l.startsWith("star ")
    }

    fun isOutlineStar(n: AccessibilityNodeInfo): Boolean = labelOf(n) == normalize("star_outline_dim")

    fun starLabelText(n: AccessibilityNodeInfo?): String = n?.let { labelOf(it) }.orEmpty()

    /** Nut sao cua tin MOI NHAT (nut sao thap nhat tren man hinh Discord); null = khong thay. */
    fun latestStarNode(service: AutoAccessibilityService): AccessibilityNodeInfo? {
        val root = service.discordRoot() ?: return null
        val nodes = NodeTools.collect(root, maxNodes = 1500) { NodeTools.onScreen(it) && isStarLabel(it) }
        val clickable = nodes.filter { it.isClickable }
        return (if (clickable.isNotEmpty()) clickable else nodes).maxByOrNull { n -> val r = Rect(); n.getBoundsInScreen(r); r.top }
    }

    /**
     * Nut sao `btn` co nam DUOI tin lenh ta vua go (vd "owo w AL57PC") khong? Co = day la tin tra loi cua OwO cho DUNG lenh nay
     * (tin cua cay truoc nam o TREN tin lenh). Khong can ma cay hien trong embed.
     */
    fun starIsBelowCommand(service: AutoAccessibilityService, btn: AccessibilityNodeInfo, commandText: String): Boolean {
        val br = Rect(); btn.getBoundsInScreen(br)
        val root = service.discordRoot() ?: return false
        val want = normalize(commandText)
        if (want.isEmpty()) return false
        var cmdBottom = Int.MIN_VALUE
        var cmdTop = Int.MIN_VALUE
        NodeTools.collect(root, maxNodes = 1500) {
            val t = (it.text?.toString()?.takeIf { s -> s.isNotBlank() } ?: it.contentDescription?.toString())
            !t.isNullOrBlank() && NodeTools.onScreen(it) && !it.isEditable && normalize(t).contains(want)
        }.forEach {
            val r = Rect(); it.getBoundsInScreen(r)
            if (r.top > cmdTop) { cmdTop = r.top; cmdBottom = r.bottom }
        }
        return cmdBottom != Int.MIN_VALUE && br.top >= cmdBottom
    }

    /** Anh chup de chan doan khi buoc sao khong nhan ra tin cua cay (hien o dong loi cua Hub). */
    data class StarProbe(
        val discordWindow: Boolean, val starNodes: Int, val latest: String,
        val idInText: Boolean, val textLen: Int, val commandFound: Boolean, val belowCommand: Boolean
    )

    /** Vai doan chu (text hoac mo ta) thap nhat tren man hinh Discord: de chan doan khi khong thay tin lenh. */
    fun sampleTexts(service: AutoAccessibilityService): String {
        val root = service.discordRoot() ?: return "-"
        val items = NodeTools.collect(root, maxNodes = 1500) {
            NodeTools.onScreen(it) && !it.isEditable && !(it.text?.toString() ?: it.contentDescription?.toString()).isNullOrBlank()
        }.map { n -> val r = Rect(); n.getBoundsInScreen(r); r.top to (n.text?.toString()?.takeIf { it.isNotBlank() } ?: n.contentDescription.toString()) }
        return items.sortedByDescending { it.first }.take(4).joinToString(" | ") { it.second.replace("\n", " ").take(40) }
    }

    fun probeStar(service: AutoAccessibilityService, commandText: String, id: String): StarProbe {
        val root = service.discordRoot()
        val stars = NodeTools.collect(root, maxNodes = 1500) { isStarLabel(it) }.size
        val node = if (root != null) latestStarNode(service) else null
        val text = node?.let { textAboveNode(service, it) }
        val want = normalize(commandText)
        val cmdFound = root != null && want.isNotEmpty() && NodeTools.collect(root, maxNodes = 1500) {
            val t = (it.text?.toString()?.takeIf { s -> s.isNotBlank() } ?: it.contentDescription?.toString())
            !t.isNullOrBlank() && !it.isEditable && normalize(t).contains(want)
        }.isNotEmpty()
        val below = node != null && starIsBelowCommand(service, node, commandText)
        return StarProbe(root != null, stars, starLabelText(node).ifEmpty { "-" },
            com.starclan.starhub.data.CommandGuard.textHasId(text, id), text?.length ?: 0, cmdFound, below)
    }

    /** So node tren man hinh Discord co chu chua `needle` (khong phan biet hoa thuong). Dung de nhan ra tin MOI: so luong tang len. */
    fun countTextContaining(service: AutoAccessibilityService, needle: String): Int {
        val root = service.discordRoot() ?: return 0
        val want = normalize(needle)
        return NodeTools.collect(root, maxNodes = 1500) {
            val t = (it.text?.toString()?.takeIf { s -> s.isNotBlank() } ?: it.contentDescription?.toString())
            !t.isNullOrBlank() && !it.isEditable && normalize(t).contains(want)
        }.size
    }
}
