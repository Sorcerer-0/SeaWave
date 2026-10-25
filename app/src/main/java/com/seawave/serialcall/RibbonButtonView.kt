package com.seawave.serialcall

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.LinearGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo

/** Angled blue-white ribbon-style button inspired by the supplied reference. */
class RibbonButtonView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    var label: String = ""
        set(value) { field = value; contentDescription = value; invalidate() }
    var textSizeSp: Float = 17f
        set(value) { field = value; invalidate() }

    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
    }
    private val path = Path()

    init {
        isClickable = true
        isFocusable = true
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val inset = 5f * resources.displayMetrics.density
        path.reset()
        path.moveTo(inset + 24f, inset)
        path.lineTo(w - inset - 18f, inset)
        path.lineTo(w - inset, inset + 19f)
        path.lineTo(w - inset - 29f, h - inset - 13f)
        path.lineTo(inset + 19f, h - inset - 13f)
        path.lineTo(inset, h - inset - 1f)
        path.lineTo(inset + 24f, inset)
        path.close()

        shapePaint.shader = LinearGradient(0f, inset, w, h, 0xFF18B9F1.toInt(), 0xFF0076B8.toInt(), Shader.TileMode.CLAMP)
        shapePaint.setShadowLayer(7f, 0f, 4f, 0x2600527A)
        canvas.drawPath(path, shapePaint)
        shapePaint.clearShadowLayer()
        shapePaint.shader = null

        accentPaint.color = 0xFFF8FDFF.toInt()
        accentPaint.strokeWidth = 2f
        accentPaint.style = Paint.Style.STROKE
        canvas.drawLine(inset + 28f, inset + 7f, w - inset - 37f, inset + 7f, accentPaint)
        canvas.drawLine(inset + 12f, h - inset - 8f, w - inset - 53f, h - inset - 8f, accentPaint)
        accentPaint.style = Paint.Style.FILL
        val fold = Path().apply {
            moveTo(w - inset - 24f, inset + 1f)
            lineTo(w - inset - 8f, inset + 1f)
            lineTo(w - inset - 1f, inset + 18f)
            lineTo(w - inset - 18f, inset + 18f)
            close()
        }
        accentPaint.color = 0xFFBDEFFF.toInt()
        canvas.drawPath(fold, accentPaint)

        textPaint.textSize = textSizeSp * resources.displayMetrics.scaledDensity
        val text = label
        val maxWidth = w - 72f * resources.displayMetrics.density
        val fitted = if (textPaint.measureText(text) > maxWidth && maxWidth > 0) {
            android.text.TextUtils.ellipsize(text, android.text.TextPaint(textPaint), maxWidth, android.text.TextUtils.TruncateAt.END).toString()
        } else text
        val x = w / 2f
        val y = h / 2f - (textPaint.ascent() + textPaint.descent()) / 2f + 2f
        canvas.drawText(fitted, x, y, textPaint)
        if (isPressed) {
            accentPaint.color = 0x33FFFFFF
            canvas.drawPath(path, accentPaint)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (64 * resources.displayMetrics.density).toInt()
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        val width = resolveSize((220 * resources.displayMetrics.density).toInt(), widthMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.Button::class.java.name
        info.isClickable = true
    }
}
