package com.starclan.starhub

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.starclan.starhub.data.ConfigPrefs
import com.starclan.starhub.data.MusicConfig
import com.starclan.starhub.data.MusicLinks
import com.starclan.starhub.data.Track
import com.starclan.starhub.engine.FarmController
import com.starclan.starhub.engine.MusicPlayer
import com.starclan.starhub.view.UiTheme

/**
 * Tab "Config": cac lua chon rieng cua app, luu ngay tren may (khong can bam Luu).
 *  - Nhac nen : bat/tat, PLAYLIST nhieu bai (them file trong may / dan link YouTube-TikTok-Facebook), ngau nhien, am luong, nghe thu. Nhac tu phat khi bat dau farm va dung khi farm ket thuc.
 *  - Tier gem : mo hop thoai chon tier gem bot duoc phep dung (truoc day nam o tab Cai dat).
 * Giao dien dung bang code (khong XML) giong tab Cong cu.
 */
class ConfigFragment : Fragment() {

    private lateinit var music: MusicConfig
    private lateinit var swMusic: SwitchCompat
    private lateinit var tvNow: TextView
    private lateinit var tvErr: TextView
    private lateinit var tvCount: TextView
    private lateinit var listBox: LinearLayout
    private lateinit var swShuffle: SwitchCompat
    private lateinit var btnPreview: Button
    private lateinit var btnNext: Button
    private lateinit var seekVolume: SeekBar
    private lateinit var tvVolume: TextView
    private var updatingUi = false

    private val pickMusic = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onFilesPicked(uris)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun c(hex: String) = Color.parseColor(hex)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        music = ConfigPrefs.loadMusic(ctx)

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        col.addView(text(getString(R.string.tab_config), 22f, UiTheme.TEXT_PRIMARY, bold = true))
        col.addView(text(getString(R.string.config_note_autosave), 12f, UiTheme.TEXT_MUTED)
            .also { it.setPadding(0, dp(2), 0, dp(6)) })

        // ---- Nhạc nền ----
        col.addView(sectionTitle(getString(R.string.config_section_music)))
        val musicCard = card()
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        val left = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        left.addView(text(getString(R.string.config_music_title), 14f, UiTheme.TEXT_PRIMARY, bold = true))
        left.addView(text(getString(R.string.config_music_sub), 11.5f, UiTheme.TEXT_MUTED))
        row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        swMusic = SwitchCompat(ctx).apply { setOnCheckedChangeListener { _, on -> if (!updatingUi) onMusicToggled(on) } }
        row.addView(swMusic, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .also { it.leftMargin = dp(10) })
        musicCard.addView(row, lp(top = 0, bottom = 10))

        tvNow = text("", 12.5f, UiTheme.ACCENT)
        musicCard.addView(tvNow, lp(top = 0, bottom = 6))
        tvErr = text("", 12f, UiTheme.BAD).also { it.setTextIsSelectable(true) }   // lý do lỗi thật, chọn được để chép gửi đi
        musicCard.addView(tvErr, lp(top = 0, bottom = 6))
        tvCount = text("", 12.5f, UiTheme.TEXT_SECONDARY)
        musicCard.addView(tvCount, lp(top = 0, bottom = 4))
        listBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        musicCard.addView(listBox, lp(top = 0, bottom = 6))

        val addRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        addRow.addView(button(getString(R.string.config_add_file)) { pickMusic.launch(arrayOf("audio/*")) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addRow.addView(button(getString(R.string.config_paste_link)) { showPasteLinkDialog() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.leftMargin = dp(8) })
        musicCard.addView(addRow, lp(top = 0, bottom = 4))
        musicCard.addView(text(getString(R.string.config_link_note), 11f, UiTheme.TEXT_MUTED), lp(top = 0, bottom = 8))

        val playRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        btnPreview = button(getString(R.string.config_preview)) { onPreviewClicked() }
        playRow.addView(btnPreview, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        btnNext = button(getString(R.string.config_next_track)) { MusicPlayer.skip() }
        playRow.addView(btnNext, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.leftMargin = dp(8) })
        musicCard.addView(playRow, lp(top = 0, bottom = 6))

        swShuffle = SwitchCompat(ctx).apply {
            text = getString(R.string.config_shuffle); textSize = 13f; setTextColor(c(UiTheme.TEXT_SECONDARY))
            setOnCheckedChangeListener { _, on -> if (!updatingUi) saveMusic(music.copy(shuffle = on)) }
        }
        musicCard.addView(swShuffle, lp(top = 0, bottom = 8))

        tvVolume = text("", 12f, UiTheme.TEXT_MUTED)
        musicCard.addView(tvVolume)
        seekVolume = SeekBar(ctx).apply {
            max = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    music = music.copy(volume = MusicConfig.clampVolume(progress))
                    tvVolume.text = volumeLabel()
                    MusicPlayer.setVolume(music.volume)   // đang phát thì nghe đổi ngay
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) { ConfigPrefs.saveMusic(requireContext(), music) }
            })
        }
        musicCard.addView(seekVolume, lp(top = 0))
        col.addView(musicCard)

        // ---- Tier gem ----
        col.addView(sectionTitle(getString(R.string.hub_section_gems)))
        val gemCard = card()
        gemCard.addView(text(getString(R.string.section_gem_tier), 14f, UiTheme.TEXT_PRIMARY, bold = true))
        gemCard.addView(text(getString(R.string.note_gem_tier), 11.5f, UiTheme.TEXT_MUTED))
        gemCard.addView(button(getString(R.string.btn_gem_tier)) {
            GemTierDialog.openFromSaved(requireContext(), viewLifecycleOwner.lifecycleScope) { view != null }
        }, lp(top = 10))
        col.addView(gemCard)

        refreshMusicUi()
        return ScrollView(ctx).apply {
            setBackgroundColor(c(UiTheme.BG)); isFillViewport = true; addView(col)
        }
    }

    override fun onResume() {
        super.onResume()
        MusicPlayer.onError = { msg -> previewing = false; toast(msg, long = true); refreshNowPlaying() }
        MusicPlayer.onSkipped = { msg -> toast(msg, long = true) }
        MusicPlayer.onChanged = { refreshNowPlaying() }
        refreshNowPlaying()
    }

    override fun onPause() {
        super.onPause()
        MusicPlayer.onError = null
        MusicPlayer.onSkipped = null
        MusicPlayer.onChanged = null
        // Rời tab thì tắt bản nghe thử (nhạc nền lúc đang farm thì giữ nguyên)
        if (previewing && !FarmController.isRunning()) MusicPlayer.stop()
        previewing = false
    }

    private var previewing = false

    // ------------------------------------------------------------------ nhạc nền

    private fun onMusicToggled(on: Boolean) {
        if (on && !music.hasTrack) {
            toast(getString(R.string.config_pick_first))
            refreshMusicUi()
            return
        }
        music = music.copy(enabled = on)
        ConfigPrefs.saveMusic(requireContext(), music)
        // Đang farm thì bật/tắt có hiệu lực ngay, khỏi chờ lần farm sau
        if (FarmController.isRunning()) {
            if (on) MusicPlayer.startIfEnabled(requireContext()) else MusicPlayer.stop()
        }
        refreshMusicUi()
    }

    /** Luu playlist moi; dang phat thi ap dung ngay (khong ngat bai), dang farm ma chua phat thi bat dau phat. */
    private fun saveMusic(newCfg: MusicConfig) {
        val ctx = requireContext()
        music = newCfg
        ConfigPrefs.saveMusic(ctx, music)
        if (MusicPlayer.playing) MusicPlayer.refresh(music)
        else if (FarmController.isRunning() && music.shouldPlay) MusicPlayer.startIfEnabled(ctx)
        refreshMusicUi()
    }

    private fun onFilesPicked(uris: List<Uri>) {
        val ctx = requireContext()
        val tracks = uris.map { uri ->
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // Một số trình chọn file không cho giữ quyền lâu dài: vẫn lưu, nếu mất quyền thì lần phát sau sẽ báo lỗi để chọn lại
            }
            Track(uri.toString(), displayName(uri))
        }
        addTracks(tracks)
    }

    private fun addTracks(tracks: List<Track>) {
        val before = music.tracks.size
        val next = music.addTracks(tracks)
        val added = next.tracks.size - before
        if (added <= 0) {
            toast(if (before >= MusicConfig.MAX_TRACKS) getString(R.string.config_playlist_full, MusicConfig.MAX_TRACKS) else getString(R.string.config_added_none))
            return
        }
        toast(getString(R.string.config_added_n, added))
        saveMusic(next)
    }

    /** Hop thoai dan link (nhieu link, moi dong 1 link). Co link trong clipboard thi dien san. */
    private fun showPasteLinkDialog() {
        val ctx = requireContext()
        val input = EditText(ctx).apply {
            hint = getString(R.string.config_paste_link_hint)
            minLines = 4
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        try {
            val clip = (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip
            val clipText = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(ctx)?.toString().orEmpty() else ""
            if (MusicLinks.extract(clipText).isNotEmpty()) input.setText(clipText)
        } catch (e: Exception) {
            // khong doc duoc clipboard thi de trong
        }
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        AlertDialog.Builder(ctx)
            .setTitle(R.string.config_paste_link)
            .setMessage(R.string.config_paste_link_msg)
            .setView(box)
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.config_paste_link_ok) { _, _ ->
                val links = MusicLinks.extract(input.text?.toString().orEmpty())
                if (links.isEmpty()) toast(getString(R.string.config_links_none)) else addTracks(links.map { Track(it) })
            }
            .show()
    }

    private fun onPreviewClicked() {
        if (!music.hasTrack) { toast(getString(R.string.config_pick_first)); return }
        if (previewing) {
            MusicPlayer.stop()
            previewing = false
        } else {
            MusicPlayer.preview(requireContext(), music)
            previewing = true
        }
        refreshNowPlaying()
    }

    private fun refreshMusicUi() {
        if (!::listBox.isInitialized) return
        updatingUi = true
        try {
            swMusic.isChecked = music.enabled && music.hasTrack
            swShuffle.isChecked = music.shuffle
        } finally {
            updatingUi = false
        }
        tvCount.text = if (music.hasTrack) getString(R.string.config_playlist_count, music.tracks.size) else getString(R.string.config_playlist_empty)
        rebuildList()
        seekVolume.progress = music.volume
        tvVolume.text = volumeLabel()
        refreshNowPlaying()
    }

    private fun rebuildList() {
        listBox.removeAllViews()
        music.tracks.forEachIndexed { i, t ->
            val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val name = text("${i + 1}. ${t.displayName}", 13f, UiTheme.TEXT_PRIMARY).apply {
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            }
            row.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(rowAction("↑", i > 0) { saveMusic(music.move(i, -1)) })
            row.addView(rowAction("↓", i < music.tracks.size - 1) { saveMusic(music.move(i, +1)) })
            row.addView(rowAction("✕", true) { saveMusic(music.removeAt(i)) })
            listBox.addView(row)
        }
    }

    /** Nut chu nho o cuoi moi dong bai (len / xuong / xoa), vung cham du rong. */
    private fun rowAction(label: String, enabled: Boolean, onClick: () -> Unit) = text(label, 16f, if (enabled) UiTheme.ACCENT else UiTheme.BAR_IDLE).apply {
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(8), dp(12), dp(8))
        isEnabled = enabled
        setOnClickListener { onClick() }
    }

    private fun refreshNowPlaying() {
        if (!::tvNow.isInitialized || !::tvErr.isInitialized || view == null) return
        val name = MusicPlayer.nowPlaying
        val line = when {
            MusicPlayer.playing && name.isNotEmpty() && MusicPlayer.loading -> getString(R.string.config_now_loading, name)
            MusicPlayer.playing && name.isNotEmpty() -> getString(R.string.config_now_playing, name)
            else -> ""
        }
        tvNow.text = line
        tvNow.visibility = if (line.isEmpty()) View.GONE else View.VISIBLE
        val err = MusicPlayer.lastError
        tvErr.text = err
        tvErr.visibility = if (err.isEmpty()) View.GONE else View.VISIBLE
        if (previewing && !MusicPlayer.playing) previewing = false   // bi dung han (vd tat ca bai loi) -> tra nut ve "Nghe thu"
        btnPreview.text = if (previewing) getString(R.string.config_preview_stop) else getString(R.string.config_preview)
        btnPreview.isEnabled = music.hasTrack
        btnNext.isEnabled = MusicPlayer.playing
    }

    private fun volumeLabel() = getString(R.string.config_volume, music.volume)

    private fun displayName(uri: Uri): String {
        try {
            requireContext().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cur ->
                if (cur.moveToFirst()) {
                    val n = cur.getString(0)
                    if (!n.isNullOrBlank()) return n
                }
            }
        } catch (e: Exception) {
            // rơi xuống tên mặc định
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: getString(R.string.config_track_fallback)
    }

    // ------------------------------------------------------------------ dựng giao diện nhỏ

    private fun toast(msg: String, long: Boolean = false) {
        context?.let { Toast.makeText(it, msg, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show() }
    }

    private fun round(fill: String, line: String): GradientDrawable = GradientDrawable().apply {
        setColor(c(fill)); setStroke(dp(1), c(line)); cornerRadius = dp(12).toFloat()
    }

    private fun text(s: String, size: Float, color: String, bold: Boolean = false) = TextView(requireContext()).apply {
        text = s; textSize = size; setTextColor(c(color)); if (bold) setTypeface(null, Typeface.BOLD)
    }

    private fun sectionTitle(s: String) = text(s, 15f, UiTheme.TEXT_PRIMARY, bold = true).also { it.setPadding(0, dp(18), 0, dp(8)) }

    private fun card(fill: String = UiTheme.SURFACE, line: String = UiTheme.LINE) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL; background = round(fill, line); setPadding(dp(14), dp(10), dp(14), dp(10))
    }

    private fun lp(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).also { it.topMargin = dp(top); it.bottomMargin = dp(bottom) }

    private fun button(label: String, onClick: () -> Unit) = Button(requireContext()).apply {
        text = label; isAllCaps = false; textSize = 13f
        setTextColor(c(UiTheme.ACCENT_ON)); background = round(UiTheme.ACCENT, UiTheme.ACCENT)
        setOnClickListener { onClick() }
    }
}
