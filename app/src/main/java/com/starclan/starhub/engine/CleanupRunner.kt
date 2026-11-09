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
        enabled = true; exportPending = true; holdUntilMs = 0L; lastFailure = ""
        set(CleanupStatus(CleanupPhase.EXPORTING, detail = "Chờ lượt kiểm tra kế tiếp (tối đa 15 giây) rồi app gõ nw inv export…"))
    }

    fun turnOff() {
        enabled = false; exportPending = false
        val cur = _status.value
        if (cur.finished) return   // dong ket qua (xong / loi) khong bi xoa khi tat cong tac
        val note = if (cur.busy) "Đã tắt. Phiên của Tom có thể còn dang dở — gõ ,cleanstop trong Discord nếu muốn hủy hẳn." else ""
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
        CleanupGate.progress("Đã gõ lệnh. Chờ OwO trả lời…")
        delay(3_500)   // doi OwO tra loi
        return when (mode) {
            "star" -> star(service)
            "tier", "id" -> dismantle(service, mode, params)
            "export" -> {
                CleanupGate.progress("Đã gõ nw inv export — chờ NeonUtil gửi file")
                true   // chi go `nw inv export`; Neon tra file thi Tom tu nhan
            }
            else -> fail("bước không rõ (mode=$mode)")
        }
    }

    /** Ghi ly do that bai va tra false (de dung `return fail(...)`). */
    private fun fail(reason: String): Boolean {
        CleanupGate.lastFailure = reason
        return false
    }

    private suspend fun star(service: AutoAccessibilityService): Boolean {
        CleanupGate.progress("Tìm nút sao trên tin của OwO…")
        if (ButtonClicker.has(service, "star_outline_dim")) {
            if (!ButtonClicker.clickOnce(service, "star_outline_dim")) return fail("không bấm được nút sao")
            CleanupGate.progress("Đã bấm sao")
            delay(1_500)
            return true
        }
        // Khong co nut sao chua danh dau: da sao san thi OK, khong thay gi ca thi that bai
        if (ButtonClicker.has(service, "star_yellow")) {
            CleanupGate.progress("Cây này đã có sao sẵn")
            return true
        }
        return fail("không thấy nút sao trên màn hình (OwO chưa trả lời, hoặc Discord không đang ở kênh chat)")
    }

    private suspend fun dismantle(service: AutoAccessibilityService, mode: String, params: Map<String, String>): Boolean {
        CleanupGate.progress("Chờ tin xác nhận của OwO…")
        var waited = 0
        while (!ButtonClicker.has(service, "dismantle") && waited < 6_000) { delay(1_000); waited += 1_000 }
        if (!ButtonClicker.has(service, "dismantle")) {
            // Khong co gi de ra (bac trong, cay da het) -> bo qua neu bot danh dau optional
            if (params["optional"] == "1") {
                CleanupGate.progress("Không có cây nào để rã ở mục này — bỏ qua")
                return true
            }
            return fail("không thấy nút Dismantle sau 6 giây (OwO không trả lời, hoặc không có cây nào để rã)")
        }
        val text = ButtonClicker.textAbove(service, "dismantle")
        val count = CommandGuard.parseWeaponCount(text)
        CleanupGate.progress(if (count != null) "OwO báo $count cây — đang đối chiếu kế hoạch…" else "Đang đọc tin xác nhận của OwO…")
        val expect = params["expect"]?.toIntOrNull()
        val expectMax = params["expect_max"]?.toIntOrNull()
        val expectId = params["expect_id"]
        if (!CommandGuard.confirmMatches(mode, text, expect, expectMax, expectId)) {
            Log.w(TAG, "Tin xac nhan khong khop (mode=$mode) - bam Cancel")
            CleanupGate.progress("Không khớp kế hoạch → bấm Cancel")
            ButtonClicker.clickOnce(service, "cancel")
            return fail(CleanupStatusText.mismatchReason(mode, count, expect, expectMax, expectId))
        }
        CleanupGate.progress("Khớp kế hoạch → bấm Dismantle")
        if (!ButtonClicker.clickOnce(service, "dismantle")) return fail("không bấm được nút Dismantle")
        delay(2_500)
        return true
    }
}
