package com.example.weeklytotals

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class BudgetGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var spent: Double = 0.0
        set(value) {
            field = value
            invalidate()
        }

    var budget: Double = 0.0
        set(value) {
            field = value
            invalidate()
        }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#E0E0E0")
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val spentTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val budgetTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#B0B0B0")
    }

    private val arcRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = min(width, height).toFloat()
        val strokeWidth = size * 0.1f
        val padding = strokeWidth / 2f + 4f

        trackPaint.strokeWidth = strokeWidth
        arcPaint.strokeWidth = strokeWidth

        val cx = width / 2f
        val cy = height / 2f
        val radius = size / 2f - padding

        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // Draw background track
        canvas.drawArc(arcRect, 0f, 360f, false, trackPaint)

        // Calculate fill ratio (spent can be negative due to refunds)
        val effectiveSpent = spent.coerceAtLeast(0.0)
        val ratio = if (budget > 0) (effectiveSpent / budget).coerceAtMost(1.0) else 0.0
        val sweepAngle = (ratio * 360.0).toFloat()
        val overBudget = budget > 0 && spent > budget
        val hasRefundBonus = spent < 0 // More refunds than spending

        // Arc color: blue → orange → red, or green if refund bonus
        arcPaint.color = when {
            hasRefundBonus -> Color.parseColor("#009688")
            overBudget -> Color.parseColor("#F44336")
            ratio <= 0.5 -> interpolateColor(
                Color.parseColor("#2196F3"),
                Color.parseColor("#FF9800"),
                (ratio / 0.5).toFloat()
            )
            else -> interpolateColor(
                Color.parseColor("#FF9800"),
                Color.parseColor("#F44336"),
                ((ratio - 0.5) / 0.5).toFloat()
            )
        }

        if (overBudget) {
            canvas.drawArc(arcRect, -90f, 360f, false, arcPaint)
        } else if (sweepAngle > 0f) {
            canvas.drawArc(arcRect, -90f, sweepAngle, false, arcPaint)
        }

        // Center text: amount remaining
        val remaining = budget - spent
        spentTextPaint.textSize = size * 0.18f
        spentTextPaint.color = when {
            overBudget -> Color.parseColor("#F44336")
            remaining > budget -> Color.parseColor("#009688")
            else -> Color.WHITE
        }
        val remainStr = if (overBudget) String.format("-$%.0f", -remaining) else String.format("$%.0f", remaining)
        canvas.drawText(remainStr, cx, cy + spentTextPaint.textSize * 0.35f, spentTextPaint)
    }

    private fun interpolateColor(startColor: Int, endColor: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        val sr = Color.red(startColor)
        val sg = Color.green(startColor)
        val sb = Color.blue(startColor)
        val er = Color.red(endColor)
        val eg = Color.green(endColor)
        val eb = Color.blue(endColor)
        return Color.rgb(
            (sr + (er - sr) * f).toInt(),
            (sg + (eg - sg) * f).toInt(),
            (sb + (eb - sb) * f).toInt()
        )
    }
}
