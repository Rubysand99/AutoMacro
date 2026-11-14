package com.starclan.starhub.engine

import android.util.Log
import com.starclan.starhub.data.CleanupPhase
import com.starclan.starhub.data.CleanupStatus
import com.starclan.starhub.data.CleanupStatusText
import com.starclan.starhub.data.CommandGuard
import com.starclan.starhub.service.AutoAccessibilityService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.starclan.starhub.R
import com.starclan.starhub.Str

/**
 * Cong tac "Don kho tu dong" o Hub (mac dinh TAT, tu tat khi dong Hub / khi co loi / khi xong). Chi khi BAT thi app moi lam cac lenh
 * `clean_*` cua bot; TAT thi de nguyen cho (het han tu huy).
 * `status` la thu Hub hien cho Ruby: dang cho gi, dang lam buoc nao, xong chua, loi o dau (xem CleanupStatusText).
 */
object CleanupGate {
    @Volatile
    var enabled: Boolean = false

    /** Vua bat cong tac: viec DAU TIEN la go `nw inv export` (Tom nhan file roi giao cac buoc don kho). */
    @Volatile
    var exportPending: Boolean = false

    /** Sau khi go export, doi toi luc nay (ms) de Tom kip nhan file va doi ke hoach: truoc do khong lam buoc don kho cu nao. */
    @Volatile
    var holdUntilMs: Long = 0L

    /** Luc app go `nw inv export` (ms): de biet ke hoach bot lap sau do co phai MOI khong. */
    @Volatile
    var exportSentMs: Long = 0L

    /** Ly do buoc vua roi that bai (CleanupRunner ghi, service doc de hien dong loi). */
    @Volatile
    var lastFailure: String = ""

    /** Tong so cay da ra theo nhom ("low" = common+uncommon, "high" = epic tro len) trong phien nay: buoc co `cum_cap` khong duoc vuot tong. */
    val tally = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /** So tin "could not find a weapon" tren man hinh TRUOC khi go lenh cua buoc nay (service ghi): tang len = OwO vua bao khong tim thay cay. */
    @Volatile
    var notFoundBefore: Int = 0

    /** Cay bi bo qua vi OwO bao khong tim thay (da ban/ra hoac sai ID): bao o cuoi phien, doi chieu bang export. */
    val notFound = java.util.concurrent.CopyOnWriteArrayList<String>()

    const val HOLD_AFTER_EXPORT_MS = 45_000L

    private val _status = MutableStateFlow(CleanupStatus())
    val status: StateFlow<CleanupStatus> = _status

    /** Dat trang thai moi (atMs = bay gio neu chua co). */
    fun set(s: CleanupStatus) {
        _status.value = if (s.atMs > 0L) s else s.copy(atMs = System.currentTimeMillis())
    }

    /** Doi dong chi tiet cua buoc dang lam (chi khi dang RUNNING). */
    fun progress(detail: String) {
        val cur = _status.value
        if (cur.phase == CleanupPhase.RUNNING) _status.value = cur.copy(detail = detail)
    }

    /** Ket thuc phien (xong / khong co ke hoach / loi): giu dong ket qua de Ruby doc, nhung tat cong tac. */
    fun finish(s: CleanupStatus) {
        set(s)
        enabled = false
        exportPending = false
    }

    fun turnOn() {
        enabled = true; exportPending = true; holdUntilMs = 0L; lastFailure = ""; tally.clear()
        set(CleanupStatus(CleanupPhase.EXPORTING, detail = Str.get(R.string.clean_cho_luot_kiem_tra_ke)))
    }

    fun turnOff() {
        enabled = false; exportPending = false
        val cur = _status.value
        if (cur.finished) return   // dong ket qua (xong / loi) khong bi xoa khi tat cong tac
        val note = if (cur.busy) Str.get(R.string.clean_da_tat_phien_cua_tom) else ""
        set(CleanupStatus(CleanupPhase.OFF, detail = note))
    }
}

/**
 * Lam MOT buoc don kho SAU KHI lenh da duoc go vao Discord (service goi sau DiscordSender.send):
 *  - star: bam nut sao (nhan "star_outline_dim") tren embed `owo w <ID>`; da sao (star_yellow) thi coi nhu xong.
 *  - tier / id: doi tin xac nhan cua OwO; doc chu tren man hinh, CHI bam "Dismantle" khi CommandGuard.confirmMatches dung
 *    (so cay khop so bot tinh / dung ID). Lech hoac khong doc duoc -> bam "Cancel" va bao that bai (bot dung ca phien).
 * Moi giai doan ghi CleanupGate.progress de Ruby thay app dang lam gi; that bai ghi CleanupGate.lastFailure (ly do cu the).
 */
object CleanupRunner {
    private const val TAG = "CleanupRunner"

    suspend fun run(service: AutoAccessibilityService, mode: String, params: Map<String, String>): Boolean {
        CleanupGate.lastFailure = ""
        if (params["step_no"] == "1") { CleanupGate.tally.clear(); CleanupGate.notFound.clear() }   // phien moi bat dau tu buoc 1
        CleanupGate.progress(Str.get(R.string.clean_da_go_lenh_cho_owo))
        delay(3_500)   // doi OwO tra loi
        return when (mode) {
            "star" -> star(service, params)
            "tier", "id" -> dismantle(service, mode, params)
            "export" -> {
                CleanupGate.progress(Str.get(R.string.clean_da_go_nw_inv_export))
                true   // chi go `nw inv export`; Neon tra file thi Tom tu nhan
            }
            else -> fail(Str.get(R.string.clean_buoc_khong_ro_mode, mode))
        }
    }

    /** Ghi ly do that bai va tra false (de dung `return fail(...)`). */
    private fun fail(reason: String): Boolean {
        CleanupGate.lastFailure = reason
        return false
    }

    /** Tin co nut sao `node` o duoi cung la cua DUNG cay `id`: ma cay hien tren tin, hoac tin nam duoi tin lenh ta vua go. */
    private fun isThisWeapon(service: AutoAccessibilityService, node: android.view.accessibility.AccessibilityNodeInfo, id: String, params: Map<String, String>): Boolean =
        CommandGuard.textHasId(ButtonClicker.textAboveNode(service, node), id) ||
            ButtonClicker.starIsBelowCommand(service, node, params["command_text"].orEmpty().trim())

    /**
     * Sao 1 cay GIU. Man hinh con tin cua cay TRUOC (da sao), nen chi lam khi tin hien o duoi cung co DUNG ma cay nay;
     * bam xong phai thay nut sao chuyen vang. Khong chac -> that bai (khong bao gio coi la xong khi chua chac cay da sao,
     * vi cay Giu chua sao se bi rã theo bac).
     */
    private suspend fun star(service: AutoAccessibilityService, params: Map<String, String>): Boolean {
        val id = params["command_text"].orEmpty().trim().split(Regex("\\s+")).lastOrNull().orEmpty()
        CleanupGate.progress(Str.get(R.string.clean_tim_nut_sao_tren_tin))
        var node: android.view.accessibility.AccessibilityNodeInfo? = null
        var waited = 0
        while (true) {
            // OwO bao khong tim thay cay nay (da ban/ra hoac sai ID): bo qua, khong lam dung ca phien (cay khong ton tai thi khong co gi de giu)
            if (ButtonClicker.countTextContaining(service, "could not find a weapon") > CleanupGate.notFoundBefore) {
                CleanupGate.notFound.add(id)
                CleanupGate.progress(Str.get(R.string.clean_cay_khong_ton_tai, id))
                return true
            }
            val n = ButtonClicker.latestStarNode(service)
            if (n != null && isThisWeapon(service, n, id, params)) { node = n; break }
            if (waited >= 8_000) break
            delay(1_000); waited += 1_000
        }
        if (node == null) {
            val pr = ButtonClicker.probeStar(service, params["command_text"].orEmpty().trim(), id)
            fun yn(b: Boolean) = Str.get(if (b) R.string.svc_co else R.string.svc_khong)
            return fail(Str.get(R.string.clean_khong_thay_tin_cua_cay, id) + " " +
                Str.get(R.string.clean_chan_doan_sao, yn(pr.discordWindow), pr.starNodes, pr.latest, yn(pr.idInText), pr.textLen, yn(pr.commandFound), yn(pr.belowCommand)) + " [" + ButtonClicker.sampleTexts(service) + "]")
        }
        if (!ButtonClicker.isOutlineStar(node)) {   // nhan khac "star_outline_dim" (vd star_yellow) = da sao tu truoc
            CleanupGate.progress(Str.get(R.string.clean_cay_nay_da_co_sao))
            return true
        }
        val clickedTop = android.graphics.Rect().also { node.getBoundsInScreen(it) }.top
        if (!NodeTools.clickNode(service, node)) return fail(Str.get(R.string.clean_khong_bam_duoc_nut_sao))
        CleanupGate.progress(Str.get(R.string.clean_da_bam_sao))
        waited = 0
        var last = ""
        while (waited < 5_000) {
            delay(1_000); waited += 1_000
            val n = ButtonClicker.latestStarNode(service) ?: continue
            last = ButtonClicker.starLabelText(n)
            val top = android.graphics.Rect().also { n.getBoundsInScreen(it) }.top
            // Sau khi bam, embed ve lai nen khong con khop ma cay: kiem tra DUNG nut vua bam (cung vi tri) da doi nhan khoi "outline"
            if (!ButtonClicker.isOutlineStar(n) && (kotlin.math.abs(top - clickedTop) <= 200 || isThisWeapon(service, n, id, params))) {
                delay(500)
                return true
            }
        }
        return fail(Str.get(R.string.clean_sao_khong_len, id) + " " + Str.get(R.string.clean_sao_hien_la, last.ifEmpty { "-" }))
    }

    private suspend fun dismantle(service: AutoAccessibilityService, mode: String, params: Map<String, String>): Boolean {
        CleanupGate.progress(Str.get(R.string.clean_cho_tin_xac_nhan_cua))
        var waited = 0
        while (!ButtonClicker.has(service, "dismantle") && waited < 6_000) { delay(1_000); waited += 1_000 }
        if (!ButtonClicker.has(service, "dismantle")) {
            // Khong co gi de ra (bac trong, cay da het) -> bo qua neu bot danh dau optional
            if (params["optional"] == "1") {
                CleanupGate.progress(Str.get(R.string.clean_khong_co_cay_nao_de))
                return true
            }
            return fail(Str.get(R.string.clean_khong_thay_nut_dismantle_sau))
        }
        val text = ButtonClicker.textAbove(service, "dismantle")
        val count = CommandGuard.parseWeaponCount(text)
        CleanupGate.progress(if (count != null) Str.get(R.string.clean_owo_bao_cay_dang_doi, count) else Str.get(R.string.clean_dang_doc_tin_xac_nhan))
        val expect = params["expect"]?.toIntOrNull()
        val expectMin = params["expect_min"]?.toIntOrNull()
        var expectMax = params["expect_max"]?.toIntOrNull()
        val expectId = params["expect_id"]
        // Nhom cong don (vd common + uncommon): tong cay da ra trong nhom + lan nay khong duoc vuot cum_cap
        val cumGroup = params["cum_group"]
        val cumCap = params["cum_cap"]?.toIntOrNull()
        if (cumGroup != null && cumCap != null) {
            val left = cumCap - (CleanupGate.tally[cumGroup] ?: 0)
            expectMax = minOf(expectMax ?: left, left)
        }
        if (!CommandGuard.confirmMatches(mode, text, expect, expectMax, expectId, expectMin)) {
            Log.w(TAG, "Tin xac nhan khong khop (mode=$mode) - bam Cancel")
            CleanupGate.progress(Str.get(R.string.clean_khong_khop_ke_hoach_bam))
            ButtonClicker.clickOnce(service, "cancel")
            return fail(CleanupStatusText.mismatchReason(mode, count, expect, expectMax, expectId, expectMin))
        }
        CleanupGate.progress(Str.get(R.string.clean_khop_ke_hoach_bam_dismantle))
        if (!ButtonClicker.clickOnce(service, "dismantle")) return fail(Str.get(R.string.clean_khong_bam_duoc_nut_dismantle))
        if (cumGroup != null && count != null) CleanupGate.tally[cumGroup] = (CleanupGate.tally[cumGroup] ?: 0) + count
        delay(2_500)
        return true
    }
}
