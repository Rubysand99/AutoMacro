package com.starclan.starhub.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.starclan.starhub.data.CountdownFormat

/**
 * Dong ho dem nguoc cho Hub noi: dong chu "Ly do · m:ss" + thanh tien do co dan ve 0 (cap nhat moi 0,1 giay
 * nen chay muot). Dung de Ruby thay app dang CHO chu khong phai dung. danger = true (thoi gian vang captcha):
 * mau do, het gio thi hien "HET GIO" va dung lai. onTick(text) bao chu "m:ss" hien tai (null = da an) de
 * bong bong ngoi sao cung hien dong ho khi menu dang thu gon.
 */
class WaitCountdownView(context: Context) : LinearLayout(context) {
    private val title = TextView(context)
    private val bar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
    private val handler = Handler(Looper.getMainLooper())
    private var endMs = 0L
    private var totalMs = 1L
    private var label = ""
    private var danger = false
    private var lastText: String? = null

    var onTick: ((String?) -> Unit)? = null

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
        }
    }

    init {
        orientation = VERTICAL
        visibility = View.GONE
        val d = resources.displayMetrics.density
        title.textSize = 13f
        title.setTypeface(null, Typeface.BOLD)
        bar.max = BAR_MAX
        addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, (6 * d).toInt()).also { it.topMargin = (3 * d).toInt() })
    }

    /** Bat dau / cap nhat dem nguoc. Goi lai voi cung gia tri thi khong lam gi (khong giat). */
    fun start(endMs: Long, totalMs: Long, label: String, danger: Boolean) {
        if (visibility == View.VISIBLE && this.endMs == endMs && this.label == label && this.danger == danger) return
        this.endMs = endMs
        this.totalMs = if (totalMs > 0L) totalMs else 1L
        this.label = label
        this.danger = danger
        val color = Color.parseColor(if (danger) "#FF5252" else "#FFD54F")
        title.setTextColor(color)
        bar.progressTintList = ColorStateList.valueOf(color)
        bar.progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#343B4F"))
        visibility = View.VISIBLE
        handler.removeCallbacks(ticker)
        lastText = null
        refresh()
    }

    /** An dong ho va dung dem. */
    fun clear() {
        handler.removeCallbacks(ticker)
        val wasShown = visibility == View.VISIBLE
        visibility = View.GONE
        endMs = 0L
        lastText = null
        if (wasShown) onTick?.invoke(null)
    }

    private fun refresh() {
        val left = endMs - System.currentTimeMillis()
        if (left <= 0L) {
            if (danger) {
                title.text = "$label · HẾT GIỜ (0:00)"
                bar.progress = 0
                if (lastText != "0:00") {
                    lastText = "0:00"
                    onTick?.invoke("0:00")
                }
            } else {
                clear()
            }
            return
        }
        val text = CountdownFormat.mmss(left)
        title.text = "$label · $text"
        bar.progress = (CountdownFormat.fraction(left, totalMs) * BAR_MAX).toInt()
        if (text != lastText) {
            lastText = text
            onTick?.invoke(text)
        }
        handler.postDelayed(ticker, TICK_MS)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == View.VISIBLE && endMs > 0L) refresh()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val TICK_MS = 100L
        const val BAR_MAX = 1000
    }
}
