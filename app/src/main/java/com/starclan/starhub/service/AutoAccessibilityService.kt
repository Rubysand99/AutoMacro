package com.starclan.starhub.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.PixelFormat
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.TextView
import com.starclan.starhub.R
import com.starclan.starhub.data.CommandGuard
import com.starclan.starhub.data.FarmCmd
import com.starclan.starhub.data.OwoTrackerApi
import com.starclan.starhub.data.OwoTrackerPrefs
import com.starclan.starhub.engine.ButtonClicker
import com.starclan.starhub.engine.DiscordSender
import com.starclan.starhub.engine.FarmController
import com.starclan.starhub.engine.HuntbotNotifier
import com.starclan.starhub.engine.SendResult
import com.starclan.starhub.view.FarmOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.random.Random

/**
 * Service nen chiu trach nhiem "nhin" man hinh Discord (rootInActiveWindow) va "thao tac" thay nguoi dung
 * (cham, go lenh) khi Hub noi dang bat. Cu 15 giay hoi owo-tracker co lenh moi thi go vao Discord,
 * nhac huntbot con 10 phut, va co wizard 1 lan de cai vi tri o nhap + nut Gui du phong.
 *
 * Chi nhan su kien cua Discord (accessibility_service_config.xml: packageNames="com.discord").
 * (Che do ghi kich ban + chay kich ban da duoc go bo o v24.)
 */
class AutoAccessibilityService : AccessibilityService() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    // --- Man hinh chon diem (dung cho wizard cai vi tri Discord) ---
    private var pickerView: View? = null

    // --- Dong bo voi owo-tracker: cu vai giay kiem tra lenh moi 1 lan ---
    private var syncJob: Job? = null

    // Khi OwO doi giai captcha: tam dung gui lenh toi moc nay (de Ruby tu giai), tranh gui don dap
    private var captchaPauseUntilMs = 0L

    companion object {
        private const val TAG = "AutoAccessibilityService"

        /** So lenh gan nhat nho lai de khong go lai khi ack that bai (xem handledCommands) */
        private const val MAX_HANDLED = 200

        /** Tham chieu toi service dang chay, de noi khac (VD: man hinh Cai dat) goi setupDiscordTargets() / applySyncSettings() */
        var instance: AutoAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility Service da ket noi")
        val saved = OwoTrackerPrefs.load(this)
        applySyncSettings(saved.enabled, saved.apiUrl, saved.token)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Khong phan ung theo su kien: app chu dong doc man hinh Discord (rootInActiveWindow) khi can.
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service bi ngat")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Service chet (nguoi dung tat quyen Accessibility, he thong don dep...) thi MOI hoat dong tu dong phai dung:
        // hide() dung farm + tu hoc tag + vong refresh va go Hub noi (neu khong, isShown() van true va farm go lenh vao hu khong)
        FarmOverlay.hide()
        hidePicker()
        instance = null
        serviceJob.cancel()
    }

    // ----------------- DONG BO VOI OWO-TRACKER -----------------

    /** Goi khi Ruby luu cai dat ket noi trong man hinh Cai dat - bat/tat vong lap kiem tra lenh. */
    fun applySyncSettings(enabled: Boolean, apiUrl: String, token: String) {
        syncJob?.cancel()
        syncJob = null
        if (!enabled || apiUrl.isBlank() || token.isBlank()) {
            Log.d(TAG, "Dong bo owo-tracker: TAT")
            return
        }
        Log.d(TAG, "Dong bo owo-tracker: BAT, cu 15 giay kiem tra 1 lan")
        syncJob = serviceScope.launch {
            var lastHuntbotCheckMs = 0L
            while (isActive) {
                try {
                    pollOwoTrackerOnce(apiUrl, token)
                } catch (e: Exception) {
                    Log.e(TAG, "Loi khi dong bo owo-tracker: ${e.message}")
                }
                // Nhac huntbot con ~10 phut: hoi bot moi phut, KE CA luc Hub noi dang tat / app khong farm
                val nowMs = System.currentTimeMillis()
                if (nowMs - lastHuntbotCheckMs >= 60_000L) {
                    lastHuntbotCheckMs = nowMs
                    try {
                        checkHuntbotAlert(apiUrl, token)
                    } catch (e: Exception) {
                        Log.w(TAG, "Loi khi kiem tra gio huntbot: ${e.message}")
                    }
                }
                delay(15_000)
            }
        }
    }

    /** Hoi bot gio huntbot ve; con <= 10 phut thi hien thong bao tren dien thoai (moi chuyen 1 lan). */
    private suspend fun checkHuntbotAlert(apiUrl: String, token: String) {
        val info = OwoTrackerApi.fetchAlerts(apiUrl, token, null) ?: return
        val sent = HuntbotNotifier.maybeNotify(
            this, info.huntbotHunting, info.huntbotSecondsLeft, info.huntbotReadyEpoch
        )
        if (sent) Log.i(TAG, "Da thong bao huntbot sap ve (con ${info.huntbotSecondsLeft}s)")
    }

    /**
     * Lenh da xu ly (kem chu ky action+command_text va ket qua). Neu ack that bai (mat mang) thi lan poll sau bot van
     * tra lai lenh do - khi do chi BAO LAI ket qua, KHONG go lai. Chu ky de phong bot reset bo dem id lam trung id.
     * Chi giu trong bo nho (mat khi service khoi dong lai), toi da MAX_HANDLED lenh gan nhat.
     */
    private data class HandledCommand(val signature: String, val status: String)
    private val handledCommands = LinkedHashMap<Int, HandledCommand>()

    private fun commandSignature(action: String, commandText: String?): String = "$action|${commandText.orEmpty()}"

    private suspend fun ackAndRemember(
        apiUrl: String, token: String, id: Int, signature: String, status: String
    ) {
        handledCommands[id] = HandledCommand(signature, status)
        while (handledCommands.size > MAX_HANDLED) {
            handledCommands.remove(handledCommands.keys.first())
        }
        if (!OwoTrackerApi.ack(apiUrl, token, id, status)) {
            Log.w(TAG, "Chua bao duoc ket qua lenh $id ($status) - se bao lai o lan poll sau")
        }
    }

    private suspend fun pollOwoTrackerOnce(apiUrl: String, token: String) {
        // App chi tu hanh dong khi Hub noi dang BAT. Hub tat -> khong dong vao lenh nao (lenh cua bot cu nam cho,
        // het han sau 10 phut)
        if (!FarmOverlay.isShown()) return
        // Dang tam dung vi captcha -> khong dong vao lenh nao, de chung nam cho
        if (System.currentTimeMillis() < captchaPauseUntilMs) return

        val commands = OwoTrackerApi.fetchPending(apiUrl, token)
        if (commands.isEmpty()) return

        val settings = OwoTrackerPrefs.load(this)
        if (!settings.hasDiscordTargets) {
            // Toa do chi la phuong an du phong (khi khong tim thay o nhap/nut Gui theo giao dien)
            Log.w(TAG, "Chua cai vi tri o nhap/nut Gui Discord - van thu tim theo giao dien")
        }

        val sender = DiscordSender(this)
        var first = true
        for (cmd in commands) {
            val commandText = cmd.params["command_text"]
            val signature = commandSignature(cmd.action, commandText)
            // Da xu ly roi (ack lan truoc bi loi mang) -> chi bao lai ket qua, khong go lai
            val handled = handledCommands[cmd.id]
            if (handled != null && handled.signature == signature) {
                Log.i(TAG, "Lenh '${cmd.action}' (${cmd.id}) da xu ly truoc do - chi bao lai ket qua ${handled.status}")
                OwoTrackerApi.ack(apiUrl, token, cmd.id, handled.status)
                continue
            }
            if (commandText.isNullOrBlank()) {
                Log.w(TAG, "Lenh '${cmd.action}' khong co command_text - bo qua")
                ackAndRemember(apiUrl, token, cmd.id, signature, "failed")
                continue
            }
            // Chi go dung cac lenh OwO ma bot that su gui (danh sach cho phep o CommandGuard). Con lai bi tu choi.
            if (!CommandGuard.isSafeCommand(commandText)) {
                Log.w(TAG, "Lenh '${cmd.action}' co command_text khong hop le - tu choi")
                ackAndRemember(apiUrl, token, cmd.id, signature, "failed")
                continue
            }

            // Lenh check bot nho go xen (auto_check): Ruby da TAT lenh do o Setting -> khong go, bao "failed" de bot biet
            if (cmd.action == "auto_check") {
                val gate = FarmCmd.forCheckCommand(commandText)
                if (gate != null && !FarmController.state.value.commands.isEnabled(gate)) {
                    Log.i(TAG, "Lenh check '${commandText.trim()}' dang TAT o Setting (Lenh trong farm) - khong go")
                    ackAndRemember(apiUrl, token, cmd.id, signature, "failed")
                    continue
                }
            }

            // Hub noi bi tat giua chung -> dung ngay, cac lenh con lai de nguyen (chua ack)
            if (!FarmOverlay.isShown()) {
                Log.i(TAG, "Hub noi da tat - dung xu ly lenh cua bot")
                break
            }
            // Lenh co "click_label" (vd quet danh sach vu khi nhieu trang): sau khi go lenh se BAM nut do nhieu lan.
            // Khong lam khi dang farm vi cac tin moi se day danh sach ra khoi man hinh.
            val clickLabel = cmd.params["click_label"]
            // Chi cho bam dung nhan trong danh sach cho phep (hien chi "forward"), khong bam nut tuy y theo server
            if (!clickLabel.isNullOrBlank() && !CommandGuard.isAllowedClickLabel(clickLabel)) {
                Log.w(TAG, "Lenh '${cmd.action}' co click_label khong duoc phep - tu choi")
                ackAndRemember(apiUrl, token, cmd.id, signature, "failed")
                continue
            }
            if (!clickLabel.isNullOrBlank() && FarmController.isRunning()) {
                Log.w(TAG, "Lenh '${cmd.action}' can man hinh yen tinh nhung dang farm - bo qua")
                ackAndRemember(apiUrl, token, cmd.id, signature, "failed")
                continue
            }

            var result = try {
                sender.send(commandText.trim(), settings, reopenChannel = first)
            } catch (e: Exception) {
                Log.e(TAG, "Loi khi chay lenh '${cmd.action}': ${e.message}")
                SendResult.SEND_FAILED
            }
            first = false
            if (result == SendResult.OK && !clickLabel.isNullOrBlank()) {
                val times = CommandGuard.clampClickTimes(cmd.params["click_times"]?.toIntOrNull())
                val (minMs, maxMs) = CommandGuard.clampClickGap(
                    cmd.params["click_min_ms"]?.toLongOrNull(), cmd.params["click_max_ms"]?.toLongOrNull()
                )
                val n = try {
                    ButtonClicker.clickRepeatedly(this, clickLabel, times, minMs, maxMs)
                } catch (e: Exception) {
                    Log.e(TAG, "Loi khi bam nut '$clickLabel': ${e.message}")
                    -1
                }
                Log.d(TAG, "Lenh '${cmd.action}': da bam '$clickLabel' $n lan")
                if (n < 0) result = SendResult.SEND_FAILED
            }
            Log.d(TAG, "Lenh '${cmd.action}' (${cmd.id}) -> $result")

            ackAndRemember(apiUrl, token, cmd.id, signature, if (result == SendResult.OK) "done" else "failed")

            if (result == SendResult.CAPTCHA) {
                alertCaptcha()
                captchaPauseUntilMs = System.currentTimeMillis() + 10 * 60 * 1000L
                break // dung ngay, cac lenh con lai de lan sau
            }
            // Nghi ngau nhien giua 2 lenh, khong gui lien tuc nhu may
            delay(Random.nextLong(2500, 5500))
        }
    }

    /** Rung + chuong de Ruby biet OwO dang doi giai captcha. */
    private fun alertCaptcha() {
        Log.w(TAG, "OwO dang yeu cau captcha - dung gui lenh tu dong 10 phut")
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.vibrate(VibrationEffect.createOneShot(1500, VibrationEffect.DEFAULT_AMPLITUDE))
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(this, uri)?.play()
        } catch (e: Exception) {
            Log.w(TAG, "Khong phat duoc chuong: ${e.message}")
        }
    }

    /** Wizard cai vi tri 1 lan: cham o nhap tin nhan, roi cham nut Gui, luu lai de dung cho moi lenh sau nay. */
    fun setupDiscordTargets(onComplete: (msgX: Float, msgY: Float, sendX: Float, sendY: Float) -> Unit) {
        startTwoPointPicking(
            getString(R.string.picker_prompt_message_box),
            getString(R.string.picker_prompt_send_button),
            onComplete
        )
    }

    // ----------------- CHON DIEM TREN MAN HINH (co dau cham xac nhan) -----------------

    /** Danh dau 2 diem lien tiep (dau roi cuoi), ca 2 cham cung hien tren 1 man hinh - dung cho wizard
     * cai vi tri Discord (setupDiscordTargets). */
    private fun startTwoPointPicking(
        prompt1: String,
        prompt2: String,
        onComplete: (x1: Float, y1: Float, x2: Float, y2: Float) -> Unit
    ) {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_point_picker, null)
        val promptView = view.findViewById<TextView>(R.id.tvPickerPrompt)
        val markerStart = view.findViewById<View>(R.id.pickerMarkerStart)
        val markerEnd = view.findViewById<View>(R.id.pickerMarkerEnd)
        promptView.text = prompt1

        val params = pickerWindowParams()
        var firstPoint: Pair<Float, Float>? = null

        view.setOnTouchListener { _, motionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                val x = motionEvent.rawX
                val y = motionEvent.rawY
                val first = firstPoint
                if (first == null) {
                    firstPoint = x to y
                    placeMarker(markerStart, x, y)
                    promptView.text = prompt2
                } else {
                    placeMarker(markerEnd, x, y)
                    view.postDelayed({
                        removePickerView(wm, view)
                        onComplete(first.first, first.second, x, y)
                    }, 400)
                }
            }
            true
        }

        wm.addView(view, params)
        pickerView = view
    }

    private fun pickerWindowParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
    }

    /** Dat 1 cham danh dau tai dung toa do (x, y) vua cham, de nguoi dung thay ro da cham dung cho */
    private fun placeMarker(marker: View, x: Float, y: Float) {
        val size = marker.layoutParams.width.takeIf { it > 0 }
            ?: (26 * resources.displayMetrics.density).toInt()
        val lp = marker.layoutParams as FrameLayout.LayoutParams
        lp.leftMargin = (x - size / 2).toInt()
        lp.topMargin = (y - size / 2).toInt()
        marker.layoutParams = lp
        marker.visibility = View.VISIBLE
    }

    private fun removePickerView(wm: WindowManager, view: View) {
        try {
            wm.removeView(view)
        } catch (e: Exception) {
            // Da bi go truoc do (VD: service bi ngat giua chung), bo qua
        }
        if (pickerView == view) pickerView = null
    }

    private fun hidePicker() {
        val view = pickerView ?: return
        removePickerView(getSystemService(WINDOW_SERVICE) as WindowManager, view)
    }

    // ----------------- CAC HAM MO PHONG THAO TAC (dung boi DiscordSender / NodeTools) -----------------

    /**
     * Cham 1 cu tai (x, y) va CHO den khi cu cham thuc su xong (hoac bi huy) roi moi tra ve.
     * Tra ve true neu he thong bao da cham xong. Dung cho cac buoc phai doi o nhap duoc chon
     * truoc khi go chu.
     */
    suspend fun performClickAwait(x: Float, y: Float, durationMs: Long = 50): Boolean =
        suspendCancellableCoroutine { cont ->
            val path = Path().apply { moveTo(x, y) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build()
            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            }
            val dispatched = dispatchGesture(gesture, callback, null)
            if (!dispatched && cont.isActive) cont.resume(false)
        }
}
