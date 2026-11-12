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
import com.starclan.starhub.data.BotAlt
import com.starclan.starhub.data.BotManualResult
import com.starclan.starhub.data.BotNote
import com.starclan.starhub.data.BotSettings
import com.starclan.starhub.data.BotToggle
import com.starclan.starhub.data.BotToolsText
import com.starclan.starhub.data.ConfigPrefs
import com.starclan.starhub.data.HubResponse
import com.starclan.starhub.data.HubResult
import com.starclan.starhub.data.HubSearch
import com.starclan.starhub.data.ManualCheck
import com.starclan.starhub.data.ManualInput
import com.starclan.starhub.data.ManualKind
import com.starclan.starhub.data.OwoTrackerApi
import com.starclan.starhub.data.OwoTrackerPrefs
import com.starclan.starhub.view.UiTheme
import kotlinx.coroutines.launch

/**
 * Tab "Công cụ": đưa các lệnh của owo-tracker lên app cho khỏi phải gõ trong Discord.
 *  - Công tắc bot   = ,autocheck ,hbauto ,hbalert ,eventmode ,off + nút báo đã giải captcha (,captcha clear)
 *  - Acc phụ        = ,setalt ,testalt (acc nhận DM nhắc huntbot: xem / đặt / tắt / thử gửi)
 *  - Đội hình & zoo = ,teamcheck ,zoodiff ,unlocked (bot trả chữ đã làm sạch, app hiện thẳng)
 *  - Nhập tay       = ,petset ,weapontypeset ,passiveset (chọn loại, điền ô, bấm "Lưu vào bot"; luật kiểm tra ở ManualInput)
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
    private lateinit var etSearch: EditText
    private lateinit var notesBox: LinearLayout
    private lateinit var toolResultBox: LinearLayout
    private lateinit var tvAltStatus: TextView
    private lateinit var tvAltResult: TextView
    private lateinit var etAlt: EditText
    // Mục "Nhập tay"
    private var manualKind = ManualKind.PET
    private lateinit var manualKindButtons: List<Button>
    private lateinit var etManualName: EditText
    private lateinit var etManualRank: EditText
    private lateinit var etManualStats: List<EditText>   // theo thứ tự ManualInput.STAT_KEYS: HP ATT PR WP MAG MR
    private lateinit var etManualDesc: EditText
    private lateinit var manualPetBox: LinearLayout
    private lateinit var tvManualResult: TextView
    private val switches = LinkedHashMap<String, SwitchCompat>()
    private var updatingSwitches = false
    private var busy = false   // đang chờ bot trả lời 1 nút ở mục Acc phụ / Đội hình & zoo: chặn bấm liên tiếp (đỡ DM thử nhiều lần)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun c(hex: String) = Color.parseColor(hex)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        col.addView(text(getString(R.string.tab_tools), 22f, UiTheme.TEXT_PRIMARY, bold = true))
        tvStatus = text("", 12f, UiTheme.TEXT_MUTED).also { it.setPadding(0, dp(2), 0, dp(10)) }
        col.addView(tvStatus)

        // ---- Công tắc bot ----
        col.addView(sectionTitle(getString(R.string.tools_section_switches)))
        captchaBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        col.addView(captchaBox)
        val card = card()
        addSwitch(card, "auto", getString(R.string.tools_sw_auto_title),
            getString(R.string.tools_sw_auto_sub)) { on -> toggle(BotToggle.AUTO_CHECK, on) }
        addSwitch(card, "hbauto", getString(R.string.tools_sw_hbauto_title),
            getString(R.string.tools_sw_hbauto_sub)) { on -> toggle(BotToggle.HB_AUTO, on) }
        addSwitch(card, "hbalert", getString(R.string.tools_sw_hbalert_title),
            getString(R.string.tools_sw_hbalert_sub)) { on -> toggle(BotToggle.HB_ALERT, on) }
        addSwitch(card, "event", getString(R.string.tools_sw_event_title),
            getString(R.string.tools_sw_event_sub)) { on -> setEvent(on) }
        addSwitch(card, "power", getString(R.string.tools_sw_power_title),
            getString(R.string.tools_sw_power_sub)) { on -> setPower(on) }
        col.addView(card)

        // ---- Acc phụ (DM nhắc huntbot) ----
        col.addView(sectionTitle(getString(R.string.tools_section_alt)))
        val altCard = card()
        tvAltStatus = text(getString(R.string.tools_loading), 12.5f, UiTheme.TEXT_SECONDARY)
        altCard.addView(tvAltStatus)
        etAlt = EditText(ctx).apply {
            hint = getString(R.string.tools_alt_hint)
            setHintTextColor(c(UiTheme.TEXT_MUTED)); setTextColor(c(UiTheme.TEXT_PRIMARY)); textSize = 14f
            inputType = InputType.TYPE_CLASS_NUMBER; maxLines = 1
            background = round(UiTheme.RAISED, UiTheme.LINE); setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        altCard.addView(etAlt, lp(top = 8))
        val altRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        altRow.addView(button(getString(R.string.btn_save)) { saveAlt() }, weightLp())
        altRow.addView(secondaryButton(getString(R.string.tools_alt_off)) { sendAlt("") }, weightLp(left = 8))
        altRow.addView(secondaryButton(getString(R.string.tools_alt_test)) { testAlt() }, weightLp(left = 8))
        altCard.addView(altRow, lp(top = 8))
        tvAltResult = text("", 12f, UiTheme.TEXT_SECONDARY).also { it.visibility = View.GONE; it.setPadding(0, dp(8), 0, 0) }
        altCard.addView(tvAltResult)
        col.addView(altCard)

        // ---- Đội hình & zoo ----
        col.addView(sectionTitle(getString(R.string.tools_section_team_zoo)))
        val toolRow1 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        toolRow1.addView(secondaryButton(getString(R.string.tools_btn_teamcheck)) {
            runTool(getString(R.string.tools_btn_teamcheck)) { u, t -> OwoTrackerApi.fetchTeamCheck(u, t)?.text }
        }, weightLp())
        toolRow1.addView(secondaryButton(getString(R.string.tools_btn_unlocked)) {
            runTool(getString(R.string.tools_btn_unlocked)) { u, t -> OwoTrackerApi.fetchUnlocked(u, t)?.let { BotToolsText.unlockedText(it) } }
        }, weightLp(left = 8))
        val toolRow2 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        toolRow2.addView(secondaryButton(getString(R.string.tools_btn_zoodiff_now)) {
            runTool(getString(R.string.tools_btn_zoodiff_now)) { u, t -> OwoTrackerApi.fetchZooDiff(u, t, "now")?.text }
        }, weightLp())
        toolRow2.addView(secondaryButton(getString(R.string.tools_btn_zoodiff_all)) {
            runTool(getString(R.string.tools_btn_zoodiff_all)) { u, t -> OwoTrackerApi.fetchZooDiff(u, t, "d")?.text }
        }, weightLp(left = 8))
        col.addView(toolRow1, lp(top = 0))
        col.addView(toolRow2, lp(top = 8))
        toolResultBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        col.addView(toolResultBox, lp(top = 8))

        // ---- Nhập tay (pet / loại vũ khí / passive) ----
        col.addView(sectionTitle(getString(R.string.tools_manual_title)))
        val manualCard = card()
        manualCard.addView(text(getString(R.string.tools_manual_note), 11.5f, UiTheme.TEXT_MUTED))
        val kindRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        manualKindButtons = ManualKind.values().mapIndexed { i, k ->
            secondaryButton(manualKindLabel(k)) { selectManualKind(k) }.also { kindRow.addView(it, weightLp(left = if (i == 0) 0 else 8)) }
        }
        manualCard.addView(kindRow, lp(top = 8))
        etManualName = inputField("", InputType.TYPE_CLASS_TEXT)
        manualCard.addView(etManualName, lp(top = 8))
        manualPetBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        etManualRank = inputField(getString(R.string.tools_manual_hint_rank), InputType.TYPE_CLASS_TEXT)
        manualPetBox.addView(etManualRank, lp(top = 0))
        etManualStats = ManualInput.STAT_KEYS.map { inputField(it.uppercase(), InputType.TYPE_CLASS_NUMBER) }
        for (r in 0..1) {   // 6 chỉ số chia 2 hàng x 3 ô
            val statRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in 0..2) statRow.addView(etManualStats[r * 3 + i], weightLp(left = if (i == 0) 0 else 8))
            manualPetBox.addView(statRow, lp(top = 8))
        }
        manualCard.addView(manualPetBox, lp(top = 8))
        etManualDesc = inputField(getString(R.string.tools_manual_hint_desc), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE).apply {
            minLines = 3; gravity = Gravity.TOP or Gravity.START
        }
        manualCard.addView(etManualDesc, lp(top = 8))
        manualCard.addView(button(getString(R.string.tools_manual_save)) { saveManual() }, lp(top = 8))
        tvManualResult = text("", 12f, UiTheme.TEXT_SECONDARY).also { it.visibility = View.GONE; it.setPadding(0, dp(8), 0, 0) }
        manualCard.addView(tvManualResult)
        col.addView(manualCard)
        selectManualKind(ManualKind.PET)

        // ---- Tra cứu ----
        col.addView(sectionTitle(getString(R.string.tools_section_search)))
        val search = EditText(ctx).apply {
            hint = getString(R.string.tools_search_hint)
            setHintTextColor(c(UiTheme.TEXT_MUTED)); setTextColor(c(UiTheme.TEXT_PRIMARY)); textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT; maxLines = 1; imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = round(UiTheme.RAISED, UiTheme.LINE); setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        etSearch = search
        search.setText(ConfigPrefs.loadToolsSearch(ctx))   // nhớ chữ đã tìm lần trước
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString().orEmpty()
                ConfigPrefs.saveToolsSearch(requireContext(), q)
                renderSearch(q)
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        col.addView(search, lp(top = 0))
        resultsBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        col.addView(resultsBox, lp(top = 6))

        // ---- Ghi chú ----
        col.addView(sectionTitle(getString(R.string.tools_section_notes)))
        val noteInput = EditText(ctx).apply {
            hint = getString(R.string.tools_note_hint); setHintTextColor(c(UiTheme.TEXT_MUTED)); setTextColor(c(UiTheme.TEXT_PRIMARY)); textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            background = round(UiTheme.RAISED, UiTheme.LINE); setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val addBtn = button(getString(R.string.tools_note_add)) {
            val t = noteInput.text.toString().trim()
            if (t.isEmpty()) return@button
            val s = OwoTrackerPrefs.load(requireContext())
            viewLifecycleOwner.lifecycleScope.launch {
                val list = OwoTrackerApi.addNote(s.apiUrl, s.token, t)
                if (view == null) return@launch
                if (list == null) toast(getString(R.string.tools_note_save_failed))
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
            tvStatus.text = getString(R.string.tools_need_creds)
            tvAltStatus.text = getString(R.string.tools_need_creds)
            setSwitchesEnabled(false)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val alt = OwoTrackerApi.fetchAlt(s.apiUrl, s.token)
            if (view == null) return@launch
            renderAlt(alt)
        }
        // Hiện ngay các công tắc đã lưu lần trước, rồi mới hỏi bot xem có đổi không
        ConfigPrefs.loadToolsSettings(requireContext())?.let { cached ->
            settings = cached
            applySettings(cached)
        }
        tvStatus.text = getString(R.string.tools_loading)
        viewLifecycleOwner.lifecycleScope.launch {
            val st = OwoTrackerApi.fetchBotSettings(s.apiUrl, s.token)
            val notes = OwoTrackerApi.fetchNotes(s.apiUrl, s.token)
            val hubRes = OwoTrackerApi.fetchHub(s.apiUrl, s.token)
            if (view == null) return@launch
            if (st != null) settings = st            // không lấy được từ bot thì giữ bản đã lưu
            if (hubRes is HubResult.Success) hub = hubRes.hub
            applySettings(settings)
            renderSearch(etSearch.text.toString())
            if (notes != null) renderNotes(notes) else notesBox.removeAllViews()
            tvStatus.text = when {
                st == null && settings != null -> getString(R.string.tools_status_cached)
                st == null -> getString(R.string.tools_status_failed)
                hub == null -> getString(R.string.tools_status_no_hub)
                else -> getString(R.string.tools_status_done)
            }
        }
    }

    // ------------------------------------------------------------------ công tắc

    private fun applySettings(st: BotSettings?) {
        if (st != null) ConfigPrefs.saveToolsSettings(requireContext(), st)   // nhớ để lần sau mở tab thấy ngay
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
            if (res == null) toast(getString(R.string.tools_switch_failed))
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
        box.addView(text(getString(R.string.tools_captcha_title), 14f, UiTheme.BAD, bold = true))
        box.addView(text(getString(R.string.tools_captcha_hint), 12f, UiTheme.TEXT_SECONDARY))
        box.addView(button(getString(R.string.tools_captcha_done_btn)) {
            val s = OwoTrackerPrefs.load(requireContext())
            viewLifecycleOwner.lifecycleScope.launch {
                val ok = OwoTrackerApi.clearCaptcha(s.apiUrl, s.token)
                if (view == null) return@launch
                if (ok) { settings = settings?.copy(captchaActive = false); renderCaptcha(false); toast(getString(R.string.tools_captcha_cleared)) }
                else toast(getString(R.string.tools_captcha_send_failed))
            }
        }, lp(top = 8))
        captchaBox.addView(box, lp(top = 0, bottom = 8))
    }

    // ------------------------------------------------------------------ acc phụ + đội hình & zoo

    /** Địa chỉ API + token đã lưu; chưa nhập thì báo cho người dùng và trả null. */
    private fun credsOrToast() = OwoTrackerPrefs.load(requireContext())
        .takeIf { it.apiUrl.isNotBlank() && it.token.isNotBlank() }
        ?: run { toast(getString(R.string.tools_need_creds)); null }

    private fun renderAlt(alt: BotAlt?) {
        tvAltStatus.text = BotToolsText.altStatus(alt)
        if (alt != null) etAlt.setText(alt.userId)   // rỗng (đã tắt) thì xóa ô nhập
    }

    /** Nút Lưu: chỉ nhận dãy 15–25 chữ số (ô trống KHÔNG tắt acc phụ — muốn tắt thì bấm "Tắt DM"). */
    private fun saveAlt() {
        val id = etAlt.text.toString().trim()
        if (!BotToolsText.isValidAltId(id)) { toast(getString(R.string.tools_alt_invalid)); return }
        sendAlt(id)
    }

    /** Gửi ID lên bot (như ,setalt <id>); id rỗng = tắt (như ,setalt xoa). */
    private fun sendAlt(id: String) {
        val s = credsOrToast() ?: return
        if (busy) return
        busy = true
        viewLifecycleOwner.lifecycleScope.launch {
            val res = try { OwoTrackerApi.setAlt(s.apiUrl, s.token, id) } finally { busy = false }
            if (view == null) return@launch
            if (res == null) toast(getString(R.string.tools_alt_save_failed))
            else {
                renderAlt(res)
                toast(if (res.userId.isBlank()) getString(R.string.tools_alt_turned_off) else getString(R.string.tools_alt_saved))
            }
        }
    }

    private fun testAlt() {
        val s = credsOrToast() ?: return
        if (busy) return
        busy = true
        tvAltResult.visibility = View.VISIBLE
        tvAltResult.text = getString(R.string.tools_sending)
        viewLifecycleOwner.lifecycleScope.launch {
            val res = try { OwoTrackerApi.testAlt(s.apiUrl, s.token) } finally { busy = false }
            if (view == null) return@launch
            tvAltResult.text = BotToolsText.altTestMessage(res)
        }
    }

    /** Gọi bot lấy 1 đoạn chữ rồi hiện trong khung kết quả. fetch trả null = lỗi mạng / sai token / bot chưa cập nhật. */
    private fun runTool(title: String, fetch: suspend (String, String) -> String?) {
        val s = credsOrToast() ?: return
        if (busy) return
        busy = true
        showToolResult(title, getString(R.string.tools_loading))
        viewLifecycleOwner.lifecycleScope.launch {
            val body = try { fetch(s.apiUrl, s.token) } finally { busy = false }
            if (view == null) return@launch
            showToolResult(title, body ?: getString(R.string.tools_fetch_failed))
        }
    }

    private fun showToolResult(title: String, body: String) {
        toolResultBox.removeAllViews()
        val box = card()
        box.addView(text(title, 13f, UiTheme.SELECTED, bold = true))
        box.addView(text(body, 12.5f, UiTheme.TEXT_PRIMARY).also {
            it.setTextIsSelectable(true)   // cho bôi đen / sao chép (vd chép ID, tên pet)
            it.setPadding(0, dp(4), 0, 0)
        })
        toolResultBox.addView(box)
    }

    // ------------------------------------------------------------------ nhập tay (pet / loại vũ khí / passive)

    private fun manualKindLabel(k: ManualKind) = getString(when (k) {
        ManualKind.PET -> R.string.tools_manual_kind_pet
        ManualKind.WEAPON_TYPE -> R.string.tools_manual_kind_weapon_type
        ManualKind.PASSIVE -> R.string.tools_manual_kind_passive
    })

    private fun manualNameHint(k: ManualKind) = getString(when (k) {
        ManualKind.PET -> R.string.tools_manual_hint_name_pet
        ManualKind.WEAPON_TYPE -> R.string.tools_manual_hint_name_weapon_type
        ManualKind.PASSIVE -> R.string.tools_manual_hint_name_passive
    })

    /** Chọn loại cần nhập: pet có rank + 6 chỉ số, 2 loại còn lại chỉ có tên + mô tả. Nút đang chọn viền tím (UiTheme.SELECTED). */
    private fun selectManualKind(k: ManualKind) {
        manualKind = k
        ManualKind.values().forEachIndexed { i, kind ->
            val on = kind == k
            manualKindButtons[i].background = round(UiTheme.RAISED, if (on) UiTheme.SELECTED else UiTheme.LINE)
            manualKindButtons[i].setTextColor(c(if (on) UiTheme.SELECTED else UiTheme.TEXT_PRIMARY))
        }
        etManualName.hint = manualNameHint(k)
        manualPetBox.visibility = if (k == ManualKind.PET) View.VISIBLE else View.GONE
        etManualDesc.visibility = if (k == ManualKind.PET) View.GONE else View.VISIBLE
        tvManualResult.visibility = View.GONE
    }

    private fun showManualResult(msg: String) {
        tvManualResult.text = msg
        tvManualResult.visibility = View.VISIBLE
    }

    private fun clearManualInputs() {
        etManualName.setText(""); etManualRank.setText(""); etManualDesc.setText("")
        etManualStats.forEach { it.setText("") }
    }

    /** Kiểm tra ô nhập ngay trên app (cùng luật với bot, xem ManualInput) rồi gửi lên bot; lưu được thì xóa các ô. */
    private fun saveManual() {
        val s = credsOrToast() ?: return
        if (busy) return
        val name = etManualName.text.toString()
        val call: suspend () -> BotManualResult?
        if (manualKind == ManualKind.PET) {
            val check = ManualInput.pet(name, etManualRank.text.toString(), etManualStats.map { it.text.toString() })
            val pet = when (check) {
                is ManualCheck.Bad -> { showManualResult(BotToolsText.manualFieldError(check.field)); return }
                is ManualCheck.Ok -> check.value
            }
            call = { OwoTrackerApi.saveManualPet(s.apiUrl, s.token, pet) }
        } else {
            val kind = manualKind
            val check = ManualInput.desc(name, etManualDesc.text.toString())
            val desc = when (check) {
                is ManualCheck.Bad -> { showManualResult(BotToolsText.manualFieldError(check.field)); return }
                is ManualCheck.Ok -> check.value
            }
            call = { OwoTrackerApi.saveManualDesc(s.apiUrl, s.token, kind, desc) }
        }
        busy = true
        showManualResult(getString(R.string.tools_manual_saving))
        viewLifecycleOwner.lifecycleScope.launch {
            val res = try { call() } finally { busy = false }
            if (view == null) return@launch
            showManualResult(BotToolsText.manualSavedMessage(res))
            if (res?.ok == true) clearManualInputs()
        }
    }

    // ------------------------------------------------------------------ tra cứu

    private fun renderSearch(q: String) {
        resultsBox.removeAllViews()
        if (HubSearch.norm(q).length < 2) return
        if (hub == null) {
            resultsBox.addView(text(getString(R.string.tools_search_no_hub), 12f, UiTheme.WARN))
            return
        }
        val r = HubSearch.search(hub, q)
        if (r.hits.isEmpty()) {
            resultsBox.addView(text(getString(R.string.tools_search_empty, q.trim()), 12f, UiTheme.TEXT_MUTED))
            return
        }
        var lastKind: String? = null
        var card: LinearLayout? = null
        for (h in r.hits) {
            if (h.kind != lastKind) {
                lastKind?.let { k -> r.hiddenByKind[k]?.let { n -> card?.addView(text(getString(R.string.tools_search_more, n), 11.5f, UiTheme.TEXT_MUTED)) } }
                resultsBox.addView(text(h.kind, 12f, UiTheme.SELECTED, bold = true).also { it.setPadding(0, dp(8), 0, dp(2)) })
                card = card().also { resultsBox.addView(it) }
                lastKind = h.kind
            }
            card?.addView(text(h.title, 13.5f, UiTheme.TEXT_PRIMARY, bold = true))
            if (h.detail.isNotBlank()) card?.addView(text(h.detail, 12f, UiTheme.TEXT_SECONDARY).also { it.setPadding(0, 0, 0, dp(6)) })
        }
        lastKind?.let { k -> r.hiddenByKind[k]?.let { n -> card?.addView(text(getString(R.string.tools_search_more, n), 11.5f, UiTheme.TEXT_MUTED)) } }
    }

    // ------------------------------------------------------------------ ghi chú

    private fun renderNotes(list: List<BotNote>) {
        notesBox.removeAllViews()
        if (list.isEmpty()) {
            notesBox.addView(text(getString(R.string.tools_notes_empty), 12f, UiTheme.TEXT_MUTED))
            return
        }
        for (n in list) {
            val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val tv = text((if (n.done) "✅ " else "⬜ ") + n.text, 13.5f, if (n.done) UiTheme.TEXT_MUTED else UiTheme.TEXT_PRIMARY)
            row.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (!n.done) row.addView(button(getString(R.string.tools_note_done)) {
                val s = OwoTrackerPrefs.load(requireContext())
                viewLifecycleOwner.lifecycleScope.launch {
                    val res = OwoTrackerApi.markNoteDone(s.apiUrl, s.token, n.id)
                    if (view == null) return@launch
                    if (res == null) toast(getString(R.string.tools_note_update_failed)) else renderNotes(res)
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

    /** Ô nhập 1 dòng / nhiều dòng theo kiểu chung của tab (nền nhạt, viền, chữ sáng). */
    private fun inputField(hint: String, type: Int) = EditText(requireContext()).apply {
        this.hint = hint; this.inputType = type
        setHintTextColor(c(UiTheme.TEXT_MUTED)); setTextColor(c(UiTheme.TEXT_PRIMARY)); textSize = 14f
        background = round(UiTheme.RAISED, UiTheme.LINE); setPadding(dp(12), dp(10), dp(12), dp(10))
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

    /** Nút phụ (nền nhạt, viền): dùng cho các nút không phải việc chính của màn hình. */
    private fun secondaryButton(label: String, onClick: () -> Unit) = Button(requireContext()).apply {
        text = label; isAllCaps = false; textSize = 13f
        setTextColor(c(UiTheme.TEXT_PRIMARY)); background = round(UiTheme.RAISED, UiTheme.LINE)
        setOnClickListener { onClick() }
    }

    /** Tham số cho nút nằm trong hàng ngang, chia đều chiều rộng. */
    private fun weightLp(left: Int = 0) = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        .also { it.leftMargin = dp(left) }

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
