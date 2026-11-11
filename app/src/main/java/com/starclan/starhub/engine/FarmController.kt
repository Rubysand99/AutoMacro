package com.starclan.starhub.engine

import android.content.Context
import android.util.Log
import com.starclan.starhub.data.CommandGuard
import com.starclan.starhub.data.DayStats
import com.starclan.starhub.data.FarmCmd
import com.starclan.starhub.data.FarmCommandConfig
import com.starclan.starhub.data.FarmStatsStore
import com.starclan.starhub.data.HubFormat
import com.starclan.starhub.data.HubResponse
import com.starclan.starhub.data.HubResult
import com.starclan.starhub.data.HubWeaponReview
import com.starclan.starhub.data.WeaponReviewLists
import com.starclan.starhub.data.OwoTrackerApi
import com.starclan.starhub.data.OwoTrackerPrefs
import com.starclan.starhub.data.SpeedMode
import com.starclan.starhub.data.StatsBook
import com.starclan.starhub.service.AutoAccessibilityService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.Calendar
import kotlin.random.Random
import com.starclan.starhub.view.FarmOverlay

/** Trang thai farm de Hub noi hien thi */
data class FarmState(
    val running: Boolean = false,
    val status: String = "Đang dừng",
    val lastCommand: String = "",
    val hunts: Int = 0,
    val battles: Int = 0,
    val hub: HubResponse? = null,
    val hubFetchedAtMs: Long = 0L,
    val hubError: String? = null,
    val sessionStartIso: String? = null,
    val sessionNewPets: List<String> = emptyList(),
    /** Canh bao can Ruby de y (pet moi, can giai captcha...) - Hub noi tu mo rong khi co */
    val alert: String? = null,
    /** Dang reo chuong */
    val ringing: Boolean = false,
    /** Dang nghi (giai lao / nghi theo phien / gio yen lang) - khong go lenh */
    val resting: Boolean = false,
    /** Che do hunt: 1 = dung khi du lootbox hang ngay, 2 = dung khi thieu gem, 3 = khong dung */
    val huntMode: Int = 3,
    /** Toc do farm: Nhanh / Thuong / Cham (doi nhip go lenh, xem SpeedMode) */
    val speed: SpeedMode = SpeedMode.NORMAL,
    /** Bat / tat tung lenh farm (tab Setting cua Hub noi). Doc moi vong nen doi giua chung co hieu luc o vong ke tiep. */
    val commands: FarmCommandConfig = FarmCommandConfig.ALL_ON,
    /** Ly do hunt dang tam dung (null = dang hunt binh thuong) */
    val huntPaused: String? = null,
    /** Dang CHO (nghi, gian nhip, doi cooldown...): gio het cho (epoch ms, 0 = khong cho), tong thoi gian cho, ly do.
     *  Hub noi dem nguoc de Ruby biet app dang cho chu khong phai dung. */
    val waitEndMs: Long = 0L,
    val waitTotalMs: Long = 0L,
    val waitLabel: String = "",
    /** Gio het "thoi gian vang" giai captcha (10 phut ke tu luc OwO bao), epoch ms; 0 = khong co captcha */
    val captchaEndMs: Long = 0L
)

/**
 * Farm tu dong: App la "tay" go lenh, bot owo-tracker la "mat" doc kenh.
 *
 *  1. Lay link kenh duoc chi dinh tu bot (,setchannel) -> mo Discord vao DUNG kenh.
 *  2. Go `owo zoo`, doi bot doc xong -> biet pet nao da co (ke ca so luong 0).
 *  3. Lap: viec vat (daily, quest...) -> hunt / battle / `owo` (THU TU NGAU NHIEN moi vong) -> nghi ngau nhien.
 *  4. Treo lau: nghi giai lao, nghi theo phien (chay 1.5-2.5 gio nghi 15-45 phut), gioi han tong gio chay,
 *     gio yen lang (tuy chon), tu mo lai kenh khi go loi, tu mo lai khi OwO im lang.
 *  5. Su co -> dung + reo chuong + nho bot nhan tin TAG Ruby tren Discord (dien thoai/may khac cung bao).
 *  Lenh thay gem do bot tao: dich vu Accessibility lo (dung chung khoa DiscordSender.sendMutex).
 */
object FarmController {
    private const val TAG = "FarmController"

    // ================= Cau hinh mac dinh (muon doi thi sua o day) =================
    private const val HUNT_COMMAND = "owo hunt"
    private const val BATTLE_COMMAND = "owo battle"
    private const val OWO_COMMAND = "owo"          // lenh "owo" dung mot minh, go moi vong ngay sau battle
    private const val ZOO_COMMAND = "owo zoo"
    private const val DAILY_COMMAND = "owo daily"
    private const val QUEST_COMMAND = "owo quest"

    // Nhip go lenh
    private const val BURST_MAX = 3                   // toi da 3 lenh trong BURST_WINDOW_MS (OwO bao "Slow down" khi go qua day)
    private const val BURST_WINDOW_MS = 12_000L
    // 1 vong (hunt + battle). OwO bat cooldown 15 giay moi lenh, nen vong ngan nhat 16.5 giay la sat nhat co the.
    private const val TEAM_REFRESH_GAP_MS = 30 * 60_000L          // owo team: cap nhat doi hinh cho ,teamcheck cua bot
    private const val WEAPON_DETAIL_GAP_MS = 6 * 60 * 60_000L     // owo weapon <ID> cua pet trong doi: it thay doi
    /** ID vu khi lay tu JSON cua bot roi ghep vao lenh `owo weapon <ID>`: chi nhan dang ID that, khong tin tuyet doi vao server */
    private val WEAPON_ID_RE = Regex("^[A-Za-z0-9]{4,8}$")
    private const val CHORE_REQUEUE_MS = 2 * 60_000L     // lenh vua go thanh cong -> cho 2 phut moi quay lai danh sach random
    private const val FILLER_MIN_REMAINING_MS = 8_000L   // con it nhat chung nay trong vong thi moi chen them 1 lenh kiem tra

    // Nghi giai lao ngan: sau 45-80 vong nghi 1.5 - 4 phut

    // Nghi theo phien: chay 90-150 phut roi nghi 15-45 phut
    private const val SESSION_MIN_MS = 90 * 60_000L
    private const val SESSION_MAX_MS = 150 * 60_000L
    private const val BREAK_MIN_MS = 15 * 60_000L
    private const val BREAK_MAX_MS = 45 * 60_000L

    // Tong gio chay toi da cho 1 lan bam Bat dau (het thi dung han)
    private const val MAX_TOTAL_HOURS = 14

    // Gio yen lang (khong go lenh), gio dia phuong cua may. De -1 = tat.
    // Vi du muon nghi tu 2h den 6h sang: QUIET_START_HOUR = 2, QUIET_END_HOUR = 6
    private const val QUIET_START_HOUR = -1
    private const val QUIET_END_HOUR = -1

    // Viec vat
    private const val DAILY_GAP_MS = 30 * 60_000L          // da thay den gio nhan daily thi thu lai moi 30 phut
    private const val DAILY_UNKNOWN_GAP_MS = 6 * 3_600_000L // chua co du lieu daily: thu 1 lan roi cho 6 gio

    // Che do hunt (Ruby chon tren Hub noi, luu lai cho lan sau)
    private const val PREFS_NAME = "star_farm"
    private const val KEY_HUNT_MODE = "hunt_mode"
    private const val KEY_SPEED_MODE = "speed_mode"
    private const val KEY_CMD_DISABLED = "cmd_disabled"   // id cac lenh DANG TAT, cach nhau dau phay
    private val REQUIRED_GEM_SLOTS = setOf("1", "3", "4")   // 3 slot gem thuong ngay

    // Huntbot: app chi NHAN thanh qua (owo hb) khi da xong. App KHONG tu chay lai huntbot vi
    // lenh khoi dong can mat khau xac minh dang hinh (kieu captcha) -> Ruby tu go, app nhac qua Discord.
    private const val HUNTBOT_COLLECT_COMMAND = "owo hb"
    private const val HUNTBOT_GAP_MS = 30 * 60_000L
    private const val HUNTBOT_IDLE_NOTIFY_GAP_MS = 3 * 3_600_000L

    // Mo hop dinh ky: lootbox, weapon crate, bcrate (owo bwc all do Ruby cung cap; owo lb all / owo wc all la lenh chuan cua OwO)
    private val OPEN_BOXES_COMMANDS: List<String> = listOf("owo lb all", "owo wc all", "owo bwc all")

    // Hien te lay essence (Ruby chon owo sc all), roi nang cap huntbot bang het essence.
    private val SACRIFICE_COMMANDS: List<String> = listOf("owo sc all")
    // Nang cap huntbot tu dong theo huong dan cua Ruby (bot doc cap do tung chi so tu `owo hb`):
    //  1. cost (toi da cap 5) -> 2. efficiency va gain nang DEU nhau (toi da 215 / 200)
    //  -> 3. experience (200) -> 4. duration (235) -> 5. radar (999)
    private const val UPGRADE_ENABLED = true
    private val TRAIT_MAX = mapOf(
        "cost" to 5, "efficiency" to 215, "gain" to 200,
        "experience" to 200, "duration" to 235, "radar" to 999
    )

    // Phuc hoi / canh gac
    private const val MAX_FAILS_IN_ROW = 6        // go loi 6 lan lien tiep -> dung han
    private const val ZOO_WAIT_MS = 25_000L
    private const val BOT_POLL_MS = 4_000L        // hoi bot moi 4 giay
    private const val BOT_UNREACHABLE_STOP_MS = 120_000L  // mat ket noi bot > 2 phut -> dung (khong con ai canh captcha)
    private const val SILENT_RECOVER_SEC = 150L   // OwO khong tra loi 2.5 phut -> mo lai kenh
    private const val SILENT_STOP_SEC = 360L      // 6 phut -> dung han
    private const val CAPTCHA_GOLDEN_MS = 10 * 60_000L   // OwO cho giai captcha 10 phut ke tu luc bao
    private const val MIN_COUNTDOWN_MS = 2_000L          // cho ngan hon thi khong can hien dem nguoc
    private const val CAPTCHA_MESSAGE =
        "🚨 OwO đòi captcha — đã dừng farm! Giải xong gõ ,captcha clear rồi bấm Bắt đầu lại"

    // ===============================================================================

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var appCtx: Context? = null

    private val _state = MutableStateFlow(FarmState())
    val state: StateFlow<FarmState> = _state

    // Chia se giua vong chinh va nguoi canh gac
    @Volatile private var restingNow = false
    @Volatile private var lastSendOkAtMs = 0L
    @Volatile private var silenceBaselineMs = 0L
    @Volatile private var silentRecoverRequested = false
    private var unreachableSinceMs = 0L

    private val choreAt = mutableMapOf<String, Long>()
    @Volatile private var sendCounter = 0

    /** Lenh kiem tra nhe, chen ngau nhien vao luc cho cooldown hunt va battle (cung cap nhat du lieu cho bot) */
    private data class Filler(val key: String, val command: String, val cmd: FarmCmd)

    /** 1 viec trong danh sach random: key de tinh hang cho 2 phut, run = cach go */
    private class Chore(val key: String, val run: suspend () -> SendResult)

    private val FILLERS = listOf(
        Filler("inv", "owo inv", FarmCmd.INV),      // kho: de bot biet con gem nao de thay
        Filler("cash", "owo cash", FarmCmd.CASH),   // cowoncy
        Filler("ws", "owo ws", FarmCmd.WS),         // weapon shards
        Filler("zoo", "owo zoo", FarmCmd.ZOO)       // zoo: bao pet moi chinh xac
    )
    private var questUiWarned = false

    fun isRunning(): Boolean = job?.isActive == true

    private fun update(block: (FarmState) -> FarmState) {
        // update{} la nguyen tu: nhieu luong (farm, lam moi hub, canh bao) cung ghi khong de mat cap nhat cua nhau
        _state.update(block)
    }

    fun clearAlert() {
        update { it.copy(alert = null) }
    }

    /** Cho `ms` va hien dem nguoc tren Hub noi (cho ngan thi cho thang, khong hien). */
    private suspend fun waitWithCountdown(ms: Long, label: String) {
        if (ms < MIN_COUNTDOWN_MS) {
            if (ms > 0L) delay(ms)
            return
        }
        update { it.copy(waitEndMs = System.currentTimeMillis() + ms, waitTotalMs = ms, waitLabel = label) }
        try {
            delay(ms)
        } finally {
            update { it.copy(waitEndMs = 0L, waitTotalMs = 0L, waitLabel = "") }
        }
    }

    /** Hub noi BAT = bot ON, Hub noi TAT = bot OFF (bot khong giao viec cho app). Goi tu FarmOverlay.show/hide. */
    fun reportHubPower(context: Context, on: Boolean) {
        val app = context.applicationContext
        scope.launch {
            val s = OwoTrackerPrefs.load(app)
            if (s.apiUrl.isNotBlank() && s.token.isNotBlank()) {
                OwoTrackerApi.setPower(s.apiUrl, s.token, on)
            }
        }
    }

    /** Ruby da giai captcha va go ,captcha clear -> bo dong ho "thoi gian vang" tren Hub noi */
    private suspend fun clearCaptchaIfSolved(s: OwoTrackerPrefs.Settings) {
        val info = OwoTrackerApi.fetchAlerts(s.apiUrl, s.token, null) ?: return
        if (!info.captchaActive) {
            update { it.copy(captchaEndMs = 0L, alert = if (it.alert == CAPTCHA_MESSAGE) null else it.alert) }
        }
    }

    // ---------- Thong ke farm theo ngay (luu o may, xem data/FarmStats.kt) ----------
    private val statsLock = Any()
    private var statsBook: StatsBook? = null
    private var statsCtx: Context? = null

    /** Nap thong ke da luu (goi khi bat Hub noi / bat farm). Goi lai nhieu lan khong sao. */
    fun initStats(context: Context) {
        val app = context.applicationContext
        synchronized(statsLock) {
            statsCtx = app
            if (statsBook == null) statsBook = FarmStatsStore.load(app)
        }
    }

    /** Ghi nhan 1 su kien vao thong ke roi luu ngay (so lieu rat nho). Chua nap thong ke thi bo qua. */
    private fun recordStats(block: (StatsBook, Long) -> Unit) {
        synchronized(statsLock) {
            val book = statsBook ?: return
            block(book, System.currentTimeMillis())
            val ctx = statsCtx
            if (ctx != null && book.dirty) FarmStatsStore.save(ctx, book)
        }
    }

    /** Thong ke toi da [days] ngay gan nhat (moi nhat truoc) de Hub noi hien thi. Khong ghi gi ra may. */
    fun statsSnapshot(days: Int = 7): List<DayStats> = synchronized(statsLock) {
        statsBook?.recent(System.currentTimeMillis(), days).orEmpty()
    }

    fun clearStats() = recordStats { book, _ -> book.clear() }

    /** Bat Hub noi / bat farm moi: xoa danh sach "pet moi" cua phien truoc, tinh lai tu bay gio */
    fun resetSession() {
        update { it.copy(sessionStartIso = Instant.now().toString(), sessionNewPets = emptyList()) }
    }

    /** Tat chuong (nut "Tat chuong" tren Hub noi) */
    fun stopAlarm() {
        AlarmPlayer.stop()
        update { it.copy(ringing = false) }
    }

    private fun startAlarm(ctx: Context) {
        AlarmPlayer.onStopped = { update { it.copy(ringing = false) } }
        AlarmPlayer.start(ctx)
    }

    /** Bao bot: phien farm da ket thuc (bot thoi canh gac) */
    private fun signalFarmEnded() {
        val ctx = appCtx ?: return
        scope.launch {
            val s = OwoTrackerPrefs.load(ctx)
            if (s.apiUrl.isNotBlank() && s.token.isNotBlank()) {
                OwoTrackerApi.fetchAlerts(s.apiUrl, s.token, "0")
            }
        }
    }

    /** Nho bot nhan tin TAG Ruby tren Discord (toAlt = gui DM vao acc phu cua Ruby) */
    private fun notifyOwner(text: String, toAlt: Boolean = false) {
        val ctx = appCtx ?: return
        scope.launch {
            val s = OwoTrackerPrefs.load(ctx)
            if (s.apiUrl.isNotBlank() && s.token.isNotBlank()) {
                OwoTrackerApi.notify(s.apiUrl, s.token, text, toAlt)
            }
        }
    }

    /** OwO doi captcha: reo chuong + rung, dung farm. (Bot tu nhan tin tag Ruby, app khong can nhan them) */
    private fun handleCaptcha(ctx: Context, ageSeconds: Long = 0L) {
        // Dem 1 lan cho moi dot captcha (ham nay co the duoc goi lai khi dang cho giai)
        if (_state.value.captchaEndMs <= 0L) recordStats { b, now -> b.addCaptcha(now) }
        startAlarm(ctx)
        // Dem nguoc 10 phut "thoi gian vang" ke tu luc OwO bao (bot cho biet da bao cach day bao lau)
        val endMs = System.currentTimeMillis() + maxOf(0L, CAPTCHA_GOLDEN_MS - ageSeconds * 1000L)
        update {
            it.copy(
                running = false, resting = false, status = CAPTCHA_MESSAGE, alert = CAPTCHA_MESSAGE, ringing = true,
                captchaEndMs = endMs, waitEndMs = 0L, waitTotalMs = 0L, waitLabel = ""
            )
        }
        signalFarmEnded()
    }

    /** Su co nghiem trong: dung han + reo chuong + nho bot nhan tin tag Ruby */
    private fun hardStop(ctx: Context, message: String) {
        startAlarm(ctx)
        update { it.copy(running = false, resting = false, status = message, alert = message, ringing = true) }
        notifyOwner(message)
        signalFarmEnded()
        job?.cancel()
        job = null
    }

    /** Dung theo ke hoach (het gio chay toi da...): khong reo chuong, nhung van nhan tin cho Ruby */
    private fun plannedStop(message: String) {
        update { it.copy(running = false, resting = false, status = message, alert = message) }
        notifyOwner(message)
        signalFarmEnded()
        job = null
    }

    fun start(service: AutoAccessibilityService) {
        if (isRunning()) return
        if (!FarmOverlay.isShown()) {
            update { it.copy(alert = "Bật Hub nổi trước rồi mới bắt đầu farm được") }
            return
        }
        // Dang hoc tag thi dang goi lenh / doc man hinh Discord: chay chong len nhau se bam nham
        if (TagCollector.isRunning()) {
            update { it.copy(alert = "Đang học lời khuyên vũ khí — dừng lại rồi mới bắt đầu farm được") }
            return
        }
        appCtx = service.applicationContext
        initStats(service.applicationContext)
        loadHuntMode(service.applicationContext)
        stopAlarm()
        MusicPlayer.startIfEnabled(service.applicationContext)
        update { it.copy(captchaEndMs = 0L) }
        job = scope.launch {
            try {
                runFarm(service)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Farm loi: ${e.message}", e)
                hardStop(service.applicationContext, "Lỗi bất ngờ: ${(e.message ?: "không rõ").take(100)}")
            } finally {
                // Moi cach ket thuc (dung tay, captcha, loi, het gio) deu chot gio chay vao thong ke
                recordStats { b, now -> b.endSession(now) }
                MusicPlayer.stop()   // nhac nen dung cung luc voi farm (captcha thi de chuong bao thuc keu ro)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        stopAlarm()
        restingNow = false
        update { it.copy(running = false, resting = false, status = "Đã dừng", alert = null, captchaEndMs = 0L) }
        signalFarmEnded()
    }

    private fun parseInstant(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return try {
            Instant.parse(value)
        } catch (e: Exception) {
            null
        }
    }

    /** Lay du lieu Hub moi nhat tu bot, cap nhat trang thai va bao pet moi (neu dang farm). */
    /** Bat/tat che do Event (bot luu lai). Hub cap nhat ngay khi bot xac nhan; loi thi bao tren Hub noi. */
    suspend fun setEventMode(context: Context, enabled: Boolean) {
        val s = OwoTrackerPrefs.load(context)
        if (s.apiUrl.isBlank() || s.token.isBlank()) {
            update { it.copy(hubError = "Chưa nhập địa chỉ API / token ở Cài đặt") }
            return
        }
        val saved = OwoTrackerApi.setEventMode(s.apiUrl, s.token, enabled)
        if (saved == null) {
            update { it.copy(alert = "⚠️ Không đổi được chế độ Event (kiểm tra mạng / bot đã cập nhật chưa)") }
            return
        }
        update { st -> st.copy(hub = st.hub?.copy(eventMode = saved)) }
    }

    private val reviewBusy = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Hub noi bam Giu / Bo cho cay vu khi dang duyet: bao bot ghi lai, roi (neu KHONG dang farm) go `owo w <cay ke tiep>`.
     * CHI ghi danh sach - khong dismantle / ban gi. Bam lien tuc: bo qua lan bam trong luc dang xu ly lan truoc.
     */
    suspend fun reviewDecision(service: AutoAccessibilityService, decision: String) {
        if (!reviewBusy.compareAndSet(false, true)) return
        try {
            val s = OwoTrackerPrefs.load(service)
            if (s.apiUrl.isBlank() || s.token.isBlank()) return
            val res = OwoTrackerApi.postWeaponReview(s.apiUrl, s.token, decision)
            if (res == null) {
                update { it.copy(status = "Không ghi được quyết định Giữ/Bỏ (mất kết nối bot?) — bấm lại") }
                return
            }
            update { st ->
                val hub = st.hub
                val rv = hub?.weaponReview
                if (hub == null || rv == null) st
                else st.copy(hub = hub.copy(weaponReview = rv.copy(
                    currentId = res.nextId, remaining = res.remaining,
                    kept = (rv.kept ?: 0) + if (decision == "keep") 1 else 0,
                    dropped = (rv.dropped ?: 0) + if (decision == "drop") 1 else 0,
                    unknown = (rv.unknown ?: 0) + if (decision == "unknown") 1 else 0)))
            }
            val next = res.nextId ?: return
            if (isRunning() || TagCollector.isRunning()) {
                update { it.copy(status = "Đã ghi. Cây kế tiếp: $next — đang farm nên chưa tự gõ, hãy dừng farm") }
                return
            }
            val cmd = "owo w $next"
            if (!CommandGuard.isSafeCommand(cmd)) return
            val r = DiscordSender(service).send(cmd, s, reopenChannel = false)
            if (r != SendResult.OK) update { it.copy(status = "Không gõ được $cmd (${describeSend(r)})") }
        } finally {
            reviewBusy.set(false)
        }
    }

    /** Nut "Tu duyet": bot xep hang cac cay Chua ro, nho app go `owo w <ID>` tung cay va doc dong gon NeonUtil. Phai DUNG farm. */
    suspend fun reviewAutoNeon(context: Context) {
        if (isRunning() || TagCollector.isRunning()) {
            update { it.copy(status = "Dừng farm trước rồi bấm Tự duyệt (để tin Neon không bị lẫn)") }
            return
        }
        val s = OwoTrackerPrefs.load(context)
        if (s.apiUrl.isBlank() || s.token.isBlank()) return
        val err = OwoTrackerApi.startAutoNeonReview(s.apiUrl, s.token)
        update { it.copy(status = if (err == null) "Đang tự duyệt các cây Chưa rõ — bấm Dừng để ngừng" else "Không bật được tự duyệt: $err") }
    }

    /** Nut "Dung" tren Hub: bao bot dung duyet, an hang nut Giu/Bo/? ngay (khong cho lan lam moi Hub ke tiep). */
    suspend fun reviewStop(context: Context) {
        val s = OwoTrackerPrefs.load(context)
        if (s.apiUrl.isBlank() || s.token.isBlank()) return
        if (!OwoTrackerApi.stopWeaponReview(s.apiUrl, s.token)) {
            update { it.copy(status = "Không dừng được duyệt vũ khí (mất kết nối bot?) — bấm lại") }
            return
        }
        update { st ->
            val hub = st.hub
            val rv = hub?.weaponReview
            if (hub == null || rv == null) st
            else st.copy(hub = hub.copy(weaponReview = rv.copy(active = false, currentId = null)))
        }
    }

    /** Tai 3 danh sach ID vu khi cho tab Misc (null = loi mang / chua nhap API). */
    suspend fun loadReviewLists(context: Context): WeaponReviewLists? {
        val s = OwoTrackerPrefs.load(context)
        if (s.apiUrl.isBlank() || s.token.isBlank()) return null
        return OwoTrackerApi.fetchWeaponReviewLists(s.apiUrl, s.token)
    }

    /**
     * Ruby chạm 1 ID trong danh sach: bao bot dat cay do la cay dang duyet, roi (neu KHONG dang farm) go `owo w <ID>`
     * de xem cay; sau do dung 2 nut Giu / Bo (hoac ?) nhu binh thuong. CHI ghi danh sach - khong dismantle gi.
     */
    suspend fun reviewSelect(service: AutoAccessibilityService, id: String) {
        if (!reviewBusy.compareAndSet(false, true)) return
        try {
            val s = OwoTrackerPrefs.load(service)
            if (s.apiUrl.isBlank() || s.token.isBlank()) return
            if (!OwoTrackerApi.selectWeaponReview(s.apiUrl, s.token, id)) {
                update { it.copy(status = "Không chọn được cây $id (bot đang quét kho / tự duyệt, hoặc mất kết nối)") }
                return
            }
            update { st ->
                val hub = st.hub
                if (hub == null) st
                else st.copy(hub = hub.copy(weaponReview = (hub.weaponReview ?: HubWeaponReview()).copy(active = true, currentId = id)))
            }
            if (isRunning() || TagCollector.isRunning()) {
                update { it.copy(status = "Đã chọn cây $id — đang farm nên chưa tự gõ, hãy dừng farm") }
                return
            }
            val cmd = "owo w $id"
            if (!CommandGuard.isSafeCommand(cmd)) return
            val r = DiscordSender(service).send(cmd, s, reopenChannel = false)
            if (r != SendResult.OK) update { it.copy(status = "Không gõ được $cmd (${describeSend(r)})") }
        } finally {
            reviewBusy.set(false)
        }
    }

    suspend fun refreshHub(context: Context) {
        val s = OwoTrackerPrefs.load(context)
        if (s.apiUrl.isBlank() || s.token.isBlank()) {
            update { it.copy(hubError = "Chưa nhập địa chỉ API / token ở Cài đặt") }
            return
        }
        if (_state.value.captchaEndMs > 0L && !isRunning()) clearCaptchaIfSolved(s)
        when (val r = OwoTrackerApi.fetchHub(s.apiUrl, s.token)) {
            is HubResult.Success -> {
                val hub = r.hub
                val cur = _state.value
                val startAt = parseInstant(cur.sessionStartIso)
                var pets = cur.sessionNewPets
                if (startAt != null) {
                    pets = hub.newPets?.pets.orEmpty()
                        .filter { p ->
                            val t = parseInstant(p.firstFoundAt)
                            t != null && !t.isBefore(startAt)
                        }
                        .mapNotNull { p -> p.name }
                }
                val added = pets.filter { name -> !cur.sessionNewPets.contains(name) }
                val newAlert = if (added.isNotEmpty()) "🆕 Pet mới: " + added.joinToString(", ") else cur.alert
                update {
                    it.copy(
                        hub = hub,
                        hubFetchedAtMs = System.currentTimeMillis(),
                        hubError = null,
                        sessionNewPets = pets,
                        alert = newAlert
                    )
                }
                // Thong ke: pet moi, so lan Slow down (cao nhat), cowoncy bot vua doc
                recordStats { b, now ->
                    b.addNewPets(now, added.size)
                    hub.slowdown?.count30m?.let { n -> b.observeSlowdown(now, n) }
                    hub.cowoncy?.amount?.let { amount -> b.observeCash(now, amount, hub.cowoncy?.ageSeconds) }
                }
            }
            is HubResult.Failure -> update { it.copy(hubError = r.message) }
        }
    }

    private fun describeOpen(r: OpenResult): String = when (r) {
        OpenResult.OK_CHANNEL -> "đã vào kênh"
        OpenResult.OK_APP_ONLY -> "chỉ mở được app Discord, chưa vào đúng kênh"
        OpenResult.OK_LINK_FAILED -> "link kênh bị lỗi"
        OpenResult.NOT_INSTALLED -> "không thấy app Discord"
        OpenResult.LAUNCH_ERROR -> "Android không cho mở Discord"
        OpenResult.NOT_FOREGROUND -> "Discord không lên màn hình (kiểm tra đã mở khóa màn hình chưa)"
    }

    private fun describeSend(r: SendResult): String = when (r) {
        SendResult.OK -> "ok"
        SendResult.DISCORD_NOT_OPEN -> "không mở được Discord"
        SendResult.NO_INPUT -> "không thấy ô nhập tin nhắn"
        SendResult.TYPE_FAILED -> "không gõ được chữ vào ô nhập"
        SendResult.SEND_FAILED -> "không bấm gửi được"
        SendResult.CAPTCHA -> "OwO đang đòi captcha"
    }

    private val recentSends = ArrayDeque<Long>()

    /** Cho den khi gui them 1 lenh khong vuot qua BURST_MAX lenh / BURST_WINDOW_MS, roi ghi nhan lan gui nay */
    private suspend fun awaitBurstSlot() {
        while (true) {
            val now = System.currentTimeMillis()
            while (recentSends.isNotEmpty() && now - recentSends.first() > BURST_WINDOW_MS) recentSends.removeFirst()
            if (recentSends.size < BURST_MAX) break
            val wait = BURST_WINDOW_MS - (now - recentSends.first()) + Random.nextLong(200L, 900L)
            update { it.copy(status = "Giãn nhịp gõ lệnh (tránh OwO báo Slow down)…") }
            waitWithCountdown(wait.coerceAtLeast(300L), "Giãn nhịp gõ lệnh")
        }
        recentSends.addLast(System.currentTimeMillis())
    }

    /** Lan gan nhat go thanh cong hunt / battle: thu tu moi vong la ngau nhien nen hai lan cung lenh co the sat nhau (OwO cooldown 15s) */
    private val sameCmdAt = mutableMapOf<String, Long>()
    private const val SAME_CMD_GAP_MS = 15_500L

    private suspend fun sendCommand(
        sender: DiscordSender,
        settings: OwoTrackerPrefs.Settings,
        text: String
    ): SendResult {
        if (text == HUNT_COMMAND || text == BATTLE_COMMAND) {
            val wait = SAME_CMD_GAP_MS - (System.currentTimeMillis() - (sameCmdAt[text] ?: 0L))
            if (wait > 0) {
                update { it.copy(status = "Chờ cooldown ${text.removePrefix("owo ")}…") }
                delay(wait + Random.nextLong(100L, 900L))
            }
        }
        awaitBurstSlot()
        update { it.copy(lastCommand = text) }
        sendCounter++
        val result = try {
            sender.send(text, settings, reopenChannel = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gui '$text' loi: ${e.message}")
            SendResult.SEND_FAILED
        }
        if (result == SendResult.OK) {
            lastSendOkAtMs = System.currentTimeMillis()
            if (text == HUNT_COMMAND || text == BATTLE_COMMAND) sameCmdAt[text] = lastSendOkAtMs
        }
        return result
    }

    /** Mo lai Discord vao dung kenh (co khoa de khong dam vao lenh thay gem dang go) */
    private suspend fun reopenChannel(sender: DiscordSender, link: String): Boolean {
        update { it.copy(status = "Đang mở lại kênh Discord…") }
        val r = DiscordSender.sendMutex.withLock { sender.openDiscordDetailed(link) }
        return r == OpenResult.OK_CHANNEL
    }

    private fun inQuietHours(): Boolean {
        if (QUIET_START_HOUR < 0 || QUIET_END_HOUR < 0) return false
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (QUIET_START_HOUR < QUIET_END_HOUR) {
            h >= QUIET_START_HOUR && h < QUIET_END_HOUR
        } else {
            h >= QUIET_START_HOUR || h < QUIET_END_HOUR
        }
    }

    /** Nghi `ms` mili giay (khong go lenh). Bao bot "dang nghi" de bot khong tuong OwO im lang. */
    private suspend fun CoroutineScope.restFor(ms: Long, label: String) {
        restingNow = true
        val end = System.currentTimeMillis() + ms
        update { it.copy(resting = true, waitEndMs = end, waitTotalMs = ms, waitLabel = label) }
        try {
            while (isActive && System.currentTimeMillis() < end) {
                val left = (end - System.currentTimeMillis()) / 1000
                val text = if (left >= 90) "$label, còn ${(left + 30) / 60} phút…" else "$label, còn ${left}s…"
                update { it.copy(status = text) }
                delay(5_000)
            }
        } finally {
            restingNow = false
            silenceBaselineMs = System.currentTimeMillis()
            update { it.copy(resting = false, waitEndMs = 0L, waitTotalMs = 0L, waitLabel = "") }
        }
    }

    /** Chon chi so huntbot nen nang tiep theo (null = khong biet cap do hoac da toi da het) */
    private fun pickUpgradeTrait(traits: Map<String, Int>?): String? {
        if (traits == null || traits.isEmpty()) return null
        fun below(t: String): Boolean {
            val level = traits[t] ?: return false
            return level < (TRAIT_MAX[t] ?: Int.MAX_VALUE)
        }
        if (below("cost")) return "cost"
        val effBelow = below("efficiency")
        val gainBelow = below("gain")
        if (effBelow && gainBelow) {
            // nang deu: ben nao con thap hon (theo ti le tren cap toi da) thi nang truoc
            val e = (traits["efficiency"] ?: 0).toDouble() / 215.0
            val g = (traits["gain"] ?: 0).toDouble() / 200.0
            return if (e <= g) "efficiency" else "gain"
        }
        if (effBelow) return "efficiency"
        if (gainBelow) return "gain"
        if (below("experience")) return "experience"
        if (below("duration")) return "duration"
        if (below("radar")) return "radar"
        return null
    }

    /** Doc che do hunt da luu (mac dinh 3 = khong dung) va dua vao trang thai */
    fun loadHuntMode(context: Context) {
        val mode = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_HUNT_MODE, 3)
        val speed = SpeedMode.fromId(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_SPEED_MODE, SpeedMode.NORMAL.id))
        val commands = FarmCommandConfig.decode(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_CMD_DISABLED, null))
        update { it.copy(huntMode = if (mode in 1..3) mode else 3, speed = speed, commands = commands) }
    }

    /**
     * Doi cau hinh bat / tat lenh (nut tren Hub noi) va nho cho lan sau. Co hieu luc o vong farm ke tiep, ke ca khi dang chay.
     * `change` nhan cau hinh hien tai, tra ve cau hinh moi.
     */
    fun changeCommands(context: Context, change: (FarmCommandConfig) -> FarmCommandConfig) {
        var saved = FarmCommandConfig.ALL_ON
        update { st ->
            saved = change(st.commands)
            st.copy(commands = saved)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_CMD_DISABLED, saved.encode()).apply()
    }

    fun setCommandEnabled(context: Context, cmd: FarmCmd, enabled: Boolean) =
        changeCommands(context) { it.with(cmd, enabled) }

    /** Doi toc do farm (co hieu luc ngay o vong ke tiep, ke ca khi dang chay) va nho cho lan sau */
    fun setSpeed(context: Context, speed: SpeedMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_SPEED_MODE, speed.id).apply()
        update { it.copy(speed = speed) }
    }

    /** Bam nut tren Hub noi: doi qua lai che do 1 -> 2 -> 3 -> 1 */
    fun cycleHuntMode(context: Context) {
        val next = when (_state.value.huntMode) {
            1 -> 2
            2 -> 3
            else -> 1
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_HUNT_MODE, next).apply()
        update { it.copy(huntMode = next, huntPaused = huntPauseReason(it.hub, next)) }
    }

    /**
     * Hunt co nen TAM DUNG khong (battle va viec vat van chay)? Tra ve ly do, hoac null = cu hunt.
     *  Che do 1: dung khi checklist Daily da du lootbox (vd 3/3) — het ngay (reset) thi hunt lai.
     *  Che do 2: dung khi gem dang dung it hon 3 VA trong kho khong con loai gem dang thieu.
     *  Che do 3: khong bao gio dung.
     */
    private fun huntPauseReason(hub: HubResponse?, mode: Int): String? {
        if (hub == null) return null
        when (mode) {
            1 -> {
                val daily = hub.checklists?.daily ?: return null
                val item = daily.items.orEmpty().firstOrNull { (it.name ?: "").lowercase().contains("lootbox") }
                    ?: return null
                val resets = daily.resetsAtEpoch
                val stale = resets != null && System.currentTimeMillis() / 1000 >= resets
                if (item.done == true && !stale) {
                    return "Mode 1: đã đủ lootbox hôm nay (${item.cur ?: "?"}/${item.max ?: "?"})"
                }
                return null
            }
            2 -> {
                val gems = hub.gems ?: return null
                val equipped = gems.equipped.orEmpty()
                if (equipped.isEmpty()) return null   // chua co du lieu gem -> khong dung
                val equippedSlots = equipped.mapNotNull { it.slot }.toSet()
                if (equippedSlots.containsAll(REQUIRED_GEM_SLOTS)) return null   // du 3 slot thuong (gem special "star" khong tinh)
                val spareSlots = gems.spare.orEmpty()
                    .filter { (it.count ?: 0L) > 0L }
                    .mapNotNull { it.slot }
                    .toSet()
                val missing = REQUIRED_GEM_SLOTS - equippedSlots
                val noSpare = missing.filter { it !in spareSlots }
                if (noSpare.isNotEmpty()) {
                    return "Mode 2: thiếu gem ${noSpare.joinToString(", ") { HubFormat.gemType(it) }} và trong kho không còn"
                }
                return null
            }
            else -> return null
        }
    }

    private fun choreDue(key: String, gapMs: Long): Boolean {
        val last = choreAt[key] ?: 0L
        return System.currentTimeMillis() - last >= gapMs
    }

    /** Gõ lần lượt các lệnh của 1 việc vặt. Trả về CAPTCHA nếu gặp captcha, OK nếu xong, còn lại là lỗi. */
    private suspend fun runChoreCommands(
        sender: DiscordSender,
        settings: OwoTrackerPrefs.Settings,
        key: String,
        commands: List<String>
    ): SendResult {
        choreAt[key] = System.currentTimeMillis()
        for ((i, cmd) in commands.withIndex()) {
            update { it.copy(status = "Làm việc vặt: $cmd") }
            val r = sendCommand(sender, settings, cmd)
            if (r != SendResult.OK) return r
            if (i < commands.size - 1) delay(Random.nextLong(2_500L, 5_000L))
        }
        return SendResult.OK
    }

    /**
     * Moi vong lam toi da 1 viec vat, theo du lieu bot doc duoc (bot la "mat"):
     * daily khi den gio, lam moi quest, va (neu Ruby da cho lenh) huntbot / hien te / nang cap.
     */
    /** Go owo quest, doi Quest Log hien, roi bam nut nhan thuong o tab Daily / Weekly / Quests neu da xong */
    private suspend fun claimQuests(
        service: AutoAccessibilityService,
        sender: DiscordSender,
        settings: OwoTrackerPrefs.Settings
    ): SendResult {
        choreAt["quest"] = System.currentTimeMillis()
        awaitBurstSlot()
        sendCounter++
        update { it.copy(status = "Kiểm tra nhiệm vụ…", lastCommand = QUEST_COMMAND) }
        return DiscordSender.sendMutex.withLock {
            val r = try {
                sender.sendWithoutLock(QUEST_COMMAND, settings, false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gui owo quest loi: ${e.message}")
                SendResult.SEND_FAILED
            }
            if (r == SendResult.OK) {
                lastSendOkAtMs = System.currentTimeMillis()
                delay(4_000)
                val res = QuestClaimer.claimAll(service)
                if (res.claimed.isNotEmpty()) {
                    update { it.copy(alert = "🎁 Đã nhận thưởng nhiệm vụ: " + res.claimed.joinToString(", ")) }
                } else if (res.tabsFound == 0 && !questUiWarned) {
                    questUiWarned = true
                    update {
                        it.copy(alert = "⚠️ Không thấy các nút trong Quest Log trên màn hình (Discord có thể không cho app đọc nút). Nhận thưởng nhiệm vụ cần bấm tay")
                    }
                }
            }
            r
        }
    }

    /**
     * Chon NGAU NHIEN 1 viec trong danh sach "lenh ngoai hunt/battle" roi go. Viec nao go thanh cong
     * thi phai cho CHORE_REQUEUE_MS (2 phut) moi quay lai danh sach; go loi thi van o trong danh sach.
     * Viec chi hop le khi du dieu kien (vd daily chi khi bot bao den gio). Khong con viec nao -> OK, khong go gi.
     */
    private suspend fun runOneChore(
        service: AutoAccessibilityService,
        sender: DiscordSender,
        settings: OwoTrackerPrefs.Settings
    ): SendResult {
        val hub = _state.value.hub
        val cmds = _state.value.commands   // viec nao bi tat o Setting thi khong dua vao danh sach random
        fun ready(key: String) = choreDue("wait_$key", CHORE_REQUEUE_MS)
        val pool = mutableListOf<Chore>()

        // Daily: chi khi bot bao da den gio (hoac chua co du lieu: thu 1 lan roi cho 6 gio)
        val daily = hub?.daily
        val dailyDue = if (daily == null) {
            choreDue("daily_unknown", DAILY_UNKNOWN_GAP_MS)
        } else {
            val left = daily.secondsLeft
            left != null && left <= 0L && choreDue("daily", DAILY_GAP_MS)
        }
        if (cmds.isEnabled(FarmCmd.DAILY) && dailyDue && ready("daily")) {
            pool += Chore("daily") {
                if (daily == null) choreAt["daily_unknown"] = System.currentTimeMillis()
                runChoreCommands(sender, settings, "daily", listOf(DAILY_COMMAND))
            }
        }

        // Huntbot: nhac Ruby khi dang khong chay (khong go lenh)
        val hb = hub?.huntbot
        if (hb != null && hb.hunting == false && choreDue("huntbot_idle", HUNTBOT_IDLE_NOTIFY_GAP_MS)) {
            choreAt["huntbot_idle"] = System.currentTimeMillis()
            notifyOwner("🤖 Huntbot đang không chạy. Bạn tự gõ `owo hb 24h` + mã xác nhận để chạy lại nhé.", toAlt = true)
        }

        // owo hb: xem / nhan thanh qua; neu huntbot vua xong thi nhac Ruby tu chay lai (can mat khau xac minh)
        val hbChore = Chore("hb") {
            val finished = hb?.secondsLeft?.let { it <= 0L } == true && choreDue("huntbot", HUNTBOT_GAP_MS)
            val r = runChoreCommands(sender, settings, "hb", listOf(HUNTBOT_COLLECT_COMMAND))
            if (r == SendResult.OK && finished) {
                choreAt["huntbot"] = System.currentTimeMillis()
                choreAt["huntbot_idle"] = System.currentTimeMillis()
                notifyOwner(
                    "🤖 Huntbot đã chạy xong và app đã nhận thành quả (owo hb). Chạy lại cần mật khẩu xác nhận: " +
                        "bot sẽ nhờ app gõ lệnh và gửi ảnh mật khẩu sang acc phụ, bạn chỉ cần đọc rồi nhắn lại đúng mã.",
                    toAlt = true
                )
            }
            r
        }
        if (cmds.isEnabled(FarmCmd.HUNTBOT) && ready("hb")) {
            // Huntbot da toi gio xong -> nhan thanh qua NGAY, khong cho boc ngau nhien
            if (hb?.hunting == true && (hb.secondsLeft ?: 1L) <= 0L) {
                val r = hbChore.run()
                if (r == SendResult.OK) choreAt["wait_hb"] = System.currentTimeMillis()
                return r
            }
            pool += hbChore
        }

        // Doi hinh + chi tiet vu khi cua pet trong doi (de ,teamcheck cua bot cham diem duoc). Thua hon cac viec khac.
        if (cmds.isEnabled(FarmCmd.TEAM) && choreDue("team_refresh", TEAM_REFRESH_GAP_MS)) {
            pool += Chore("team") { runChoreCommands(sender, settings, "team_refresh", listOf("owo team")) }
        }
        for (m in hub?.team?.members.orEmpty()) {
            val wid = m.weaponId?.takeIf { WEAPON_ID_RE.matches(it) } ?: continue
            if (cmds.isEnabled(FarmCmd.WEAPON) && choreDue("weapon_$wid", WEAPON_DETAIL_GAP_MS)) {
                pool += Chore("wpn_$wid") { runChoreCommands(sender, settings, "weapon_$wid", listOf("owo weapon $wid")) }
            }
        }

        // Mo lootbox / crate / bcrate
        if (cmds.isEnabled(FarmCmd.OPEN_BOXES) && OPEN_BOXES_COMMANDS.isNotEmpty() && ready("open_boxes")) {
            pool += Chore("open_boxes") { runChoreCommands(sender, settings, "open_boxes", OPEN_BOXES_COMMANDS) }
        }

        // Hien te lay essence
        if (cmds.isEnabled(FarmCmd.SACRIFICE) && SACRIFICE_COMMANDS.isNotEmpty() && ready("sacrifice")) {
            pool += Chore("sacrifice") { runChoreCommands(sender, settings, "sacrifice", SACRIFICE_COMMANDS) }
        }

        // Nang cap huntbot (chon chi so theo cap do bot doc duoc; bo qua khi bot biet essence = 0)
        if (cmds.isEnabled(FarmCmd.UPGRADE) && UPGRADE_ENABLED && ready("upgrade")) {
            val trait = hb?.traits?.let { pickUpgradeTrait(it) }
            val essence = hb?.essence
            if (trait != null && (essence == null || essence > 0L)) {
                pool += Chore("upgrade") {
                    // go owo hb sau khi nang cap de bot cap nhat cap do + essence moi
                    runChoreCommands(sender, settings, "upgrade", listOf("owo upg $trait all", HUNTBOT_COLLECT_COMMAND))
                }
            }
        }

        // Quest: mo Quest Log va bam nhan thuong
        if (cmds.isEnabled(FarmCmd.QUEST) && ready("quest")) {
            pool += Chore("quest") { claimQuests(service, sender, settings) }
        }

        // Lenh kiem tra nhe
        for (f in FILLERS) {
            if (cmds.isEnabled(f.cmd) && ready(f.key)) {
                pool += Chore(f.key) { runChoreCommands(sender, settings, "filler_" + f.key, listOf(f.command)) }
            }
        }

        if (pool.isEmpty()) return SendResult.OK
        val pick = pool.random()
        val r = pick.run()
        if (r == SendResult.OK) choreAt["wait_${pick.key}"] = System.currentTimeMillis()
        return r
    }

    /** Canh gac: hoi bot moi vai giay. Thay captcha -> dung; OwO im lang -> yeu cau mo lai kenh / dung han; mat bot -> dung. */
    private suspend fun watchBot(ctx: Context, apiUrl: String, token: String) {
        while (true) {
            delay(BOT_POLL_MS)
            val flag = if (restingNow) "2" else "1"
            val info = OwoTrackerApi.fetchAlerts(apiUrl, token, flag)
            val now = System.currentTimeMillis()

            if (info == null) {
                if (unreachableSinceMs == 0L) unreachableSinceMs = now
                if (now - unreachableSinceMs > BOT_UNREACHABLE_STOP_MS) {
                    hardStop(ctx, "Mất kết nối với bot hơn 2 phút — đã dừng vì không còn ai canh captcha")
                    return
                }
                continue
            }
            unreachableSinceMs = 0L

            if (info.off) {
                val j = job
                plannedStop("🔴 Đã tắt từ xa bằng lệnh ,off — app đã dừng farm. Gõ ,off on trong kênh Discord rồi bấm Bắt đầu lại")
                j?.cancel()
                return
            }

            if (info.captchaActive) {
                handleCaptcha(ctx, info.captchaAgeSeconds ?: 0L)
                job?.cancel()
                job = null
                return
            }

            // OwO im lang: chi tinh tu luc da bat dau go (khong tinh thoi gian truoc do / luc nghi)
            if (!restingNow && lastSendOkAtMs > 0L && now - lastSendOkAtMs < 90_000L) {
                val sinceBaseline = (now - silenceBaselineMs) / 1000
                val age = info.lastReplyAgeSeconds
                if (age != null) {
                    val silent = minOf(age, sinceBaseline)
                    if (silent > SILENT_STOP_SEC) {
                        hardStop(ctx, "OwO không trả lời hơn ${silent / 60} phút dù app vẫn gõ — đã dừng")
                        return
                    }
                    if (silent > SILENT_RECOVER_SEC) {
                        silentRecoverRequested = true
                    }
                }
            }
        }
    }

    private suspend fun CoroutineScope.runFarm(service: AutoAccessibilityService) {
        val ctx = service.applicationContext
        val first = OwoTrackerPrefs.load(ctx)
        unreachableSinceMs = 0L
        val watcher = launch { watchBot(ctx, first.apiUrl, first.token) }
        try {
            runLoop(service)
        } finally {
            watcher.cancel()
            restingNow = false
        }
    }

    private suspend fun CoroutineScope.runLoop(service: AutoAccessibilityService) {
        val ctx = service.applicationContext
        var settings = OwoTrackerPrefs.load(ctx)
        if (settings.apiUrl.isBlank() || settings.token.isBlank()) {
            plannedStop("Chưa nhập địa chỉ API / token ở Cài đặt")
            return
        }
        if (!_state.value.commands.anyEnabled()) {
            plannedStop("Chưa bật lệnh nào — vào tab Setting, mục \"Lệnh trong farm\" bật ít nhất 1 lệnh rồi bấm Bắt đầu lại")
            return
        }

        val startIso = Instant.now().toString()
        choreAt.clear()
        choreAt["wait_zoo"] = System.currentTimeMillis()   // zoo vua duoc go o buoc "Check zoo luc bat dau"
        silentRecoverRequested = false
        questUiWarned = false
        lastSendOkAtMs = 0L
        silenceBaselineMs = System.currentTimeMillis()
        update {
            it.copy(
                running = true, resting = false, status = "Đang chuẩn bị…", hunts = 0, battles = 0,
                sessionStartIso = startIso, sessionNewPets = emptyList(), alert = null
            )
        }

        // Bot dang TAT app (lenh ,off) -> khong bat dau farm
        if (OwoTrackerApi.fetchAppOff(settings.apiUrl, settings.token) == true) {
            plannedStop("🔴 Bot đang TẮT app (lệnh ,off). Gõ ,off on trong kênh Discord rồi bấm Bắt đầu lại")
            return
        }

        // Dang co canh bao captcha chua giai -> khong go them lenh nao
        if (OwoTrackerApi.fetchCaptchaActive(settings.apiUrl, settings.token) == true) {
            plannedStop("🚨 OwO đang đòi captcha. Giải xong gõ ,captcha clear (trong kênh Discord) rồi bấm Bắt đầu lại")
            return
        }

        // Qua het cac buoc kiem tra dau vao: tu day moi tinh la 1 phien farm thuc su chay
        recordStats { b, now -> b.startSession(now) }

        // --- 1. Tim kenh duoc chi dinh ---
        refreshHub(ctx)
        var link = settings.discordLink.trim()
        if (link.isBlank()) {
            val auto = _state.value.hub?.channel?.link.orEmpty()
            if (auto.isNotBlank()) {
                link = auto
                OwoTrackerPrefs.saveDiscordLink(ctx, auto)
                settings = OwoTrackerPrefs.load(ctx)
            }
        }
        if (link.isBlank()) {
            plannedStop("Chưa biết kênh nào để farm. Gõ ,setserver here (theo dõi cả server) hoặc ,setchannel trong Discord (cho bot), hoặc dán link kênh ở Cài đặt")
            return
        }

        // --- 2. Mo Discord vao dung kenh (bat buoc vao dung kenh moi go lenh) ---
        update { it.copy(status = "Đang mở Discord…") }
        val sender = DiscordSender(service)
        val opened = sender.openDiscordDetailed(link)
        if (opened != OpenResult.OK_CHANNEL) {
            hardStop(ctx, "Không vào được kênh: " + describeOpen(opened))
            return
        }
        if (!settings.enabled) {
            update {
                it.copy(alert = "ℹ️ Ô \"Bật tự động kiểm tra lệnh\" trong Cài đặt đang tắt — gem sẽ không tự thay")
            }
        }

        // --- 3. Check zoo luc bat dau ---
        // Lenh Zoo tat o Setting -> bo qua buoc nay (bot se khong biet chinh xac pet nao da co cho toi khi Ruby tu go owo zoo)
        if (_state.value.commands.isEnabled(FarmCmd.ZOO)) {
            update { it.copy(status = "Kiểm tra zoo…") }
            val zooSentAt = System.currentTimeMillis()
            val zooResult = sendCommand(sender, settings, ZOO_COMMAND)
            if (zooResult == SendResult.CAPTCHA) {
                handleCaptcha(ctx)
                return
            }
            if (zooResult == SendResult.OK) {
                val waitStart = System.currentTimeMillis()
                var zooRead = false
                while (isActive && System.currentTimeMillis() - waitStart < ZOO_WAIT_MS) {
                    delay(3_000)
                    refreshHub(ctx)
                    val age = _state.value.hub?.zoo?.ageSeconds
                    if (age != null && age < (System.currentTimeMillis() - zooSentAt) / 1000 + 4) {
                        zooRead = true
                        break
                    }
                }
                if (!zooRead) Log.w(TAG, "Chua xac nhan bot da doc zoo - van farm")
            } else {
                hardStop(ctx, "Không gõ được lệnh zoo: " + describeSend(zooResult))
                return
            }
        }

        // --- 4. Vong farm ---
        val runStartMs = System.currentTimeMillis()
        var sessionStartMs = runStartMs
        var sessionLimitMs = Random.nextLong(SESSION_MIN_MS, SESSION_MAX_MS)
        var failsInRow = 0
        var cyclesUntilRest = _state.value.speed.let { Random.nextInt(it.restEveryMin, it.restEveryMax + 1) }
        silenceBaselineMs = System.currentTimeMillis()

        cycle@ while (isActive) {
            val cycleStart = System.currentTimeMillis()
            val sp = _state.value.speed   // doc moi vong: doi toc do giua chung co hieu luc ngay

            // 4.0 Het tong gio chay toi da
            if (cycleStart - runStartMs > MAX_TOTAL_HOURS * 3_600_000L) {
                plannedStop("Đã chạy đủ $MAX_TOTAL_HOURS giờ — dừng để nghỉ. Bấm Bắt đầu để chạy tiếp")
                return
            }

            // 4.1 Gio yen lang
            if (inQuietHours()) {
                restFor(5 * 60_000L, "Giờ yên lặng")
                continue
            }

            // 4.2 Het 1 phien -> nghi dai roi mo lai kenh
            if (cycleStart - sessionStartMs > sessionLimitMs) {
                restFor(Random.nextLong(BREAK_MIN_MS, BREAK_MAX_MS), "Nghỉ giữa phiên")
                if (!isActive) return
                if (!reopenChannel(sender, link)) {
                    hardStop(ctx, "Sau giờ nghỉ không mở lại được kênh Discord")
                    return
                }
                sessionStartMs = System.currentTimeMillis()
                sessionLimitMs = Random.nextLong(SESSION_MIN_MS, SESSION_MAX_MS)
                continue
            }

            // 4.3 OwO im lang -> mo lai kenh
            if (silentRecoverRequested) {
                silentRecoverRequested = false
                update { it.copy(alert = "⚠️ OwO không trả lời hơn 2,5 phút — đang mở lại kênh") }
                reopenChannel(sender, link)
                silenceBaselineMs = System.currentTimeMillis()
                delay(3_000)
            }

            // 4.4 Hunt va battle len dau de dung nhip cooldown; viec vat chen vao luc cho (4.8)
            update { it.copy(status = "Đang farm…") }

            // 4.5 hunt (tuy che do hunt co the tam dung, battle van chay)
            val cmds = _state.value.commands   // doc moi vong: bat/tat lenh giua chung co hieu luc o vong ke tiep
            val huntOn = cmds.isEnabled(FarmCmd.HUNT)
            val pauseReason = if (huntOn) huntPauseReason(_state.value.hub, _state.value.huntMode) else null
            update { it.copy(huntPaused = pauseReason) }
            // 4.5-4.6b hunt / battle / "owo": THU TU NGAU NHIEN moi vong (khong luc nao cung hunt -> battle -> owo)
            var cycleOk = true   // false neu battle go loi: lenh "owo" ke sau bi bo qua
            for (step in listOf(0, 1, 2).shuffled()) {
                when (step) {
                    0 -> {
            if (!huntOn) {
                // Hunt tat o Setting: khong go hunt (battle va viec vat van chay neu dang bat)
            } else if (pauseReason == null) {
                val huntResult = sendCommand(sender, settings, HUNT_COMMAND)
                if (huntResult == SendResult.CAPTCHA) {
                    handleCaptcha(ctx)
                    return
                }
                if (huntResult == SendResult.OK) {
                    failsInRow = 0
                    update { it.copy(hunts = it.hunts + 1) }
                    recordStats { b, now -> b.addHunt(now) }
                } else {
                    failsInRow++
                    if (failsInRow >= MAX_FAILS_IN_ROW) {
                        hardStop(ctx, "Gõ lệnh lỗi $failsInRow lần liên tiếp (${describeSend(huntResult)}) — đã dừng")
                        return
                    }
                    update { it.copy(status = "Lỗi gõ lệnh (${describeSend(huntResult)}), lần $failsInRow — đang khắc phục…") }
                    if (failsInRow >= 2) reopenChannel(sender, link)
                    waitWithCountdown(minOf(4_000L * failsInRow, 30_000L), "Thử lại sau lỗi gõ lệnh")
                    continue@cycle
                }
                // Khac lenh (hunt -> battle) thi go luon, khong nghi. Chi cooldown cua CHINH lenh do (hunt -> hunt) moi phai cho.
            } else {
                update { it.copy(status = "Hunt tạm dừng — $pauseReason") }
            }

                    }
                    1 -> {
            // 4.6 battle (tat o Setting thi bo qua; cycleOk = false chi khi battle loi)
            if (cmds.isEnabled(FarmCmd.BATTLE)) {
                val battleResult = sendCommand(sender, settings, BATTLE_COMMAND)
                if (battleResult == SendResult.CAPTCHA) {
                    handleCaptcha(ctx)
                    return
                }
                if (battleResult == SendResult.OK) {
                    failsInRow = 0
                    update { it.copy(battles = it.battles + 1) }
                    recordStats { b, now -> b.addBattle(now) }
                } else {
                    failsInRow++
                    if (failsInRow >= MAX_FAILS_IN_ROW) {
                        hardStop(ctx, "Gõ lệnh lỗi $failsInRow lần liên tiếp (${describeSend(battleResult)}) — đã dừng")
                        return
                    }
                    cycleOk = false
                    if (failsInRow >= 2) reopenChannel(sender, link)
                }
            }

                    }
                    else -> {
            // 4.6b Moi vong go them 1 lan "owo" (ngay sau battle, khong nghi). Loi go chi bo qua: khong tinh vao failsInRow.
            if (cycleOk && cmds.isEnabled(FarmCmd.OWO)) {
                val owoResult = sendCommand(sender, settings, OWO_COMMAND)
                if (owoResult == SendResult.CAPTCHA) {
                    handleCaptcha(ctx)
                    return
                }
            }

                    }
                }
            }

            // 4.7 Nghi giai lao ngan cho giong nguoi that
            cyclesUntilRest--
            if (cyclesUntilRest <= 0) {
                cyclesUntilRest = Random.nextInt(sp.restEveryMin, sp.restEveryMax + 1)
                restFor(Random.nextLong(sp.restMinMs, sp.restMaxMs), "Nghỉ giải lao")
                continue
            }

            // 4.8 Luc cho cooldown: cap nhat du lieu, roi chen viec vat / lenh kiem tra cho den het vong
            val target = Random.nextLong(sp.cycleMinMs, sp.cycleMaxMs)
            refreshHub(ctx)
            while (isActive && target - (System.currentTimeMillis() - cycleStart) >= FILLER_MIN_REMAINING_MS) {
                val before = sendCounter
                val r = runOneChore(service, sender, settings)
                if (r == SendResult.CAPTCHA) {
                    handleCaptcha(ctx)
                    return
                }
                if (sendCounter == before) break            // khong con viec nao den gio
                if (r != SendResult.OK) {
                    Log.w(TAG, "Viec chen loi: ${describeSend(r)} (bo qua, thu lai sau)")
                    break
                }
                update { it.copy(status = "Đang farm…") }
            }
            val left = target - (System.currentTimeMillis() - cycleStart)
            if (left > 0) waitWithCountdown(left, "Chờ cooldown hunt/battle") else delay(800)
        }
    }
}
