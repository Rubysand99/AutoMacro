package com.starclan.starhub

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.starclan.starhub.data.ConfigPrefs
import com.starclan.starhub.data.MusicConfig
import com.starclan.starhub.engine.FarmController
import com.starclan.starhub.engine.MusicPlayer
import com.starclan.starhub.view.UiTheme

/**
 * Tab "Config": cac lua chon rieng cua app, luu ngay tren may (khong can bam Luu).
 *  - Nhac nen : bat/tat, chon file nhac, am luong, nghe thu. Nhac tu phat khi bat dau farm va dung khi farm ket thuc.
 *  - Tier gem : mo hop thoai chon tier gem bot duoc phep dung (truoc day nam o tab Cai dat).
 * Giao dien dung bang code (khong XML) giong tab Cong cu.
 */
class ConfigFragment : Fragment() {

    private lateinit var music: MusicConfig
    private lateinit var swMusic: SwitchCompat
    private lateinit var tvTrack: TextView
    private lateinit var btnPreview: Button
    private lateinit var seekVolume: SeekBar
    private lateinit var tvVolume: TextView
    private var updatingUi = false

    private val pickMusic = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onTrackPicked(uri)
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
        col.addView(text("Config", 22f, UiTheme.TEXT_PRIMARY, bold = true))
        col.addView(text("Lựa chọn ở đây được lưu ngay trên máy, không cần bấm Lưu.", 12f, UiTheme.TEXT_MUTED)
            .also { it.setPadding(0, dp(2), 0, dp(6)) })

        // ---- Nhạc nền ----
        col.addView(sectionTitle("Nhạc nền"))
        val musicCard = card()
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        val left = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        left.addView(text("Phát nhạc khi farm", 14f, UiTheme.TEXT_PRIMARY, bold = true))
        left.addView(text("Bắt đầu farm thì nhạc tự phát (lặp lại), dừng farm thì nhạc tự tắt. Chuông báo captcha vẫn kêu bình thường.", 11.5f, UiTheme.TEXT_MUTED))
        row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        swMusic = SwitchCompat(ctx).apply { setOnCheckedChangeListener { _, on -> if (!updatingUi) onMusicToggled(on) } }
        row.addView(swMusic, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .also { it.leftMargin = dp(10) })
        musicCard.addView(row, lp(top = 0, bottom = 10))

        tvTrack = text("", 13f, UiTheme.TEXT_SECONDARY)
        musicCard.addView(tvTrack, lp(top = 0, bottom = 6))
        val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow.addView(button("Chọn file nhạc") { pickMusic.launch(arrayOf("audio/*")) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        btnPreview = button("Nghe thử") { onPreviewClicked() }
        btnRow.addView(btnPreview, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.leftMargin = dp(8) })
        musicCard.addView(btnRow, lp(top = 0, bottom = 8))

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
        col.addView(sectionTitle("Gem"))
        val gemCard = card()
        gemCard.addView(text("Tier gem khi thay", 14f, UiTheme.TEXT_PRIMARY, bold = true))
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
        MusicPlayer.onError = { msg -> previewing = false; toast(msg); refreshPreviewButton() }
        refreshPreviewButton()
    }

    override fun onPause() {
        super.onPause()
        MusicPlayer.onError = null
        // Rời tab thì tắt bản nghe thử (nhạc nền lúc đang farm thì giữ nguyên)
        if (previewing && !FarmController.isRunning()) MusicPlayer.stop()
        previewing = false
    }

    private var previewing = false

    // ------------------------------------------------------------------ nhạc nền

    private fun onMusicToggled(on: Boolean) {
        if (on && !music.hasTrack) {
            toast("Chọn file nhạc trước đã")
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

    private fun onTrackPicked(uri: Uri) {
        val ctx = requireContext()
        try {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            // Một số trình chọn file không cho giữ quyền lâu dài: vẫn lưu, nếu mất quyền thì lần phát sau sẽ báo lỗi để chọn lại
        }
        music = music.copy(uri = uri.toString(), name = displayName(uri))
        ConfigPrefs.saveMusic(ctx, music)
        if (FarmController.isRunning() && music.shouldPlay) MusicPlayer.startIfEnabled(ctx)
        refreshMusicUi()
    }

    private fun onPreviewClicked() {
        if (!music.hasTrack) { toast("Chọn file nhạc trước đã"); return }
        if (previewing) {
            MusicPlayer.stop()
            previewing = false
        } else {
            MusicPlayer.preview(requireContext(), music)
            previewing = true
        }
        refreshPreviewButton()
    }

    private fun refreshMusicUi() {
        updatingUi = true
        try {
            swMusic.isChecked = music.enabled && music.hasTrack
        } finally {
            updatingUi = false
        }
        tvTrack.text = if (music.hasTrack) "Bài đã chọn: " + music.name.ifBlank { "(không rõ tên)" } else "Chưa chọn file nhạc."
        seekVolume.progress = music.volume
        tvVolume.text = volumeLabel()
        refreshPreviewButton()
    }

    private fun refreshPreviewButton() {
        if (!::btnPreview.isInitialized) return
        btnPreview.text = if (previewing) "Dừng nghe thử" else "Nghe thử"
        btnPreview.isEnabled = music.hasTrack
    }

    private fun volumeLabel() = "Âm lượng nhạc nền: ${music.volume}%"

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
        return uri.lastPathSegment?.substringAfterLast('/') ?: "nhạc"
    }

    // ------------------------------------------------------------------ dựng giao diện nhỏ

    private fun toast(msg: String) {
        context?.let { Toast.makeText(it, msg, Toast.LENGTH_SHORT).show() }
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
