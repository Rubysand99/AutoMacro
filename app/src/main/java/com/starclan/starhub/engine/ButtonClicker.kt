package com.starclan.starhub.engine

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.starclan.starhub.service.AutoAccessibilityService
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
        val root = service.rootInActiveWindow ?: return null
        val nodes = NodeTools.collect(root, maxNodes = 1500) { it.isVisibleToUser && exactLabel(it, target) }
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
        maxDelayMs: Long
    ): Int {
        val target = normalize(label)
        var clicked = 0
        delay(3_000L) // doi OwO ve danh sach trang 1
        for (i in 1..times) {
            val node = findBottomMost(service, target)
            if (node == null) {
                Log.i(TAG, "Khong thay nut '$target' - dung sau $clicked lan")
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
}
