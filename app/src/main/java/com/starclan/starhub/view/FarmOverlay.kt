package com.starclan.starhub.view

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.starclan.starhub.R
import com.starclan.starhub.data.FarmCmd
import com.starclan.starhub.data.FarmCmdGroup
import com.starclan.starhub.data.FarmCommandConfig
import com.starclan.starhub.data.HubFormat
import com.starclan.starhub.data.SpeedMode
import com.starclan.starhub.data.StatsFormat
import com.starclan.starhub.engine.FarmController
import com.starclan.starhub.engine.FarmState
import com.starclan.starhub.engine.TagCollector
import com.starclan.starhub.service.AutoAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

/**
 * Hub noi tren man hinh Discord. Dung cua so overlay cua dich vu Accessibility nen
 * KHONG can them quyen "Hien thi tren ung dung khac". Bong bong ngoi sao: cham de mo/thu gon,
 * keo de di chuyen.
 *
 * Bo cuc: phan dau co dinh (trang thai, dem nguoc, canh bao, nut Bat dau/Dung + ➖ + ✖) luon hien o moi tab,
 * roi thanh 4 TAB:
 *   Farm    = nhom Farm, Gem, Huntbot
 *   Setting = nut Hunt mode + bat/tat tu thu gon menu
 *   Misc    = nut hoc loi khuyen vu khi + nhom Tai nguyen, Nhiem vu, Zoo & pet moi
 *   Event   = nut Event + nhom Su kien
 * Cham tieu de nhom de thu gon / mo rong; tieu de luon kem 1 dong tom tat nen thu gon van doc duoc nhanh.
 */
object FarmOverlay {
    private const val REFRESH_MS = 15_000L
    private const val AUTO_COLLAPSE_MS = 8_000L

    // 4 tab cua menu: chi la cach sap xep giao dien, khong doi logic farm
    private const val TAB_FARM = 0
    private const val TAB_SETTING = 1
    private const val TAB_MISC = 2
    private const val TAB_EVENT = 3
    private val TAB_LABELS = listOf("Farm", "Setting", "Misc", "Event")
    private val TAB_ICONS = listOf(R.drawable.ic_bolt, R.drawable.ic_sliders, R.drawable.ic_grid, R.drawable.ic_star)

    // Nho tab dang chon + cai dat tu thu gon giua cac lan bat Hub (SharedPreferences rieng, khong chua token)
    private const val PREFS = "starhub_overlay"
    private const val PREF_TAB = "tab"
    private const val PREF_AUTO_COLLAPSE = "auto_collapse"
    private const val STATS_CLEAR_LABEL = "Xóa thống kê"
    private var clearStatsConfirmUntilMs = 0L

    // Mau lay tu UiTheme (cung he mau voi man hinh XML cua app). C_PANEL = SURFACE + do trong suot F2.
    private const val C_PANEL = "#F212151C"
    private const val C_CARD = UiTheme.RAISED
    private const val C_TITLE = UiTheme.TEXT_PRIMARY
    private const val C_TEXT = UiTheme.TEXT_PRIMARY
    private const val C_LABEL = UiTheme.TEXT_SECONDARY
    private const val C_MUTED = UiTheme.TEXT_MUTED
    private const val C_GOOD = UiTheme.OK
    private const val C_WARN = UiTheme.WARN
    private const val C_OFF = UiTheme.TEXT_MUTED

    /** Kieu nut: PRIMARY = vang (duy nhat 1 nut chinh), DANGER = do nhat, ACTIVE = tim nhat (dang bat), SECONDARY = nen noi */
    private enum class Btn { PRIMARY, DANGER, ACTIVE, SECONDARY, GOOD }

    private enum class Tone { NORMAL, GOOD, WARN, MUTED }

    /** 1 dong: nhan (cot trai, co the de trong) + gia tri */
    private class Row(val label: String, val value: String, val tone: Tone = Tone.NORMAL, val build: ((Context) -> View)? = null)

    private class Group(
        val card: LinearLayout,
        val body: LinearLayout,
        val summary: TextView,
        val chevron: TextView
    )

    /** ScrollView cao toi da maxH, thap hon thi co theo noi dung (khong chua khoang trong thua) */
    private class MaxHeightScrollView(ctx: Context, private val maxH: Int) : ScrollView(ctx) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST))
        }
    }

    /** LinearLayout bao cho noi ngoai moi khi co ngon tay cham xuong (ke ca cham len nut con), de tinh lai gio tu thu gon */
    private class TouchAwareLinearLayout(ctx: Context, private val onTouchDown: () -> Unit) : LinearLayout(ctx) {
        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) onTouchDown()
            return super.dispatchTouchEvent(ev)
        }
    }

    private var root: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var windowManager: WindowManager? = null
    private var panel: LinearLayout? = null
    private var viewCtx: Context? = null
    private var density = 1f

    private var tvDot: TextView? = null
    private var tvStatusTitle: TextView? = null
    private var tvStatusCounts: TextView? = null
    private var tvHuntBig: TextView? = null
    private var tvBattleBig: TextView? = null
    private var bubbleBg: GradientDrawable? = null
    private var bubbleIcon: ImageView? = null
    private var reviewRow: LinearLayout? = null
    private var tvReviewInfo: TextView? = null
    private var tvStatusDetail: TextView? = null
    private var tvAlert: TextView? = null
    private var waitView: WaitCountdownView? = null
    private var btnToggle: TextView? = null
    private var btnMute: TextView? = null
    private var btnMode: TextView? = null
    private var btnEvent: TextView? = null
    private var btnTags: TextView? = null
    private var btnAuto: TextView? = null

    private val tabButtons = mutableListOf<TextView>()
    private val tabContainers = mutableListOf<LinearLayout>()
    private val speedButtons = mutableListOf<TextView>()
    private val cmdButtons = mutableMapOf<FarmCmd, TextView>()
    private val presetButtons = mutableListOf<Pair<TextView, FarmCommandConfig>>()
    private var currentTab = TAB_FARM
    private var autoCollapse = true

    private val groups = mutableMapOf<String, Group>()
    private val collapsed = mutableSetOf<String>()   // nho trang thai thu gon trong luc app chay

    private var scope: CoroutineScope? = null
    private var refreshJob: Job? = null
    private var idleJob: Job? = null
    private var lastExpandedAlert: String? = null   // canh bao da lam menu tu mo (chi mo lai khi canh bao doi)

    fun isShown(): Boolean = root != null

    private fun dp(v: Int): Int = (v * density).toInt()

    fun hide() {
        // Tat Hub noi = DUNG MOI HOAT DONG TU DONG cua app NGAY LAP TUC (farm, tu hoc tag, lenh tu bot).
        // Cac vong lap khac cung tu dung vi kiem tra isShown() truoc moi viec.
        val removedRoot = root
        val ctxForPower = viewCtx
        root = null   // tu day isShown() = false -> moi noi khac dung lai
        waitView?.let { it.onTick = null; it.clear() }
        waitView = null
        FarmController.stop()
        if (TagCollector.isRunning()) TagCollector.stop()
        refreshJob?.cancel()
        refreshJob = null
        idleJob?.cancel()
        idleJob = null
        lastExpandedAlert = null
        scope?.cancel()
        scope = null
        val r = removedRoot
        val wm = windowManager
        if (r != null && wm != null) {
            try {
                wm.removeView(r)
            } catch (e: Exception) {
                // da bi go roi
            }
        }
        params = null
        panel = null
        windowManager = null
        viewCtx = null
        groups.clear()
        tabButtons.clear()
        tabContainers.clear()
        speedButtons.clear()
        btnAuto = null
        tvHuntBig = null
        tvBattleBig = null
        bubbleBg = null
        bubbleIcon = null
        reviewRow = null
        tvReviewInfo = null
        // Hub TAT = bot OFF (khong giao viec cho app nua)
        if (ctxForPower != null) FarmController.reportHubPower(ctxForPower, false)
    }

    // ------------------------------------------------------------------ dung giao dien

    private fun iconDrawable(ctx: Context, res: Int, sizePx: Int, color: String = UiTheme.TEXT_PRIMARY): Drawable? {
        val d = ctx.getDrawable(res)?.mutate() ?: return null
        d.setBounds(0, 0, sizePx, sizePx)
        d.setTint(Color.parseColor(color))
        return d
    }

    private fun roundBg(color: String, radiusDp: Int, strokeColor: String? = null): GradientDrawable {
        val d = GradientDrawable()
        d.cornerRadius = dp(radiusDp).toFloat()
        d.setColor(Color.parseColor(color))
        if (strokeColor != null) d.setStroke(dp(1), Color.parseColor(strokeColor))
        return d
    }

    /** To nen + vien + mau chu (va icon kem theo) cua 1 nut theo kieu Btn */
    private fun styleBtn(v: View?, kind: Btn) {
        if (v == null) return
        val bg: String
        val line: String
        val fg: String
        when (kind) {
            Btn.PRIMARY -> { bg = UiTheme.ACCENT; line = UiTheme.ACCENT; fg = UiTheme.ACCENT_ON }
            Btn.DANGER -> { bg = UiTheme.BAD_TINT; line = UiTheme.BAD_LINE; fg = UiTheme.BAD }
            Btn.ACTIVE -> { bg = UiTheme.SELECTED_TINT; line = UiTheme.SELECTED; fg = UiTheme.SELECTED }
            Btn.SECONDARY -> { bg = UiTheme.RAISED; line = UiTheme.LINE; fg = UiTheme.TEXT_PRIMARY }
            Btn.GOOD -> { bg = UiTheme.OK_TINT; line = UiTheme.OK; fg = UiTheme.OK }
        }
        v.background = roundBg(bg, 12, line)
        if (v is TextView) {
            v.setTextColor(Color.parseColor(fg))
            for (dr in v.compoundDrawables) dr?.setTint(Color.parseColor(fg))
        }
        if (v is ImageView) v.drawable?.setTint(Color.parseColor(fg))
    }

    /** Doi chu + icon + kieu nut cung luc (nut Bat dau / Dung) */
    private fun setBtnState(t: TextView?, text: String, kind: Btn, icon: Int?) {
        if (t == null) return
        t.text = text
        val ctx = viewCtx
        if (ctx != null && icon != null) t.setCompoundDrawables(iconDrawable(ctx, icon, dp(18)), null, null, null)
        styleBtn(t, kind)
    }

    private fun pill(ctx: Context, text: String, kind: Btn = Btn.SECONDARY, size: Float = 13f, icon: Int? = null): TextView {
        val t = TextView(ctx)
        t.text = text
        t.textSize = size
        t.setTypeface(null, Typeface.BOLD)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(10), dp(8), dp(10), dp(8))
        t.minHeight = dp(44)
        t.maxLines = 2
        if (icon != null) {
            t.setCompoundDrawables(iconDrawable(ctx, icon, dp(18)), null, null, null)
            t.compoundDrawablePadding = dp(8)
        }
        t.isClickable = true
        styleBtn(t, kind)
        return t
    }

    /** Nut chi co icon (thu gon, tat Hub): 44x44dp */
    private fun iconButton(ctx: Context, res: Int): ImageView {
        val v = ImageView(ctx)
        v.setImageDrawable(iconDrawable(ctx, res, dp(20)))
        v.scaleType = ImageView.ScaleType.CENTER
        v.isClickable = true
        styleBtn(v, Btn.SECONDARY)
        return v
    }

    /** Nut tab: icon tren, chu duoi. Dang chon = nen noi + tim; khong chon = trong suot + xam mo */
    private fun styleTab(t: TextView, selected: Boolean) {
        val fg = if (selected) UiTheme.SELECTED else UiTheme.TEXT_MUTED
        t.background = if (selected) roundBg(UiTheme.RAISED, 10, UiTheme.LINE) else null
        t.setTextColor(Color.parseColor(fg))
        for (dr in t.compoundDrawables) dr?.setTint(Color.parseColor(fg))
    }

    private fun setBubbleState(color: String) {
        bubbleBg?.setStroke(dp(2), Color.parseColor(color))
        bubbleIcon?.drawable?.setTint(Color.parseColor(color))
    }

    private fun caption(ctx: Context, text: String, color: String = C_LABEL): TextView {
        val t = TextView(ctx)
        t.text = text
        t.textSize = 11.5f
        t.setTextColor(Color.parseColor(color))
        return t
    }

    /** Hang cac o so lon (so 26 dam + nhan nho). Tra ve ca cac TextView so de cap nhat sau. */
    private fun tileRow(ctx: Context, items: List<Triple<String, String, String>>): Pair<LinearLayout, List<TextView>> {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        val numbers = mutableListOf<TextView>()
        for ((i, item) in items.withIndex()) {
            val tile = LinearLayout(ctx)
            tile.orientation = LinearLayout.VERTICAL
            tile.setPadding(dp(10), dp(8), dp(10), dp(8))
            tile.background = roundBg(UiTheme.BG, 12, UiTheme.LINE)
            val n = TextView(ctx)
            n.text = item.second
            n.textSize = 26f
            n.setTypeface(null, Typeface.BOLD)
            n.setTextColor(Color.parseColor(item.third))
            n.maxLines = 1
            tile.addView(n, lparams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            tile.addView(caption(ctx, item.first), lparams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            numbers.add(n)
            val lp = lparams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight = 1f)
            lp.setMargins(if (i == 0) 0 else dp(3), 0, if (i == items.size - 1) 0 else dp(3), 0)
            row.addView(tile, lp)
        }
        return Pair(row, numbers)
    }

    /** Dong gem: ten + [hien tai/toi da] + thanh do ben 4dp (vang khi sap het) */
    private fun gemBarRow(ctx: Context, name: String, text: String, percent: Int, low: Boolean): View {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(0, dp(4), 0, dp(4))
        val top = LinearLayout(ctx)
        top.orientation = LinearLayout.HORIZONTAL
        val l = caption(ctx, name)
        val v = TextView(ctx)
        v.text = if (low) "$text · sắp hết" else text
        v.textSize = 13f
        v.setTextColor(Color.parseColor(if (low) C_WARN else C_TEXT))
        v.gravity = Gravity.END
        top.addView(l, lparams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight = 1f))
        top.addView(v, lparams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val pct = percent.coerceIn(0, 100)
        val bar = LinearLayout(ctx)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.background = roundBg(UiTheme.BAR_IDLE, 2)
        val fill = View(ctx)
        fill.background = roundBg(if (low) UiTheme.WARN else UiTheme.OK, 2)
        if (pct > 0) bar.addView(fill, LinearLayout.LayoutParams(0, dp(4), pct.toFloat()))
        if (pct < 100) bar.addView(View(ctx), LinearLayout.LayoutParams(0, dp(4), (100 - pct).toFloat()))
        box.addView(top, lparams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        box.addView(bar, lparams(LinearLayout.LayoutParams.MATCH_PARENT, dp(4)).also { it.topMargin = dp(4) })
        return box
    }

    /** Huntbot: vong tron % + tieu de + dong phu */
    private fun ringRow(ctx: Context, pct: Int, title: String, sub: String): View {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(2), 0, dp(4))
        val holder = FrameLayout(ctx)
        val ring = RingView(ctx)
        ring.strokeWidthDp = 4f
        ring.ringColor = Color.parseColor(UiTheme.OK)
        ring.trackColor = Color.parseColor(UiTheme.BAR_IDLE)
        ring.percent = pct
        holder.addView(ring, FrameLayout.LayoutParams(dp(44), dp(44)))
        val label = TextView(ctx)
        label.text = "$pct%"
        label.textSize = 11f
        label.setTypeface(null, Typeface.BOLD)
        label.setTextColor(Color.parseColor(C_TEXT))
        label.gravity = Gravity.CENTER
        holder.addView(label, FrameLayout.LayoutParams(dp(44), dp(44)))
        val texts = LinearLayout(ctx)
        texts.orientation = LinearLayout.VERTICAL
        val t1 = TextView(ctx)
        t1.text = title
        t1.textSize = 13f
        t1.setTypeface(null, Typeface.BOLD)
        t1.setTextColor(Color.parseColor(C_GOOD))
        texts.addView(t1)
        texts.addView(caption(ctx, sub))
        row.addView(holder, lparams(dp(44), dp(44)))
        row.addView(texts, lparams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight = 1f).also { it.leftMargin = dp(10) })
        return row
    }

    private fun lparams(w: Int, h: Int, weight: Float = 0f, margin: Int = 0): LinearLayout.LayoutParams {
        val p = if (weight > 0f) LinearLayout.LayoutParams(w, h, weight) else LinearLayout.LayoutParams(w, h)
        p.setMargins(margin, margin, margin, margin)
        return p
    }

    private fun makeGroup(ctx: Context, key: String, title: String, icon: Int? = null): LinearLayout {
        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(12), dp(10), dp(12), dp(10))
        card.background = roundBg(C_CARD, 12, UiTheme.LINE)

        val header = LinearLayout(ctx)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL

        val titleTv = TextView(ctx)
        titleTv.text = title
        titleTv.textSize = 13f
        titleTv.setTypeface(null, Typeface.BOLD)
        titleTv.setTextColor(Color.parseColor(C_TITLE))
        if (icon != null) {
            titleTv.setCompoundDrawables(iconDrawable(ctx, icon, dp(18), UiTheme.TEXT_SECONDARY), null, null, null)
            titleTv.compoundDrawablePadding = dp(8)
        }

        val summary = TextView(ctx)
        summary.textSize = 11.5f
        summary.setTextColor(Color.parseColor(C_MUTED))
        summary.maxLines = 1
        summary.ellipsize = TextUtils.TruncateAt.END
        summary.gravity = Gravity.END
        summary.setPadding(dp(8), 0, dp(6), 0)

        val chevron = TextView(ctx)
        chevron.textSize = 13f
        chevron.setTextColor(Color.parseColor(C_MUTED))
        chevron.minimumHeight = dp(24)

        header.addView(titleTv, lparams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        header.addView(summary, lparams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight = 1f))
        header.addView(chevron, lparams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val body = LinearLayout(ctx)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(0, dp(4), 0, 0)

        card.addView(header, lparams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        card.addView(body, lparams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val g = Group(card, body, summary, chevron)
        groups[key] = g
        applyCollapsed(key)
        header.setOnClickListener {
            if (key in collapsed) collapsed.remove(key) else collapsed.add(key)
            applyCollapsed(key)
            relayout()
        }
        return card
    }

    private fun applyCollapsed(key: String) {
        val g = groups[key] ?: return
        val isCollapsed = key in collapsed
        g.body.visibility = if (isCollapsed) View.GONE else View.VISIBLE
        val ctx = viewCtx
        if (ctx != null) {
            g.chevron.setCompoundDrawables(
                iconDrawable(ctx, if (isCollapsed) R.drawable.ic_chevron_down else R.drawable.ic_chevron_up, dp(18), UiTheme.TEXT_MUTED),
                null, null, null
            )
        }
    }

    private fun rowView(ctx: Context, r: Row): View {
        r.build?.let { return it(ctx) }
        val color = when (r.tone) {
            Tone.GOOD -> C_GOOD
            Tone.WARN -> C_WARN
            Tone.MUTED -> C_MUTED
            Tone.NORMAL -> C_TEXT
        }
        val line = LinearLayout(ctx)
        line.orientation = LinearLayout.HORIZONTAL
        line.setPadding(0, dp(4), 0, dp(4))
        if (r.label.isNotEmpty()) {
            val l = TextView(ctx)
            l.text = r.label
            l.textSize = 11.5f
            l.setTextColor(Color.parseColor(C_LABEL))
            line.addView(l, lparams(dp(92), LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        val v = TextView(ctx)
        v.text = r.value
        v.textSize = 13f
        v.setTextColor(Color.parseColor(color))
        line.addView(v, lparams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight = 1f))
        return line
    }

    /** 1 dong lenh: ten + lenh that bo go (trai), nut BAT / TAT (phai) */
    private fun commandRow(ctx: Context, cmd: FarmCmd): View {
        val line = LinearLayout(ctx)
        line.orientation = LinearLayout.HORIZONTAL
        line.gravity = Gravity.CENTER_VERTICAL
        line.setPadding(0, dp(3), 0, dp(3))
        val left = LinearLayout(ctx)
        left.orientation = LinearLayout.VERTICAL
        val name = TextView(ctx)
        name.text = cmd.label
        name.textSize = 13f
        name.setTypeface(null, Typeface.BOLD)
        name.setTextColor(Color.parseColor(UiTheme.TEXT_PRIMARY))
        left.addView(name)
        left.addView(caption(ctx, cmd.shown, C_MUTED))
        line.addView(left, lparams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight = 1f))
        val on = FarmController.state.value.commands.isEnabled(cmd)
        val b = pill(ctx, if (on) "BẬT" else "TẮT", if (on) Btn.ACTIVE else Btn.SECONDARY, 12f)
        b.setOnClickListener {
            val now = FarmController.state.value.commands.isEnabled(cmd)
            FarmController.setCommandEnabled(ctx.applicationContext, cmd, !now)
        }
        cmdButtons[cmd] = b
        line.addView(b, lparams(dp(64), LinearLayout.LayoutParams.WRAP_CONTENT))
        return line
    }

    /** Hai nut cau hinh nhanh: bat tat ca / chi hunt + battle */
    private fun presetRow(ctx: Context): View {
        val line = LinearLayout(ctx)
        line.orientation = LinearLayout.HORIZONTAL
        line.setPadding(0, dp(2), 0, dp(4))
        val presets = listOf("Bật tất cả" to FarmCommandConfig.ALL_ON, "Chỉ hunt + battle" to FarmCommandConfig.HUNT_BATTLE_ONLY)
        for ((i, pr) in presets.withIndex()) {
            val b = pill(ctx, pr.first, Btn.SECONDARY, 12f)
            b.setOnClickListener { FarmController.changeCommands(ctx.applicationContext) { pr.second } }
            presetButtons.add(b to pr.second)
            line.addView(b, lparams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight = 1f).also {
                it.setMargins(if (i == 0) 0 else dp(3), 0, if (i == presets.size - 1) 0 else dp(3), 0)
            })
        }
        return line
    }

    /** Dung lai noi dung khung "Lenh trong farm" (chay 1 lan khi mo Hub; nut BAT/TAT tu cap nhat o render) */
    private fun buildCommandGroup() {
        val rows = mutableListOf<Row>()
        rows.add(Row("", "", build = { presetRow(it) }))
        for (g in FarmCmdGroup.values()) {
            rows.add(Row("", "", build = { c -> caption(c, g.label).also { it.setPadding(0, dp(6), 0, dp(2)) } }))
            for (cmd in FarmCmd.values()) if (cmd.group == g) rows.add(Row("", "", build = { c -> commandRow(c, cmd) }))
        }
        rows.add(Row("", "", build = { c -> caption(c, "Tắt lệnh nào thì farm bỏ qua lệnh đó. Đổi giữa chừng có hiệu lực từ vòng kế tiếp; lệnh bot tự gõ (đổi gem...) không nằm ở đây.", C_MUTED) }))
        setGroup("cmds", cmdSummary(FarmController.state.value.commands), rows)
    }

    private fun cmdSummary(c: FarmCommandConfig) = "${c.enabledCount()}/${c.total()} bật"

    /** Dien noi dung 1 nhom: dong tom tat tren tieu de + cac dong ben duoi. visible=false -> an ca the */
    private fun setGroup(key: String, summary: String, rows: List<Row>, visible: Boolean = true) {
        val g = groups[key] ?: return
        val ctx = viewCtx ?: return
        g.card.visibility = if (visible) View.VISIBLE else View.GONE
        g.summary.text = summary
        g.body.removeAllViews()
        for (r in rows) g.body.addView(rowView(ctx, r))
    }

    // ------------------------------------------------------------------ hien Hub

    fun show(service: AutoAccessibilityService) {
        if (root != null) return
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        viewCtx = service
        density = service.resources.displayMetrics.density
        val screenH = service.resources.displayMetrics.heightPixels
        val screenW = service.resources.displayMetrics.widthPixels

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // KEEP_SCREEN_ON: Hub noi dang bat thi man hinh KHONG BAO GIO tat (tat man hinh la app khong go duoc)
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = dp(8)
        lp.y = dp(110)
        params = lp

        val container = LinearLayout(service)
        container.orientation = LinearLayout.VERTICAL

        // ---- Bong bong: vien vang (binh thuong) / tim + dem nguoc (dang cho) / do (captcha) ----
        val bubbleBox = FrameLayout(service)
        val bgOval = GradientDrawable()
        bgOval.shape = GradientDrawable.OVAL
        bgOval.setColor(Color.parseColor("#F00F121A"))
        bgOval.setStroke(dp(2), Color.parseColor(UiTheme.ACCENT))
        bubbleBox.background = bgOval
        bubbleBg = bgOval
        val starIcon = ImageView(service)
        starIcon.setImageDrawable(iconDrawable(service, R.drawable.ic_star, dp(22), UiTheme.ACCENT))
        starIcon.scaleType = ImageView.ScaleType.CENTER
        bubbleIcon = starIcon
        val bubble = TextView(service)
        bubble.textSize = 11f
        bubble.setTypeface(null, Typeface.BOLD)
        bubble.setTextColor(Color.parseColor(UiTheme.TEXT_PRIMARY))
        bubble.gravity = Gravity.CENTER
        bubble.visibility = View.GONE
        bubbleBox.addView(starIcon, FrameLayout.LayoutParams(dp(46), dp(46)))
        bubbleBox.addView(bubble, FrameLayout.LayoutParams(dp(46), dp(46)))
        container.addView(bubbleBox, LinearLayout.LayoutParams(dp(46), dp(46)))

        // ---- Duyet vu khi: 2 nut nho GIU / BO canh bong bong (chi hien khi `,weaponreview` dang chay), hien ca khi menu thu gon ----
        val rvRow = LinearLayout(service)
        rvRow.orientation = LinearLayout.HORIZONTAL
        rvRow.gravity = Gravity.CENTER_VERTICAL
        rvRow.setPadding(dp(6), dp(6), dp(6), dp(6))
        rvRow.background = roundBg(C_PANEL, 14, UiTheme.LINE)
        rvRow.visibility = View.GONE
        val rvInfo = TextView(service)
        rvInfo.textSize = 11.5f
        rvInfo.setTextColor(Color.parseColor(C_LABEL))
        rvInfo.setPadding(dp(4), 0, dp(8), 0)
        val keepBtn = pill(service, "Giữ", Btn.GOOD, 13f)
        val dropBtn = pill(service, "Bỏ", Btn.DANGER, 13f)
        for (b in listOf(keepBtn, dropBtn)) { b.minHeight = dp(40); b.minWidth = dp(54); b.setPadding(dp(8), dp(4), dp(8), dp(4)) }
        rvRow.addView(rvInfo, lparams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        rvRow.addView(keepBtn, lparams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)).also { it.rightMargin = dp(6) })
        rvRow.addView(dropBtn, lparams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)))
        reviewRow = rvRow
        tvReviewInfo = rvInfo
        keepBtn.setOnClickListener { scope?.launch { FarmController.reviewDecision(service, "keep") } }
        dropBtn.setOnClickListener { scope?.launch { FarmController.reviewDecision(service, "drop") } }
        container.addView(rvRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(6) })

        // ---- Khung chinh ----
        val panelWidth = minOf(dp(300), screenW - dp(40))
        val panelBox = TouchAwareLinearLayout(service) { if (panel?.visibility == View.VISIBLE) scheduleAutoCollapse() }
        panelBox.orientation = LinearLayout.VERTICAL
        panelBox.setPadding(dp(12), dp(12), dp(12), dp(12))
        panelBox.background = roundBg(C_PANEL, 16, UiTheme.LINE)
        panelBox.visibility = View.GONE
        panel = panelBox

        // -- Dau: trang thai (co dinh, khong cuon) --
        val statusRow = LinearLayout(service)
        statusRow.orientation = LinearLayout.HORIZONTAL
        statusRow.gravity = Gravity.CENTER_VERTICAL
        val dot = TextView(service)
        dot.text = "●"
        dot.textSize = 12f
        dot.setTextColor(Color.parseColor(C_OFF))
        dot.setPadding(0, 0, dp(6), 0)
        val statusTitle = TextView(service)
        statusTitle.textSize = 14f
        statusTitle.setTypeface(null, Typeface.BOLD)
        statusTitle.setTextColor(Color.parseColor(C_TITLE))
        val statusCounts = TextView(service)
        statusCounts.textSize = 11.5f
        statusCounts.setTextColor(Color.parseColor(C_LABEL))
        statusRow.addView(dot, lparams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        statusRow.addView(statusTitle, lparams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight = 1f))
        statusRow.addView(statusCounts, lparams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val statusDetail = TextView(service)
        statusDetail.textSize = 11.5f
        statusDetail.setTextColor(Color.parseColor(C_LABEL))
        statusDetail.setPadding(0, dp(2), 0, dp(6))

        val alert = TextView(service)
        alert.textSize = 13f
        alert.setTypeface(null, Typeface.BOLD)
        alert.setTextColor(Color.parseColor(UiTheme.BAD))
        alert.setPadding(dp(12), dp(10), dp(12), dp(10))
        alert.setCompoundDrawables(iconDrawable(service, R.drawable.ic_alert, dp(18), UiTheme.BAD), null, null, null)
        alert.compoundDrawablePadding = dp(8)
        alert.background = roundBg(UiTheme.BAD_TINT, 12, UiTheme.BAD_LINE)
        alert.visibility = View.GONE

        tvDot = dot
        tvStatusTitle = statusTitle
        tvStatusCounts = statusCounts
        tvStatusDetail = statusDetail
        tvAlert = alert

        // Dong ho dem nguoc: app dang CHO (nghi / gian nhip / cooldown) hoac thoi gian vang giai captcha.
        // Bong bong cung hien dong ho nen thay duoc ngay ca khi menu dang thu gon.
        val countdown = WaitCountdownView(service)
        countdown.onTick = { t ->
            if (t == null) {
                bubble.visibility = View.GONE
                starIcon.visibility = View.VISIBLE
            } else {
                bubble.text = t
                bubble.visibility = View.VISIBLE
                starIcon.visibility = View.GONE
            }
        }
        waitView = countdown

        // -- Nut: hang 1 (bat dau/dung + tat chuong), hang 2 (hunt mode, thu gon, tat hub) --
        val toggle = pill(service, "Bắt đầu", Btn.PRIMARY, 14f, R.drawable.ic_play)
        val muteBtn = pill(service, "Tắt chuông", Btn.DANGER, 13f, R.drawable.ic_alert)
        muteBtn.visibility = View.GONE
        val modeBtn = pill(service, "Hunt mode 3 · không dừng", Btn.SECONDARY, 13f, R.drawable.ic_sliders)
        val hideBtn = iconButton(service, R.drawable.ic_minus)
        val closeBtn = iconButton(service, R.drawable.ic_close)
        val eventBtn = pill(service, "Event: Tắt", Btn.SECONDARY, 13f, R.drawable.ic_star)
        val autoBtn = pill(service, "")
        val clearStatsBtn = pill(service, STATS_CLEAR_LABEL, Btn.DANGER)
        btnToggle = toggle
        btnMute = muteBtn
        btnMode = modeBtn
        btnEvent = eventBtn
        btnAuto = autoBtn
        val tagBtn = pill(service, "Học lời khuyên vũ khí", Btn.SECONDARY, 13f, R.drawable.ic_quest)
        btnTags = tagBtn

        // Dung moi tab de bam duoc: hang tren cung (Bat dau/Dung + thu gon + tat Hub) luon hien o moi tab
        val rowMute = LinearLayout(service)
        rowMute.orientation = LinearLayout.HORIZONTAL
        rowMute.addView(muteBtn, lparams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight = 1f).also { it.bottomMargin = dp(6) })
        val row1 = LinearLayout(service)
        row1.orientation = LinearLayout.HORIZONTAL
        row1.addView(toggle, lparams(0, dp(44), weight = 1f))
        row1.addView(hideBtn, lparams(dp(44), dp(44)).also { it.leftMargin = dp(6) })
        row1.addView(closeBtn, lparams(dp(44), dp(44)).also { it.leftMargin = dp(6) })

        // -- Thanh 4 tab: Farm / Setting / Misc / Event --
        val prefs = service.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        autoCollapse = prefs.getBoolean(PREF_AUTO_COLLAPSE, true)
        currentTab = prefs.getInt(PREF_TAB, TAB_FARM).coerceIn(0, TAB_LABELS.size - 1)
        val tabBar = LinearLayout(service)
        tabBar.orientation = LinearLayout.HORIZONTAL
        tabBar.setPadding(dp(4), dp(4), dp(4), dp(4))
        tabBar.background = roundBg(UiTheme.BG, 12, UiTheme.LINE)
        tabButtons.clear()
        for ((i, label) in TAB_LABELS.withIndex()) {
            val t = TextView(service)
            t.text = label
            t.textSize = 11.5f
            t.setTypeface(null, Typeface.BOLD)
            t.gravity = Gravity.CENTER_HORIZONTAL
            t.setPadding(dp(2), dp(6), dp(2), dp(4))
            t.minHeight = dp(46)
            t.setCompoundDrawables(null, iconDrawable(service, TAB_ICONS[i], dp(18)), null, null)
            t.compoundDrawablePadding = dp(2)
            t.isClickable = true
            styleTab(t, false)
            t.setOnClickListener { showTab(i) }
            tabButtons.add(t)
            tabBar.addView(t, lparams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight = 1f, margin = dp(1)))
        }
        val tabBarLp = LinearLayout.LayoutParams(panelWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
        tabBarLp.setMargins(0, dp(10), 0, dp(10))

        // -- Noi dung 4 tab (cuon duoc): moi tab 1 khung rieng, moi luc chi hien 1 khung --
        val content = LinearLayout(service)
        content.orientation = LinearLayout.VERTICAL
        tabContainers.clear()
        repeat(TAB_LABELS.size) {
            val c = LinearLayout(service)
            c.orientation = LinearLayout.VERTICAL
            tabContainers.add(c)
            content.addView(c, lparams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        fun addTo(tab: Int, v: View) {
            tabContainers[tab].addView(
                v,
                lparams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
                    it.setMargins(0, 0, 0, dp(6))
                }
            )
        }
        fun addGroupTo(tab: Int, key: String, title: String, icon: Int) = addTo(tab, makeGroup(service, key, title, icon))

        // Farm: tinh hinh farm, gem, huntbot
        val bigTiles = tileRow(
            service,
            listOf(Triple("Hunt", "0", UiTheme.TEXT_PRIMARY), Triple("Battle", "0", UiTheme.TEXT_PRIMARY))
        )
        tvHuntBig = bigTiles.second[0]
        tvBattleBig = bigTiles.second[1]
        addTo(TAB_FARM, bigTiles.first)
        addGroupTo(TAB_FARM, "farm", "Farm", R.drawable.ic_bolt)
        addGroupTo(TAB_FARM, "gem", "Gem", R.drawable.ic_gem)
        addGroupTo(TAB_FARM, "hb", "Huntbot", R.drawable.ic_huntbot)
        // Setting: cac cai dat cua Hub noi
        // Toc do farm: 3 nut Nhanh / Thuong / Cham (nut dang chon to tim); doi la co hieu luc o vong ke tiep
        val speedRow = LinearLayout(service)
        speedRow.orientation = LinearLayout.HORIZONTAL
        speedButtons.clear()
        cmdButtons.clear()
        presetButtons.clear()
        for ((i, m) in SpeedMode.values().withIndex()) {
            val b = pill(service, m.label, Btn.SECONDARY, 13f)
            b.setOnClickListener { FarmController.setSpeed(service.applicationContext, m) }
            speedButtons.add(b)
            speedRow.addView(b, lparams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight = 1f).also {
                it.setMargins(if (i == 0) 0 else dp(3), 0, if (i == SpeedMode.values().size - 1) 0 else dp(3), 0)
            })
        }
        addTo(TAB_SETTING, caption(service, "Tốc độ farm"))
        addTo(TAB_SETTING, speedRow)
        addTo(TAB_SETTING, caption(service, "Nhanh: nhiều lệnh hơn nhưng dễ bị OwO nghi ngờ hơn. Chậm: an toàn hơn. Có hiệu lực từ vòng kế tiếp.", C_MUTED))
        addGroupTo(TAB_SETTING, "cmds", "Lệnh trong farm", R.drawable.ic_sliders)
        buildCommandGroup()
        addTo(TAB_SETTING, modeBtn)
        addTo(TAB_SETTING, autoBtn)
        addTo(TAB_SETTING, clearStatsBtn)
        // Misc: hoc loi khuyen vu khi, thong ke, tai nguyen, nhiem vu, zoo
        addTo(TAB_MISC, tagBtn)
        addGroupTo(TAB_MISC, "stats", "Thống kê", R.drawable.ic_chart)
        addGroupTo(TAB_MISC, "res", "Tài nguyên", R.drawable.ic_coin)
        addGroupTo(TAB_MISC, "quest", "Nhiệm vụ", R.drawable.ic_quest)
        addGroupTo(TAB_MISC, "zoo", "Zoo & pet mới", R.drawable.ic_pets)
        // Event: bat/tat che do event + dem su kien
        addTo(TAB_EVENT, eventBtn)
        addGroupTo(TAB_EVENT, "event", "Sự kiện", R.drawable.ic_star)

        val scroll = MaxHeightScrollView(service, (screenH * 0.46f).toInt())
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(content)

        val wrap = LinearLayout.LayoutParams.WRAP_CONTENT
        panelBox.addView(statusRow, LinearLayout.LayoutParams(panelWidth, wrap))
        panelBox.addView(statusDetail, LinearLayout.LayoutParams(panelWidth, wrap))
        panelBox.addView(countdown, LinearLayout.LayoutParams(panelWidth, wrap).also { it.setMargins(0, 0, 0, dp(6)) })
        panelBox.addView(alert, LinearLayout.LayoutParams(panelWidth, wrap).also { it.setMargins(0, 0, 0, dp(6)) })
        panelBox.addView(rowMute, LinearLayout.LayoutParams(panelWidth, wrap))
        panelBox.addView(row1, LinearLayout.LayoutParams(panelWidth, wrap))
        panelBox.addView(tabBar, tabBarLp)
        panelBox.addView(scroll, LinearLayout.LayoutParams(panelWidth, wrap))
        container.addView(panelBox, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // ---- Su kien ----
        toggle.setOnClickListener {
            if (FarmController.isRunning()) {
                FarmController.stop()
            } else {
                FarmController.clearAlert()
                FarmController.start(service)
            }
        }
        muteBtn.setOnClickListener { FarmController.stopAlarm() }
        // Tu hoc loi khuyen vu khi trong server OwO (go ?tag <vu khi>, doc chu, gui len bot; bot gui file cho Ruby)
        tagBtn.setOnClickListener {
            if (TagCollector.isRunning()) TagCollector.stop() else TagCollector.start(service, service.applicationContext)
        }
        // Che do Event: BAT = dang co su kien, duoc dung slot thu 4 (gem Special). Bot luu lai trang thai.
        eventBtn.setOnClickListener {
            val nowOn = FarmController.state.value.hub?.eventMode == true
            scope?.launch { FarmController.setEventMode(service.applicationContext, !nowOn) }
        }
        modeBtn.setOnClickListener { FarmController.cycleHuntMode(service.applicationContext) }
        autoBtn.setOnClickListener { setAutoCollapse(!autoCollapse) }
        // Xoa thong ke: bam 2 lan trong 4 giay cho do xoa nham
        clearStatsBtn.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now < clearStatsConfirmUntilMs) {
                clearStatsConfirmUntilMs = 0L
                FarmController.clearStats()
                clearStatsBtn.text = STATS_CLEAR_LABEL
                render(FarmController.state.value)
            } else {
                clearStatsConfirmUntilMs = now + 4_000L
                clearStatsBtn.text = "Bấm lại để xóa hết thống kê"
                scope?.launch {
                    delay(4_000L)
                    clearStatsBtn.text = STATS_CLEAR_LABEL
                }
            }
        }
        FarmController.initStats(service.applicationContext)
        FarmController.loadHuntMode(service.applicationContext)
        // Chi thu gon menu, bong bong ngoi sao van o lai tren man hinh
        hideBtn.setOnClickListener {
            panelBox.visibility = View.GONE
            relayout()
        }
        // Tat hub: dung farm (neu dang chay) va go ca bong bong
        closeBtn.setOnClickListener {
            FarmController.stop()
            hide()
        }
        // Bat hub lai = bat dau phien moi: danh sach "pet moi" duoc xoa trang
        FarmController.resetSession()
        // Hub BAT = bot ON
        FarmController.reportHubPower(service.applicationContext, true)

        bubbleBox.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var moved = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = lp.x
                        startY = lp.y
                        touchX = e.rawX
                        touchY = e.rawY
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - touchX).toInt()
                        val dy = (e.rawY - touchY).toInt()
                        if (abs(dx) > 8 || abs(dy) > 8) moved = true
                        lp.x = startX + dx
                        lp.y = startY + dy
                        try {
                            wm.updateViewLayout(container, lp)
                        } catch (ex: Exception) {
                            // view da bi go
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) togglePanel()
                        return true
                    }
                }
                return false
            }
        })

        wm.addView(container, lp)
        root = container
        showTab(currentTab)
        updateAutoBtn()

        // ---- Cap nhat theo trang thai farm + lam moi so lieu dinh ky ----
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = s
        s.launch {
            FarmController.state.collect { st -> render(st) }
        }
        s.launch {
            TagCollector.progress.collect { p ->
                val label = when {
                    p.running -> "Đang học ${p.done}/${p.total} · bấm để dừng"
                    p.message.isNotEmpty() -> p.message
                    else -> "Học lời khuyên vũ khí"
                }
                btnTags?.text = label
                styleBtn(btnTags, if (p.running) Btn.ACTIVE else Btn.SECONDARY)
                render(FarmController.state.value)
            }
        }
        refreshJob = s.launch {
            while (true) {
                if (!FarmController.isRunning()) {
                    FarmController.refreshHub(service.applicationContext)
                }
                delay(REFRESH_MS)
            }
        }
    }

    /** Chuyen sang tab i (Farm/Setting/Misc/Event): chi tab do hien, tab bam duoc to mau, nho lai cho lan bat sau */
    private fun showTab(i: Int) {
        if (tabContainers.isEmpty()) return
        currentTab = i.coerceIn(0, tabContainers.size - 1)
        for ((k, c) in tabContainers.withIndex()) c.visibility = if (k == currentTab) View.VISIBLE else View.GONE
        for ((k, b) in tabButtons.withIndex()) styleTab(b, k == currentTab)
        viewCtx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putInt(PREF_TAB, currentTab)?.apply()
        relayout()
    }

    private fun setAutoCollapse(on: Boolean) {
        autoCollapse = on
        viewCtx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean(PREF_AUTO_COLLAPSE, on)?.apply()
        updateAutoBtn()
        if (on) {
            if (panel?.visibility == View.VISIBLE) scheduleAutoCollapse()
        } else {
            idleJob?.cancel()
            idleJob = null
        }
    }

    private fun updateAutoBtn() {
        btnAuto?.text = if (autoCollapse) "Tự thu gọn menu: BẬT" else "Tự thu gọn menu: Tắt"
        styleBtn(btnAuto, if (autoCollapse) Btn.ACTIVE else Btn.SECONDARY)
    }

    private fun togglePanel() {
        val p = panel ?: return
        p.visibility = if (p.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        relayout()
        if (p.visibility == View.VISIBLE) scheduleAutoCollapse()
    }

    /**
     * Menu mo ma khong cham vao AUTO_COLLAPSE_MS thi tu thu gon ve bong bong ⭐ (menu che nut tren man hinh Discord).
     * Moi lan cham vao menu thi tinh lai. Dang co canh bao (vd captcha) thi KHONG tu thu gon de khong bo lo.
     */
    private fun scheduleAutoCollapse() {
        idleJob?.cancel()
        if (!autoCollapse) return
        val s = scope ?: return
        idleJob = s.launch {
            delay(AUTO_COLLAPSE_MS)
            val p = panel
            if (p != null && p.visibility == View.VISIBLE && tvAlert?.visibility != View.VISIBLE) {
                p.visibility = View.GONE
                relayout()
            }
        }
    }

    private fun relayout() {
        val r = root ?: return
        val lp = params ?: return
        try {
            windowManager?.updateViewLayout(r, lp)
        } catch (e: Exception) {
            // bo qua
        }
    }

    private fun ageText(seconds: Long?): String = if (seconds == null) "" else " · " + HubFormat.age(seconds)

    // ------------------------------------------------------------------ do du lieu vao giao dien

    /** Nhom "Thong ke": 3 so lon hom nay + bieu do cot 7 ngay + dong chi tiet (so lieu luu o may, khong can bot) */
    private fun renderStats() {
        val days = FarmController.statsSnapshot(7)
        val today = LocalDate.now()
        val todayKey = today.toString()
        val byDate = days.associateBy { it.date }
        val t = byDate[todayKey]
        val rows = mutableListOf<Row>()
        if (t == null) {
            rows.add(Row("Hôm nay", "Chưa có số liệu (bấm Bắt đầu để farm)", Tone.MUTED))
        } else {
            val runText = StatsFormat.duration(t.seconds).replace(" phút", "p")
            rows.add(Row("", "", build = { c ->
                tileRow(
                    c,
                    listOf(
                        Triple("Hunt", t.hunts.toString(), UiTheme.TEXT_PRIMARY),
                        Triple("Battle", t.battles.toString(), UiTheme.TEXT_PRIMARY),
                        Triple("Chạy", runText, UiTheme.TEXT_PRIMARY)
                    )
                ).first
            }))
        }
        // bieu do 7 ngay (hunt + battle moi ngay), hom nay mau tim
        val bars = (6 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val ds = byDate[d.toString()]
            DayBar(StatsFormat.shortDate(d.toString()), (ds?.hunts ?: 0) + (ds?.battles ?: 0), back == 0)
        }
        rows.add(Row("", "", build = { c ->
            val chart = DayBarsView(c)
            chart.bars = bars
            chart.also { it.setPadding(0, dp(8), 0, 0) }
        }))
        if (t != null) {
            rows.add(Row("Phiên chạy", "${t.sessions} phiên"))
            rows.add(Row("Pet mới", "${t.newPets}"))
            rows.add(Row("Captcha", "${t.captchas} lần", if (t.captchas > 0) Tone.WARN else Tone.NORMAL))
            if (t.maxSlowdown30m > 0) {
                rows.add(Row("Slow down", "cao nhất ${t.maxSlowdown30m} lần/30 phút", Tone.WARN))
            }
            val first = t.cashFirst
            val last = t.cashLast
            if (first != null && last != null) {
                rows.add(Row("Cowoncy", "${StatsFormat.signed(last - first)} (từ lần đọc đầu ngày)"))
            } else {
                rows.add(Row("Cowoncy", "Chưa đọc owo cash hôm nay", Tone.MUTED))
            }
        }
        if (days.size > 1) {
            rows.add(
                Row(
                    "Tổng ${days.size} ngày",
                    "${days.sumOf { it.hunts }} hunt · ${days.sumOf { it.battles }} battle · ${StatsFormat.duration(days.sumOf { it.seconds })}",
                    Tone.MUTED
                )
            )
        }
        setGroup("stats", t?.let { "${it.hunts} hunt · ${it.battles} battle" } ?: "", rows)
    }

    private fun render(st: FarmState) {
        if (root == null) return
        val hub = st.hub

        // --- phan dau: trang thai ---
        val dotColor: String
        val title: String
        when {
            st.running && st.resting -> { dotColor = C_WARN; title = "Đang nghỉ" }
            st.running -> { dotColor = C_GOOD; title = "Đang farm" }
            else -> { dotColor = C_OFF; title = "Đã dừng" }
        }
        tvDot?.setTextColor(Color.parseColor(dotColor))
        tvStatusTitle?.text = title
        tvStatusCounts?.text = ""
        tvHuntBig?.text = st.hunts.toString()
        tvBattleBig?.text = st.battles.toString()
        val detail = StringBuilder(st.status)
        if (st.running && st.lastCommand.isNotEmpty()) detail.append("\nLệnh gần nhất: ").append(st.lastCommand)
        if (!st.huntPaused.isNullOrBlank()) detail.append("\nHunt tạm dừng: ").append(st.huntPaused)
        // tieu de da ghi "Đã dừng" thi khong lap lai y het o dong chi tiet
        tvStatusDetail?.text = if (detail.toString().trim() == title) "" else detail.toString()

        val modeShort = when (st.huntMode) {
            1 -> "đủ lootbox"
            2 -> "thiếu gem"
            else -> "không dừng"
        }
        btnMode?.text = "Hunt mode ${st.huntMode} · $modeShort"
        for ((i, m) in SpeedMode.values().withIndex()) {
            styleBtn(speedButtons.getOrNull(i), if (st.speed == m) Btn.ACTIVE else Btn.SECONDARY)
        }
        for ((cmd, b) in cmdButtons) {
            val on = st.commands.isEnabled(cmd)
            b.text = if (on) "BẬT" else "TẮT"
            styleBtn(b, if (on) Btn.ACTIVE else Btn.SECONDARY)
        }
        for ((b, cfg) in presetButtons) styleBtn(b, if (st.commands == cfg) Btn.ACTIVE else Btn.SECONDARY)
        groups["cmds"]?.summary?.text = cmdSummary(st.commands)
        setBtnState(btnToggle, if (st.running) "Dừng" else "Bắt đầu", if (st.running) Btn.DANGER else Btn.PRIMARY, if (st.running) R.drawable.ic_stop else R.drawable.ic_play)
        btnMute?.visibility = if (st.ringing) View.VISIBLE else View.GONE
        val eventOn = hub?.eventMode == true
        btnEvent?.text = if (eventOn) "Event: BẬT" else "Event: Tắt"
        styleBtn(btnEvent, if (eventOn) Btn.ACTIVE else Btn.SECONDARY)

        // Dem nguoc: thoi gian vang captcha (uu tien, mau do) > app dang cho (nghi / gian nhip / cooldown)
        val nowMs = System.currentTimeMillis()
        val cd = waitView
        if (cd != null) {
            when {
                st.captchaEndMs > 0L ->
                    cd.start(st.captchaEndMs, 10 * 60_000L, "Thời gian vàng giải captcha", true)
                st.waitEndMs > nowMs ->
                    cd.start(st.waitEndMs, st.waitTotalMs, st.waitLabel.ifBlank { "Đang chờ" }, false)
                else -> cd.clear()
            }
        }

        setBubbleState(
            when {
                st.captchaEndMs > 0L || !st.alert.isNullOrBlank() -> UiTheme.BAD
                st.waitEndMs > nowMs -> UiTheme.SELECTED
                else -> UiTheme.ACCENT
            }
        )

        val rv = st.hub?.weaponReview
        val reviewing = rv?.active == true && !rv.currentId.isNullOrBlank()
        val wantVisible = if (reviewing) View.VISIBLE else View.GONE
        if (reviewRow?.visibility != wantVisible) {
            reviewRow?.visibility = wantVisible
            relayout()
        }
        if (reviewing) tvReviewInfo?.text = "Cây ${rv?.currentId} · còn ${rv?.remaining ?: 0}"

        val alertText = st.alert
        if (alertText.isNullOrBlank()) {
            tvAlert?.visibility = View.GONE
            lastExpandedAlert = null
        } else {
            tvAlert?.text = alertText
            tvAlert?.visibility = View.VISIBLE
            // Canh bao MOI -> tu mo rong 1 LAN. Truoc day moi lan render() (rat thuong xuyen) deu ep mo lai, nen bam ➖
            // thu gon xong la menu bat lai ngay khi canh bao con do.
            val p = panel
            if (p != null && p.visibility != View.VISIBLE && alertText != lastExpandedAlert) {
                p.visibility = View.VISIBLE
                relayout()
            }
            lastExpandedAlert = alertText
        }

        renderStats()

        // --- chua co du lieu tu bot ---
        if (hub == null) {
            val err = st.hubError
            val msg = if (err.isNullOrBlank()) "Đang tải dữ liệu từ bot…" else "Lỗi: $err"
            setGroup("farm", "", listOf(Row("", msg, if (err.isNullOrBlank()) Tone.MUTED else Tone.WARN)))
            for (k in listOf("gem", "res", "hb", "quest", "zoo", "event")) setGroup(k, "", emptyList())
            return
        }

        val elapsed = if (st.hubFetchedAtMs > 0L) (System.currentTimeMillis() - st.hubFetchedAtMs) / 1000 else 0L

        // ===== Farm =====
        val farmRows = mutableListOf<Row>()
        val b = hub.battle
        val streakNow = b?.streak ?: 0
        if (b == null) {
            farmRows.add(Row("Chuỗi battle", "Chưa có dữ liệu (gõ owo battle)", Tone.MUTED))
        } else {
            val lost = b.lastLostStreak ?: 0
            val best = b.bestStreak?.let { " · cao nhất $it" } ?: ""
            farmRows.add(Row("Chuỗi battle", "$streakNow$best${ageText(b.ageSeconds)}"))
            if (b.result == "lost" && lost > 0) farmRows.add(Row("", "Vừa thua, mất chuỗi $lost", Tone.WARN))
        }
        val modeLong = when (st.huntMode) {
            1 -> "Dừng hunt khi đủ lootbox ngày"
            2 -> "Dừng hunt khi thiếu gem"
            else -> "Không dừng"
        }
        farmRows.add(Row("Hunt mode", "${st.huntMode} · $modeLong"))
        if (!st.huntPaused.isNullOrBlank()) farmRows.add(Row("Hunt", "Tạm dừng: ${st.huntPaused}", Tone.WARN))
        hub.slowdown?.takeIf { (it.count30m ?: 0) > 0 }?.let { sd ->
            farmRows.add(Row("OwO báo chậm", "${sd.count30m} lần/30 phút${ageText(sd.ageSeconds)} — app đang tự giãn nhịp gõ", Tone.WARN))
        }
        val tagP = TagCollector.progress.value
        if (tagP.running || tagP.message.isNotEmpty()) {
            val tagText = if (tagP.running) "${tagP.done}/${tagP.total} · ${tagP.message}" else tagP.message
            farmRows.add(Row("Học tag", tagText, if (tagText.startsWith("✅")) Tone.GOOD else if (tagText.startsWith("⚠️")) Tone.WARN else Tone.NORMAL))
        }
        setGroup("farm", if (b == null) "" else "chuỗi $streakNow", farmRows)

        // ===== Gem =====
        val gemRows = mutableListOf<Row>()
        gemRows.add(
            if (eventOn) Row("Event", "BẬT — dùng cả slot 4 (Special)", Tone.GOOD)
            else Row("Event", "Tắt — gem Special không tự dùng", Tone.MUTED)
        )
        val eq = hub.gems?.equipped.orEmpty()
        if (eq.isEmpty()) {
            gemRows.add(Row("", "Đang dùng: chưa có dữ liệu (gõ owo hunt)", Tone.MUTED))
        } else {
            gemRows.add(Row("", "Đang dùng:", Tone.MUTED))
            for (g in eq) {
                val low = (g.percent ?: 100) < 20
                val gemName = HubFormat.gemType(g.slot)
                val gemText = "${g.tier ?: "?"} [${g.current ?: 0}/${g.max ?: 0}]"
                val gemPct = g.percent ?: 100
                gemRows.add(Row(gemName, gemText, if (low) Tone.WARN else Tone.NORMAL, build = { c -> gemBarRow(c, gemName, gemText, gemPct, low) }))
            }
        }
        val spare = hub.gems?.spare.orEmpty()
        if (spare.isEmpty()) {
            gemRows.add(Row("", "Trong kho: chưa có dữ liệu (gõ owo inv)", Tone.MUTED))
        } else {
            gemRows.add(Row("", "Trong kho${ageText(hub.gems?.spareAgeSeconds)}:", Tone.MUTED))
            for (entry in spare.groupBy { it.slot ?: "?" }.entries.sortedBy { it.key }) {
                gemRows.add(Row(HubFormat.gemType(entry.key), entry.value.joinToString(" · ") { g -> "${g.tier ?: "?"} ×${g.count ?: 0}" }))
            }
        }
        setGroup("gem", if (eq.isEmpty()) "" else "${eq.size} đang dùng", gemRows)

        // ===== Tai nguyen =====
        val resRows = mutableListOf<Row>()
        val cashAmount = hub.cowoncy?.amount
        val wsAmount = hub.weaponShards?.amount
        if (cashAmount == null) {
            resRows.add(Row("Cowoncy", "Chưa có dữ liệu (gõ owo cash)", Tone.MUTED))
        } else {
            resRows.add(Row("Cowoncy", "${HubFormat.num(cashAmount)}${ageText(hub.cowoncy?.ageSeconds)}"))
        }
        if (wsAmount == null) {
            resRows.add(Row("Weapon shards", "Chưa có dữ liệu (gõ owo ws)", Tone.MUTED))
        } else {
            resRows.add(Row("Weapon shards", "${HubFormat.num(wsAmount)}${ageText(hub.weaponShards?.ageSeconds)}"))
        }
        val resSummary = listOfNotNull(
            cashAmount?.let { "${HubFormat.num(it)} cash" },
            wsAmount?.let { "${HubFormat.num(it)} ws" }
        ).joinToString(" · ")
        setGroup("res", resSummary, resRows)

        // ===== Huntbot =====
        val hbRows = mutableListOf<Row>()
        val h = hub.huntbot
        var hbSummary = ""
        if (h == null) {
            hbRows.add(Row("", "Chưa có dữ liệu (gõ owo hb)", Tone.MUTED))
        } else {
            if (h.hunting == true) {
                val left = h.secondsLeft?.let { it - elapsed }
                val timeText = when {
                    left == null -> h.timeRemainingText?.let { "còn $it" } ?: "chưa rõ giờ xong"
                    left > 0L -> "còn ${HubFormat.shortDuration(left)}"
                    else -> "đã tới giờ xong"
                }
                val pct = h.progressPct?.let { "${HubFormat.trimNum(it)}%" } ?: ""
                val pctInt = h.progressPct?.toInt()
                if (pctInt != null) {
                    hbRows.add(Row("", "", build = { c -> ringRow(c, pctInt, "Đang săn", timeText) }))
                } else {
                    hbRows.add(Row("Trạng thái", "Đang săn", Tone.GOOD))
                    hbRows.add(Row("Thời gian", timeText))
                }
                h.animalsCaptured?.let { hbRows.add(Row("Bắt được", "${HubFormat.num(it)} con")) }
                hbSummary = listOf(pct, timeText).filter { it.isNotEmpty() }.joinToString(" · ")
            } else {
                hbRows.add(Row("Trạng thái", "KHÔNG chạy — bạn tự chạy lại", Tone.WARN))
                hbSummary = "không chạy"
            }
            h.essence?.let { hbRows.add(Row("Essence", HubFormat.num(it))) }
            val traitNames = listOf(
                "efficiency" to "Eff", "duration" to "Dur", "cost" to "Cost",
                "gain" to "Gain", "experience" to "Exp", "radar" to "Radar"
            )
            val traits = h.traits
            if (traits != null) {
                val text = traitNames.mapNotNull { (k, n) -> traits[k]?.let { "$n $it" } }.joinToString(" · ")
                if (text.isNotEmpty()) hbRows.add(Row("Cấp", text, Tone.MUTED))
            }
        }
        setGroup("hb", hbSummary, hbRows)

        // ===== Nhiem vu =====
        val questRows = mutableListOf<Row>()
        val d = hub.daily
        var questSummary = ""
        if (d == null) {
            questRows.add(Row("Daily", "Chưa có dữ liệu (gõ owo daily)", Tone.MUTED))
        } else {
            val left = d.secondsLeft?.let { it - elapsed }
            val ready = left != null && left <= 0L
            val timeText = when {
                left == null -> "chưa rõ giờ nhận"
                left > 0L -> "còn ${HubFormat.shortDuration(left)} nữa nhận được"
                else -> "sẵn sàng nhận"
            }
            val streak = d.streak?.let { " · chuỗi ${HubFormat.num(it)} ngày" } ?: ""
            questRows.add(Row("Daily", "$timeText$streak", if (ready) Tone.GOOD else Tone.NORMAL))
            questSummary = if (ready) "daily sẵn sàng" else ""
        }
        val q = hub.quest
        val dailyProg = hub.checklists?.daily?.progress
        val weeklyProg = hub.checklists?.weekly?.progress
        val prog = mutableListOf<String>()
        dailyProg?.cur?.let { prog.add("daily $it/${dailyProg?.max ?: "?"}") }
        weeklyProg?.cur?.let { prog.add("weekly $it/${weeklyProg?.max ?: "?"}") }
        if (prog.isNotEmpty()) {
            questRows.add(Row("Checklist", prog.joinToString(" · ")))
            if (questSummary.isEmpty()) questSummary = prog.first()
        }
        val qItems = q?.quests.orEmpty()
        if (qItems.isNotEmpty()) questRows.add(Row("Quest", "còn ${qItems.count { it.done != true }}/${qItems.size} chưa xong"))
        q?.seals?.let { questRows.add(Row("Seals", HubFormat.num(it))) }
        if (q == null && prog.isEmpty()) questRows.add(Row("Quest", "Chưa có dữ liệu (gõ owo q)", Tone.MUTED))
        setGroup("quest", questSummary, questRows)

        // ===== Zoo & pet moi =====
        val zooRows = mutableListOf<Row>()
        val z = hub.zoo
        var unlocked = 0
        if (z == null) {
            zooRows.add(Row("Zoo", "Chưa có dữ liệu (gõ owo zoo)", Tone.MUTED))
        } else {
            val sacrificed = z.sacrificedPets.orEmpty().size
            unlocked = z.unlockedTotal ?: (z.pets.orEmpty().size + sacrificed)
            val points = z.zooPoints?.let { " · $it điểm" } ?: ""
            zooRows.add(Row("Zoo", "$unlocked loài đã mở khóa$points${ageText(z.ageSeconds)}"))
            if (sacrificed > 0) zooRows.add(Row("", "Trong đó $sacrificed loài số lượng 0 (đã hiến tế)", Tone.MUTED))
        }
        // pet moi: chi tinh tu luc bat Hub / bat farm (tat bat lai la xoa trang)
        val sessionNames = st.sessionNewPets.toSet()
        val newPets = hub.newPets?.pets.orEmpty().filter { (it.name ?: "") in sessionNames }
        if (newPets.isEmpty()) {
            zooRows.add(Row("Pet mới", "Chưa có lần farm này", Tone.MUTED))
        } else {
            newPets.take(6).forEachIndexed { i, p ->
                val text = (p.name ?: "?") + (p.rankVi?.let { " · $it" } ?: "") + " ×${p.timesFound ?: 1}"
                zooRows.add(Row(if (i == 0) "Pet mới" else "", text, Tone.GOOD))
            }
        }
        val zooSummary = listOfNotNull(
            if (z != null) "$unlocked loài" else null,
            if (newPets.isNotEmpty()) "${newPets.size} mới" else null
        ).joinToString(" · ")
        setGroup("zoo", zooSummary, zooRows)

        // ===== Su kien (nam trong tab Event; chua thay tin su kien thi hien dong bao chua co) =====
        val evCounters = hub.event?.counters.orEmpty()
        if (evCounters.isEmpty()) {
            setGroup("event", "", listOf(Row("", "Chưa thấy tin sự kiện nào từ bot", Tone.MUTED)))
        } else {
            val evRows = mutableListOf<Row>()
            for (c in evCounters) {
                evRows.add(Row(c.kind ?: "?", "${c.current ?: "?"}/${c.max ?: "?"}${ageText(c.ageSeconds)}"))
            }
            val latest = hub.event?.recent.orEmpty().firstOrNull()
            val rewardText = latest?.rewards.orEmpty().joinToString(", ") { r -> "${HubFormat.num(r.amount)} ${r.item ?: "?"}" }
            if (latest != null && rewardText.isNotEmpty()) {
                evRows.add(Row("Gần nhất", rewardText + ageText(latest.ageSeconds), Tone.GOOD))
            }
            val evSummary = evCounters.joinToString(" · ") { c -> "${c.kind ?: "?"} ${c.current ?: "?"}/${c.max ?: "?"}" }
            setGroup("event", evSummary, evRows)
        }
    }
}
