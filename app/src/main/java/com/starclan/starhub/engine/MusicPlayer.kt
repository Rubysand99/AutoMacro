package com.starclan.starhub.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.starclan.starhub.R
import com.starclan.starhub.Str
import com.starclan.starhub.data.ConfigPrefs
import com.starclan.starhub.data.MusicConfig
import com.starclan.starhub.data.PlaylistRules
import com.starclan.starhub.data.Track
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * Nhac nen khi farm = PLAYLIST nhieu bai (file trong may hoac link YouTube/TikTok/Facebook...), phat lan luot,
 * het danh sach thi lap lai (hoac ngau nhien neu bat shuffle). Link duoc tach am thanh bang [LinkResolver] (yt-dlp).
 * Bai loi (mat mang, link chet, file bi xoa) thi bo qua sang bai ke tiep; TAT CA cac bai deu loi lien tiep thi dung han va bao loi.
 * Kenh MEDIA (khac kenh BAO THUC cua AlarmPlayer) nen chuong captcha van keu to, va FarmController dung nhac khi farm ket thuc.
 * Mat quyen am thanh (cuoc goi, app khac) thi tam dung, duoc lai thi phat tiep.
 * `preview` = nghe thu ngay o tab Config, khong can dang farm.
 */
object MusicPlayer {
    private const val TAG = "MusicPlayer"
    private const val DEFAULT_UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    private val main = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "music-resolver").apply { isDaemon = true }
    }

    private var player: ExoPlayer? = null   // ExoPlayer chi duoc dung tren luong chinh -> moi thao tac qua runMain
    private var focusRequest: AudioFocusRequest? = null
    private var audioManager: AudioManager? = null
    private var pausedByFocus = false
    private var appCtx: Context? = null
    private var cfg: MusicConfig = MusicConfig()
    private var currentSource: String? = null   // bai dang phat (theo nguon, khong theo vi tri, vi Ruby co the sua danh sach luc dang phat)
    private var failures = 0                    // so bai loi lien tiep
    private var plannedNext: String? = null    // bai ke tiep da chon san (de tai truoc; ngau nhien thi phat dung bai nay)
    private val prefetcher: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "music-prefetch").apply { isDaemon = true }
    }
    private var generation = 0                  // tang moi khi doi bai / dung -> bo ket qua tach link cu

    @Volatile
    var playing: Boolean = false
        private set

    /** Dang lay am thanh tu link hoac chuan bi bai (chua nghe thay gi). */
    @Volatile
    var loading: Boolean = false
        private set

    /** Ten bai dang phat / dang tai (rong khi dung). */
    @Volatile
    var nowPlaying: String = ""
        private set

    /** Loi gan nhat (kem ly do that) de tab Config hien thanh chu do ben canh nut; rong khi dang phat binh thuong. */
    @Volatile
    var lastError: String = ""
        private set

    private var currentExt = ""   // dinh dang am thanh cua bai dang tai (m4a, webm...), de chen vao ly do loi

    /** DUNG HAN vi khong phat duoc gi (de tab Config hien thong bao va doi nut Nghe thu). */
    var onError: ((String) -> Unit)? = null

    /** 1 bai loi nen bi bo qua, van tiep tuc phat bai khac. */
    var onSkipped: ((String) -> Unit)? = null

    /** Doi bai / doi trang thai (co the goi tu luong bat ky; noi nhan tu chuyen sang luong chinh). */
    var onChanged: (() -> Unit)? = null

    /** Goi luc bat dau farm: chi phat khi Ruby da bat cong tac va co it nhat 1 bai. */
    @Synchronized
    fun startIfEnabled(context: Context) {
        val c = ConfigPrefs.loadMusic(context)
        if (c.shouldPlay) begin(context, c)
    }

    /** Nghe thu (khong phu thuoc cong tac bat/tat). */
    @Synchronized
    fun preview(context: Context, config: MusicConfig) {
        if (config.hasTrack) begin(context, config)
    }

    /** Qua bai ke tiep (nut "Bai tiep" o tab Config). */
    @Synchronized
    fun skip() {
        if (!playing) return
        failures = 0
        advance()
    }

    /** Ruby sua playlist / che do ngau nhien luc dang phat: ap dung ngay, KHONG ngat bai dang phat. */
    @Synchronized
    fun refresh(config: MusicConfig) {
        if (!playing) return
        cfg = config
        if (!cfg.hasTrack) {
            endSession()
            return
        }
        val p = player ?: return
        val single = cfg.tracks.size == 1
        val g = cfg.gain
        runMain {
            try {
                p.repeatMode = if (single) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                p.volume = g
            } catch (e: Exception) {
                Log.w(TAG, "Khong ap dung duoc playlist moi: ${e.message}")
            }
        }
    }

    @Synchronized
    fun setVolume(volumePercent: Int) {
        cfg = cfg.copy(volume = MusicConfig.clampVolume(volumePercent))
        val p = player ?: return
        val g = cfg.gain
        runMain {
            try {
                p.volume = g
            } catch (e: Exception) {
                Log.w(TAG, "Khong chinh duoc am luong: ${e.message}")
            }
        }
    }

    @Synchronized
    fun stop() {
        endSession()
    }

    // ------------------------------------------------------------------ noi bo (luon trong khoa cua object)

    private fun begin(context: Context, config: MusicConfig) {
        endSession()
        val app = context.applicationContext
        appCtx = app
        cfg = config
        failures = 0
        lastError = ""
        currentSource = null
        plannedNext = null
        requestFocus(app)
        playing = true
        advance()
    }

    private fun requestFocus(app: Context) {
        val am = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager = am
        if (am == null) return
        try {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(mediaAttrs())
                .setOnAudioFocusChangeListener { change -> onFocusChange(change) }
                .build()
            focusRequest = req
            am.requestAudioFocus(req)
        } catch (e: Exception) {
            Log.w(TAG, "Khong xin duoc quyen am thanh: ${e.message}")
        }
    }

    private fun mediaAttrs(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    /** Chon bai ke tiep theo thu tu / ngau nhien roi phat. */
    private fun advance() {
        val planned = plannedNext?.let { s -> cfg.tracks.indexOfFirst { it.source == s } } ?: -1
        plannedNext = null
        val next = if (planned >= 0) planned else pickNext()
        if (next < 0) {
            endSession()
            return
        }
        startTrack(cfg.tracks[next])
    }

    private fun pickNext(): Int {
        val cur = currentSource?.let { s -> cfg.tracks.indexOfFirst { it.source == s } } ?: -1
        return PlaylistRules.nextIndex(cur, cfg.tracks.size, cfg.shuffle) { Random.nextInt(it) }
    }

    /** Dang phat bai nay thi tai san bai ke tiep, de chuyen bai khong phai cho. */
    private fun prefetchNext(gen: Int) {
        if (cfg.tracks.size < 2) return
        val i = pickNext()
        if (i < 0) return
        val nt = cfg.tracks[i]
        plannedNext = nt.source
        val app = appCtx ?: return
        if (!nt.isLink || LinkResolver.isCached(app, nt.source)) return
        prefetcher.execute {
            try {
                val r = LinkResolver.resolve(app, nt.source)
                if (r.title.isNotBlank() && r.title != nt.title) main.post {
                    synchronized(this) { if (gen == generation) saveTitle(app, nt, r.title) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Tai truoc bai ke tiep loi (se thu lai khi toi luot): ${e.message}")
            }
        }
    }

    private fun startTrack(t: Track) {
        generation++
        val gen = generation
        releasePlayer()
        currentSource = t.source
        nowPlaying = t.displayName
        currentExt = ""
        loading = true
        notifyChanged()
        val app = appCtx ?: return
        if (!t.isLink) {
            prepare(app, t, Uri.parse(t.source), emptyMap(), gen)
            return
        }
        worker.execute {
            var reason = ""
            val res = try {
                LinkResolver.resolve(app, t.source)
            } catch (e: Exception) {
                reason = LinkResolver.shortReason(e)
                null
            }
            main.post { onResolved(gen, t, res, reason) }
        }
    }

    @Synchronized
    private fun onResolved(gen: Int, t: Track, res: LinkResolver.Resolved?, reason: String) {
        if (gen != generation || !playing) return   // da doi bai / dung trong luc cho tach link
        val app = appCtx ?: return
        if (res == null) {
            trackFailed(t, Str.get(R.string.music_link_khong_lay_duoc, t.displayName), reason)
            return
        }
        if (res.title.isNotBlank() && res.title != t.title) saveTitle(app, t, res.title)
        nowPlaying = res.title.ifBlank { t.displayName }
        currentExt = res.ext
        notifyChanged()
        prepare(app, t, Uri.parse(res.url), res.headers, gen)
    }

    private fun saveTitle(app: Context, t: Track, title: String) {
        try {
            cfg = cfg.withTitle(t.source, title)
            val stored = ConfigPrefs.loadMusic(app)
            ConfigPrefs.saveMusic(app, stored.withTitle(t.source, title))
        } catch (e: Exception) {
            Log.w(TAG, "Khong luu duoc ten bai: ${e.message}")
        }
    }

    private fun runMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private fun prepare(app: Context, t: Track, uri: Uri, headers: Map<String, String>, gen: Int) {
        runMain {
            synchronized(this) {
                if (gen != generation || !playing) return@runMain
                try {
                    val http = DefaultHttpDataSource.Factory()
                        .setAllowCrossProtocolRedirects(true)
                        .setConnectTimeoutMs(15_000)
                        .setReadTimeoutMs(20_000)
                        .setUserAgent(headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value ?: DEFAULT_UA)
                        .setDefaultRequestProperties(headers.filterKeys { !it.equals("User-Agent", true) })
                    val p = ExoPlayer.Builder(app)
                        .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(app, http)))
                        .setAudioAttributes(
                            androidx.media3.common.AudioAttributes.Builder()
                                .setUsage(C.USAGE_MEDIA)
                                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                                .build(),
                            false   // quyen am thanh da tu xu ly o tren
                        )
                        .setWakeMode(C.WAKE_MODE_NETWORK)   // tat man hinh van tai/phat tiep duoc bai tu link
                        .build()
                    p.repeatMode = if (cfg.tracks.size == 1) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    p.volume = cfg.gain
                    p.addListener(object : Player.Listener {
                        private var started = false
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_READY && !started) {
                                started = true
                                onPrepared(gen)
                            } else if (state == Player.STATE_ENDED) {
                                onCompleted(gen)
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            Log.w(TAG, "ExoPlayer loi: ${error.errorCodeName}", error)
                            onPlayerFailed(gen, t, error)
                        }
                    })
                    player = p
                    p.setMediaItem(MediaItem.fromUri(uri))
                    p.playWhenReady = true
                    p.prepare()
                } catch (e: Exception) {
                    Log.w(TAG, "Khong phat duoc nhac: ${e.message}")
                    releasePlayer()
                    trackFailed(t, Str.get(R.string.music_khong_mo_duoc_file_nhac), e.message ?: e.javaClass.simpleName)
                }
            }
        }
    }

    @Synchronized
    private fun onPrepared(gen: Int) {
        if (gen != generation || !playing) return
        failures = 0
        lastError = ""
        loading = false
        notifyChanged()
        prefetchNext(gen)
    }

    @Synchronized
    private fun onCompleted(gen: Int) {
        if (gen != generation || !playing) return
        advance()
    }

    @Synchronized
    private fun onPlayerFailed(gen: Int, t: Track, e: PlaybackException) {
        if (gen != generation || !playing) return
        releasePlayer()
        val cause = (e.cause?.message ?: "").take(120)
        val why = "ExoPlayer ${e.errorCodeName}" + (if (cause.isBlank()) "" else ": $cause") + (if (currentExt.isBlank()) "" else " (${currentExt})")
        trackFailed(t, Str.get(R.string.music_khong_phat_duoc_file_nhac), why)
    }

    /** 1 bai loi: bo qua sang bai khac; tat ca cac bai deu loi lien tiep thi dung han. */
    private fun trackFailed(t: Track, msg: String, reason: String = "") {
        failures++
        Log.w(TAG, "Bai loi: ${t.source} -> $reason")
        val detail = if (reason.isBlank()) msg else "$msg\n($reason)"
        lastError = detail
        if (PlaylistRules.shouldGiveUp(failures, cfg.tracks.size)) {
            endSession()
            onError?.invoke(Str.get(R.string.music_ca_playlist_loi) + (if (reason.isBlank()) "" else "\n($reason)"))
            return
        }
        onSkipped?.invoke(detail)
        advance()
    }

    private fun notifyChanged() {
        val cb = onChanged ?: return
        main.post { cb() }
    }

    @Synchronized
    private fun onFocusChange(change: Int) {
        val p = player ?: return
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { pausedByFocus = true; runMain { try { p.pause() } catch (e: Exception) { Log.w(TAG, "Loi tam dung: ${e.message}") } } }
            AudioManager.AUDIOFOCUS_GAIN -> if (pausedByFocus) { pausedByFocus = false; runMain { try { p.play() } catch (e: Exception) { Log.w(TAG, "Loi phat tiep: ${e.message}") } } }
        }
    }

    /** Ket thuc ca phien phat (nhả quyen am thanh, xoa trang thai). */
    private fun endSession() {
        generation++            // bo moi ket qua tach link dang cho
        pausedByFocus = false
        releasePlayer()
        try {
            val req = focusRequest
            if (req != null) audioManager?.abandonAudioFocusRequest(req)
        } catch (e: Exception) {
            // bo qua
        }
        focusRequest = null
        val was = playing
        playing = false
        loading = false
        nowPlaying = ""
        currentSource = null
        if (was) notifyChanged()
    }

    private fun releasePlayer() {
        val p = player ?: return
        player = null
        runMain {
            try {
                p.stop()
            } catch (e: Exception) {
                // bo qua
            }
            try {
                p.release()
            } catch (e: Exception) {
                // bo qua
            }
        }
    }
}
