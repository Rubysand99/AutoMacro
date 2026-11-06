package com.starclan.starhub.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.starclan.starhub.data.ConfigPrefs
import com.starclan.starhub.data.MusicConfig

/**
 * Nhac nen khi farm: lap lai file nhac Ruby chon o tab Config.
 * Kenh MEDIA (khac kenh BAO THUC cua AlarmPlayer) nen chuong captcha van keu to, va FarmController dung nhac khi farm ket thuc.
 * Mat quyen am thanh (cuoc goi, app khac) thi tam dung, duoc lai thi phat tiep.
 * `preview` = nghe thu ngay o tab Config, khong can dang farm.
 */
object MusicPlayer {
    private const val TAG = "MusicPlayer"

    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var audioManager: AudioManager? = null
    private var pausedByFocus = false

    @Volatile
    var playing: Boolean = false
        private set

    /** Bao ra ngoai khi file khong phat duoc (de tab Config hien thong bao). */
    var onError: ((String) -> Unit)? = null

    /** Goi luc bat dau farm: chi phat khi Ruby da bat cong tac va da chon file. */
    @Synchronized
    fun startIfEnabled(context: Context) {
        val cfg = ConfigPrefs.loadMusic(context)
        if (cfg.shouldPlay) play(context, cfg)
    }

    /** Nghe thu (khong phu thuoc cong tac bat/tat). */
    @Synchronized
    fun preview(context: Context, cfg: MusicConfig) {
        if (cfg.hasTrack) play(context, cfg)
    }

    @Synchronized
    fun setVolume(volumePercent: Int) {
        val g = MusicConfig.clampVolume(volumePercent) / 100f
        try {
            player?.setVolume(g, g)
        } catch (e: Exception) {
            Log.w(TAG, "Khong chinh duoc am luong: ${e.message}")
        }
    }

    @Synchronized
    fun stop() {
        pausedByFocus = false
        release()
    }

    private fun play(context: Context, cfg: MusicConfig) {
        release()
        val app = context.applicationContext
        val am = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager = am
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            if (am != null) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener { change -> onFocusChange(change) }
                    .build()
                focusRequest = req
                am.requestAudioFocus(req)
            }
            val mp = MediaPlayer()
            mp.setAudioAttributes(attrs)
            mp.setDataSource(app, Uri.parse(cfg.uri))
            mp.isLooping = true
            mp.setVolume(cfg.gain, cfg.gain)
            mp.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "MediaPlayer loi what=$what extra=$extra")
                stop()
                onError?.invoke("Không phát được file nhạc — chọn lại file khác")
                true
            }
            mp.setOnPreparedListener { it.start() }
            mp.prepareAsync()
            player = mp
            playing = true
        } catch (e: Exception) {
            Log.w(TAG, "Khong phat duoc nhac: ${e.message}")
            release()
            onError?.invoke("Không mở được file nhạc (có thể đã bị xoá hoặc mất quyền đọc) — chọn lại")
        }
    }

    @Synchronized
    private fun onFocusChange(change: Int) {
        val mp = player ?: return
        try {
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (mp.isPlaying) { mp.pause(); pausedByFocus = true }
                AudioManager.AUDIOFOCUS_GAIN -> if (pausedByFocus) { mp.start(); pausedByFocus = false }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Loi xu ly quyen am thanh: ${e.message}")
        }
    }

    private fun release() {
        try {
            player?.stop()
        } catch (e: Exception) {
            // chua kip prepare thi stop() nem loi, bo qua
        }
        try {
            player?.release()
        } catch (e: Exception) {
            // bo qua
        }
        player = null
        try {
            val req = focusRequest
            if (req != null) audioManager?.abandonAudioFocusRequest(req)
        } catch (e: Exception) {
            // bo qua
        }
        focusRequest = null
        playing = false
    }
}
