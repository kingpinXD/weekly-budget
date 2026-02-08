package com.example.weeklytotals

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class PieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Slice(
        val label: String,
        val value: Double,
        val color: Int
    )

    var slices: List<Slice> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private val slicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        isFakeBoldText = true
    }

    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E0E0E0")
    }

    private val emptyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#999999")
    }

    private val arcRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val radius = size / 2f - 8f

        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        val total = slices.sumOf { it.value }

        if (total <= 0 || slices.isEmpty()) {
            canvas.drawCircle(cx, cy, radius, emptyPaint)
            emptyTextPaint.textSize = size * 0.08f
            canvas.drawText("No data", cx, cy + emptyTextPaint.textSize * 0.35f, emptyTextPaint)
            return
        }

        var startAngle = -90f

        for (slice in slices) {
            val sweep = (slice.value / total * 360.0).toFloat()
            slicePaint.color = slice.color
            canvas.drawArc(arcRect, startAngle, sweep, true, slicePaint)

            // Draw label on slices that are large enough
            if (sweep > 20f) {
                val midAngle = Math.toRadians((startAngle + sweep / 2f).toDouble())
                val labelRadius = radius * 0.65f
                val lx = cx + labelRadius * cos(midAngle).toFloat()
                val ly = cy + labelRadius * sin(midAngle).toFloat()

                labelPaint.textSize = size * 0.06f
                val pct = String.format("%.0f%%", slice.value / total * 100)
                canvas.drawText(pct, lx, ly + labelPaint.textSize * 0.35f, labelPaint)
            }

            startAngle += sweep
        }
    }
}
