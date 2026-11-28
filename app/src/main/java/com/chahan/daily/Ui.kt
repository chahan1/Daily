package com.chahan.daily

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/** 用程式畫介面的小工具（不需要 XML 版面）。 */
fun Context.dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
fun Context.dpi(v: Int) = dp(v.toFloat()).toInt()
fun Context.col(id: Int) = ContextCompat.getColor(this, id)

fun Context.rounded(color: Int, radiusDp: Float = 16f, strokeColor: Int? = null): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp)
        if (strokeColor != null) setStroke(dpi(1), strokeColor)
    }

fun Context.vbox(padDp: Int = 0): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dpi(padDp), dpi(padDp), dpi(padDp), dpi(padDp))
}

fun Context.hbox(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}

fun Context.card(): LinearLayout = vbox(16).apply {
    background = rounded(col(R.color.card), 18f)
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dpi(12) }
}

fun Context.label(text: String, sizeSp: Float = 15f, bold: Boolean = false,
                  colorId: Int = R.color.text): TextView = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    setTextColor(col(colorId))
    if (bold) typeface = Typeface.DEFAULT_BOLD
    setLineSpacing(0f, 1.2f)
}

/** 圓角按鈕。filled=true 用品牌色底。 */
fun Context.pill(text: String, filled: Boolean = false, onClick: (View) -> Unit): TextView =
    TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dpi(16), dpi(10), dpi(16), dpi(10))
        if (filled) {
            setTextColor(col(R.color.white))
            background = rounded(col(R.color.brand), 22f)
        } else {
            setTextColor(col(R.color.text))
            background = rounded(col(R.color.soft), 22f)
        }
        isClickable = true
        setOnClickListener(onClick)
    }

fun View.lp(w: Int = ViewGroup.LayoutParams.WRAP_CONTENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
            weight: Float = 0f, top: Int = 0, end: Int = 0): View {
    layoutParams = LinearLayout.LayoutParams(w, h, weight).apply {
        topMargin = context.dpi(top); marginEnd = context.dpi(end)
    }
    return this
}

/** 最近幾天的睡眠長條圖（灰線＝7 小時），長條上方的數字是當天精神分數。 */
class SleepChart(ctx: Context) : View(ctx) {
    var data: List<Triple<String, Double, Int>> = emptyList()
        set(v) { field = v; invalidate() }

    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ctx.col(R.color.brand) }
    private val barLow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ctx.col(R.color.brand); alpha = 110 }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ctx.col(R.color.text_sub); strokeWidth = ctx.dp(1f); style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(ctx.dp(4f), ctx.dp(4f)), 0f)
    }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ctx.col(R.color.text_sub); textSize = ctx.dp(10f); textAlign = Paint.Align.CENTER
    }
    private val score = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ctx.col(R.color.text); textSize = ctx.dp(11f); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    /** 點某一天的長條時呼叫（參數是第幾天，0＝最舊）。 */
    var onPick: ((Int) -> Unit)? = null

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
        if (onPick == null || data.isEmpty()) return false
        if (e.action == android.view.MotionEvent.ACTION_UP) {
            val i = (e.x / (width.toFloat() / data.size)).toInt().coerceIn(0, data.size - 1)
            onPick?.invoke(i)
        }
        return true
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(View.MeasureSpec.getSize(w), context.dpi(150))
    }

    override fun onDraw(c: Canvas) {
        if (data.isEmpty()) return
        val top = context.dp(18f)
        val bottom = height - context.dp(18f)
        val maxH = 10.0
        fun y(h: Double) = (bottom - (bottom - top) * (h.coerceIn(0.0, maxH) / maxH)).toFloat()
        val slot = width.toFloat() / data.size
        val bw = slot * 0.55f
        data.forEachIndexed { i, (lab, sleep, energy) ->
            val cx = slot * i + slot / 2
            if (sleep >= 0) {
                c.drawRoundRect(cx - bw / 2, y(sleep), cx + bw / 2, bottom, context.dp(3f), context.dp(3f),
                    if (sleep >= 7) bar else barLow)
            }
            if (energy > 0) c.drawText(energy.toString(), cx, (if (sleep >= 0) y(sleep) else bottom) - context.dp(4f), score)
            c.drawText(lab, cx, height - context.dp(4f), txt)
        }
        val y7 = y(7.0)
        c.drawLine(0f, y7, width.toFloat(), y7, line)
        txt.textAlign = Paint.Align.LEFT
        c.drawText("7h", 0f, y7 - context.dp(3f), txt)
        txt.textAlign = Paint.Align.CENTER
    }
}
