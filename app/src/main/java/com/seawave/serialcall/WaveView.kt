package com.seawave.serialcall

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/** Soft translucent wave swooshes painted along the bottom edge of the screen. */
class WaveView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val d = resources.displayMetrics.density
    private val back = Path()
    private val front = Path()
    private val line = Path()
    private val backPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val frontPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * d
        color = 0x6637B8F2
    }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val wf = w.toFloat()
        val hf = h.toFloat()

        back.reset()
        back.moveTo(0f, hf * 0.50f)
        back.cubicTo(wf * 0.22f, hf * 0.00f, wf * 0.55f, hf * 0.95f, wf, hf * 0.22f)
        back.lineTo(wf, hf)
        back.lineTo(0f, hf)
        back.close()

        line.reset()
        line.moveTo(0f, hf * 0.58f)
        line.cubicTo(wf * 0.25f, hf * 0.12f, wf * 0.58f, hf * 1.00f, wf, hf * 0.34f)

        front.reset()
        front.moveTo(0f, hf * 0.76f)
        front.cubicTo(wf * 0.30f, hf * 0.40f, wf * 0.62f, hf * 1.05f, wf, hf * 0.52f)
        front.lineTo(wf, hf)
        front.lineTo(0f, hf)
        front.close()

        backPaint.shader = LinearGradient(
            0f, 0f, wf, 0f, 0x2E29B6F6, 0x1429B6F6, Shader.TileMode.CLAMP
        )
        frontPaint.shader = LinearGradient(
            0f, hf * 0.4f, 0f, hf, 0x3329B6F6, 0x0F29B6F6, Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(back, backPaint)
        canvas.drawPath(line, linePaint)
        canvas.drawPath(front, frontPaint)
    }
}
