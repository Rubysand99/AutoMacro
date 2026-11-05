package com.starclan.starhub

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.starclan.starhub.data.BotNote
import com.starclan.starhub.data.BotSettings
import com.starclan.starhub.data.BotToggle
import com.starclan.starhub.data.HubResponse
import com.starclan.starhub.data.HubResult
import com.starclan.starhub.data.HubSearch
import com.starclan.starhub.data.OwoTrackerApi
import com.starclan.starhub.data.OwoTrackerPrefs
import com.starclan.starhub.view.UiTheme
import kotlinx.coroutines.launch

/**
 * Tab "Công cụ": đưa các lệnh của owo-tracker lên app cho khỏi phải gõ trong Discord.
 *  - Công tắc bot   = ,autocheck ,hbauto ,hbalert ,eventmode ,off + nút báo đã giải captcha (,captcha clear)
 *  - Tra cứu        = ,zoo [tìm] ,pets [tìm] ,inv [tìm] ,myweapons [tìm] (tìm ngay trên dữ liệu Hub đã tải)
 *  - Ghi chú        = ,note add / list / done
 * Giao diện dựng bằng code (không XML) cho dễ chỉnh. Mỗi lần mở tab là 1 Fragment mới nên tự tải lại dữ liệu.
 */
class ToolsFragment : Fragment() {

    private var settings: BotSettings? = null
    private var hub: HubResponse? = null

    private lateinit var tvStatus: TextView
    private lateinit var captchaBox: LinearLayout
    private lateinit var resultsBox: LinearLayout
    private lateinit var notesBox: LinearLayout
    private val switches = LinkedHashMap<String, SwitchCompat>()
    private var updatingSwitches = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun c(hex: String) = Color.parseColor(hex)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        col.addView(text("Công cụ", 22f, UiTheme.TEXT_PRIMARY, bold = true))
        tvStatus = text("", 12f, UiTheme.TEXT_MUTED).also { it.setPadding(0, dp(2), 0, dp(10)) }
        col.addView(tvStatus)

        // ---- Công tắc bot ----
        col.addView(sectionTitle("Công tắc bot"))
        captchaBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        col.addView(captchaBox)
        val card = card()
        addSwitch(card, "auto", "Tự chen lệnh check",
            "Khi app đang farm, bot nhờ app gõ xen owo cash/inv/zoo… cho mục dữ liệu đã cũ (,autocheck)") { on -> toggle(BotToggle.AUTO_CHECK, on) }
        addSwitch(card, "hbauto", "Tự chạy lại huntbot",
            "Huntbot vừa về thì app gõ owo hb, bạn chỉ cần nhắn lại mã (,hbauto)") { on -> toggle(BotToggle.HB_AUTO, on) }
        addSwitch(card, "hbalert", "Nhắc huntbot qua DM",
            "DM acc phụ khi huntbot còn 10 phút — bản dự phòng cho thông báo trên điện thoại (,hbalert)") { on -> toggle(BotToggle.HB_ALERT, on) }
        addSwitch(card, "event", "Chế độ Event",
            "Có sự kiện thì bật để được dùng slot gem thứ 4 (,eventmode)") { on -> setEvent(on) }
        addSwitch(card, "power", "Cho bot giao việc cho app",
            "Tắt = bot ngừng nhờ app thay gem, huntbot, quét vũ khí… và hủy việc đang chờ (,off)") { on -> setPower(on) }
        col.addView(card)

        // ---- Tra cứu ----
        col.addView(sectionTitle("Tra cứu (zoo · dex · kho · vũ khí)"))
        val search = EditText(ctx).apply {
            hint = "Gõ tên pet, vật phẩm, vũ khí, passive… (không cần dấu)"
            setHintTextColor(c(UiTheme.TEXT_MUTED)); setTextColor(c(UiTheme.TEXT_PRIMARY)); textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT; maxLines = 1; imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = round(UiTheme.RAISED, UiTheme.LINE); setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = renderSearch(s?.toString().orEmpty())
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        col.addView(search, lp(top = 0))
        resultsBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        col.addView(resultsBox, lp(top = 6))

        // ---- Ghi chú ----
        col.addView(sectionTitle("Ghi chú"))
        val noteInput = EditText(ctx).apply {
            hint = "Việc cần nhớ…"; setHintTextColor(c(UiTheme.TEXT_MUTED)); setTextColor(c(UiTheme.TEXT_PRIMARY)); textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            background = round(UiTheme.RAISED, UiTheme.LINE); setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val addBtn = button("Thêm") {
            val t = noteInput.text.toString().trim()
            if (t.isEmpty()) return@button
            val s = OwoTrackerPrefs.load(requireContext())
            viewLifecycleOwner.lifecycleScope.launch {
                val list = OwoTrackerApi.addNote(s.apiUrl, s.token, t)
                if (view == null) return@launch
                if (list == null) toast("Không lưu được ghi chú (kiểm tra mạng / cập nhật bot)")
                else { noteInput.setText(""); renderNotes(list) }
            }
        }
        val noteRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        noteRow.addView(noteInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        noteRow.addView(addBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .also { it.leftMargin = dp(8) })
        col.addView(noteRow, lp(top = 0))
        notesBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        col.addView(notesBox, lp(top = 6))

        return ScrollView(ctx).apply {
            setBackgroundColor(c(UiTheme.BG)); isFillViewport = true; addView(col)
        }
    }

    override fun onViewCreated(root: View, savedInstanceState: Bundle?) {
        super.onViewCreated(root, savedInstanceState)
        val s = OwoTrackerPrefs.load(requireContext())
        if (s.apiUrl.isBlank() || s.token.isBlank()) {
            tvStatus.text = "Chưa nhập địa chỉ API / token — vào tab Cài đặt."
            setSwitchesEnabled(false)
            return
        }
        tvStatus.text = "Đang tải…"
        viewLifecycleOwner.lifecycleScope.launch {
            val st = OwoTrackerApi.fetchBotSettings(s.apiUrl, s.token)
            val notes = OwoTrackerApi.fetchNotes(s.apiUrl, s.token)
            val hubRes = OwoTrackerApi.fetchHub(s.apiUrl, s.token)
            if (view == null) return@launch
            settings = st
            if (hubRes is HubResult.Success) hub = hubRes.hub
            applySettings(st)
            if (notes != null) renderNotes(notes) else notesBox.removeAllViews()
            tvStatus.text = when {
                st == null -> "Không lấy được cài đặt từ bot — kiểm tra mạng, token, hoặc cập nhật bot lên bản mới (cần đường /settings)."
                hub == null -> "Đã tải công tắc. Chưa tải được dữ liệu Hub nên chưa tra cứu được."
                else -> "Đã tải xong."
            }
        }
    }

    // ------------------------------------------------------------------ công tắc

    private fun applySettings(st: BotSettings?) {
        setSwitchesEnabled(st != null)
        updatingSwitches = true
        try {
            switches["auto"]?.isChecked = st?.autoCheck ?: false
            switches["hbauto"]?.isChecked = st?.hbAuto ?: false
            switches["hbalert"]?.isChecked = st?.hbAlert ?: false
            switches["event"]?.isChecked = st?.eventMode ?: false
            switches["power"]?.isChecked = st?.appOn ?: false
        } finally {
            updatingSwitches = false
        }
        renderCaptcha(st?.captchaActive == true)
    }

    private fun setSwitchesEnabled(on: Boolean) {
        for (sw in switches.values) sw.isEnabled = on
    }

    /** Gọi bot đổi công tắc; lỗi thì trả công tắc về trạng thái cũ và báo cho Ruby biết. */
    private fun change(key: String, call: suspend (String, String) -> BotSettings?) {
        val s = OwoTrackerPrefs.load(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            val res = call(s.apiUrl, s.token)
            if (view == null) return@launch
            if (res == null) toast("Không đổi được (kiểm tra mạng / token / cập nhật bot)")
            else settings = res
            applySettings(settings)
        }
    }

    private fun toggle(t: BotToggle, on: Boolean) = change(t.key) { u, tk -> OwoTrackerApi.setBotToggle(u, tk, t, on) }

    private fun setEvent(on: Boolean) = change("event") { u, tk ->
        if (OwoTrackerApi.setEventMode(u, tk, on) == null) null else OwoTrackerApi.fetchBotSettings(u, tk)
    }

    private fun setPower(on: Boolean) = change("power") { u, tk ->
        if (!OwoTrackerApi.setPower(u, tk, on)) null else OwoTrackerApi.fetchBotSettings(u, tk)
    }

    private fun renderCaptcha(active: Boolean) {
        captchaBox.removeAllViews()
        captchaBox.visibility = if (active) View.VISIBLE else View.GONE
        if (!active) return
        val box = card(UiTheme.BAD_TINT, UiTheme.BAD_LINE)
        box.addView(text("🚨 OwO đang đòi captcha", 14f, UiTheme.BAD, bold = true))
        box.addView(text("Giải xong trong Discord rồi bấm nút dưới để bot cho app farm tiếp.", 12f, UiTheme.TEXT_SECONDARY))
        box.addView(button("Đã giải captcha xong") {
            val s = OwoTrackerPrefs.load(requireContext())
            viewLifecycleOwner.lifecycleScope.launch {
                val ok = OwoTrackerApi.clearCaptcha(s.apiUrl, s.token)
                if (view == null) return@launch
                if (ok) { settings = settings?.copy(captchaActive = false); renderCaptcha(false); toast("Đã hủy cảnh báo captcha") }
                else toast("Không gửi được tới bot")
            }
        }, lp(top = 8))
        captchaBox.addView(box, lp(top = 0, bottom = 8))
    }

    // ------------------------------------------------------------------ tra cứu

    private fun renderSearch(q: String) {
        resultsBox.removeAllViews()
        if (HubSearch.norm(q).length < 2) return
        if (hub == null) {
            resultsBox.addView(text("Chưa có dữ liệu Hub để tìm — kiểm tra kết nối ở tab Cài đặt rồi mở lại tab này.", 12f, UiTheme.WARN))
            return
        }
        val r = HubSearch.search(hub, q)
        if (r.hits.isEmpty()) {
            resultsBox.addView(text("Không thấy gì khớp “${q.trim()}”.", 12f, UiTheme.TEXT_MUTED))
            return
        }
        var lastKind: String? = null
        var card: LinearLayout? = null
        for (h in r.hits) {
            if (h.kind != lastKind) {
                lastKind?.let { k -> r.hiddenByKind[k]?.let { n -> card?.addView(text("… còn $n kết quả nữa, gõ cụ thể hơn", 11.5f, UiTheme.TEXT_MUTED)) } }
                resultsBox.addView(text(h.kind, 12f, UiTheme.SELECTED, bold = true).also { it.setPadding(0, dp(8), 0, dp(2)) })
                card = card().also { resultsBox.addView(it) }
                lastKind = h.kind
            }
            card?.addView(text(h.title, 13.5f, UiTheme.TEXT_PRIMARY, bold = true))
            if (h.detail.isNotBlank()) card?.addView(text(h.detail, 12f, UiTheme.TEXT_SECONDARY).also { it.setPadding(0, 0, 0, dp(6)) })
        }
        lastKind?.let { k -> r.hiddenByKind[k]?.let { n -> card?.addView(text("… còn $n kết quả nữa, gõ cụ thể hơn", 11.5f, UiTheme.TEXT_MUTED)) } }
    }

    // ------------------------------------------------------------------ ghi chú

    private fun renderNotes(list: List<BotNote>) {
        notesBox.removeAllViews()
        if (list.isEmpty()) {
            notesBox.addView(text("Chưa có ghi chú nào.", 12f, UiTheme.TEXT_MUTED))
            return
        }
        for (n in list) {
            val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val tv = text((if (n.done) "✅ " else "⬜ ") + n.text, 13.5f, if (n.done) UiTheme.TEXT_MUTED else UiTheme.TEXT_PRIMARY)
            row.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (!n.done) row.addView(button("Xong") {
                val s = OwoTrackerPrefs.load(requireContext())
                viewLifecycleOwner.lifecycleScope.launch {
                    val res = OwoTrackerApi.markNoteDone(s.apiUrl, s.token, n.id)
                    if (view == null) return@launch
                    if (res == null) toast("Không cập nhật được ghi chú") else renderNotes(res)
                }
            })
            notesBox.addView(row, lp(top = 0, bottom = 4))
        }
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

    private fun addSwitch(parent: LinearLayout, id: String, title: String, sub: String, onChange: (Boolean) -> Unit) {
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val left = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        left.addView(text(title, 14f, UiTheme.TEXT_PRIMARY, bold = true))
        left.addView(text(sub, 11.5f, UiTheme.TEXT_MUTED))
        row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw = SwitchCompat(requireContext())
        sw.setOnCheckedChangeListener { _, on -> if (!updatingSwitches) onChange(on) }
        switches[id] = sw
        row.addView(sw, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .also { it.leftMargin = dp(10) })
        parent.addView(row, lp(top = 0, bottom = 10))
    }
}
