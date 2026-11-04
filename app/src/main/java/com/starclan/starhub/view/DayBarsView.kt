package com.starclan.starhub.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

data class DayBar(val label: String, val value: Int, val highlight: Boolean)

/**
 * Bieu do cot dung nho (7 ngay): cot hom nay mau tim (highlight), cac ngay khac xam xanh. Nhan ngay o duoi, so o tren cot.
 * Tu ve bang Canvas, khong can thu vien.
 */
class DayBarsView(context: Context) : View(context) {
    var bars: List<DayBar> = emptyList()
        set(value) { field = value; invalidate() }
    var activeColor: Int = Color.parseColor(UiTheme.SELECTED)
    var idleColor: Int = Color.parseColor(UiTheme.BAR_IDLE)
    var textColor: Int = Color.parseColor(UiTheme.TEXT_MUTED)

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()

    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(92f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bars.isEmpty() || width == 0) return
        val maxV = bars.maxOf { it.value }.coerceAtLeast(1)
        val labelH = dp(16f)
        val valueH = dp(14f)
        val chartTop = valueH
        val chartBottom = height - labelH
        val slot = width.toFloat() / bars.size
        val barW = (slot * 0.52f).coerceAtMost(dp(22f))
        textPaint.textSize = dp(10.5f)
        for ((i, b) in bars.withIndex()) {
            val cx = slot * i + slot / 2f
            val h = ((chartBottom - chartTop) * (b.value.toFloat() / maxV)).coerceAtLeast(dp(3f))
            barPaint.color = if (b.highlight) activeColor else idleColor
            rect.set(cx - barW / 2f, chartBottom - h, cx + barW / 2f, chartBottom)
            canvas.drawRoundRect(rect, dp(4f), dp(4f), barPaint)
            textPaint.color = if (b.highlight) activeColor else textColor
            if (b.value > 0) canvas.drawText(b.value.toString(), cx, chartBottom - h - dp(3f), textPaint)
            textPaint.color = textColor
            canvas.drawText(b.label, cx, height - dp(3f), textPaint)
        }
    }
}
