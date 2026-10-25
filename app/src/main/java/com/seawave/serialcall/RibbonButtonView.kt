package com.seawave.serialcall

import android.content.Context
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Slanted blue ribbon used for buttons, section titles and the record banner.
 *
 * Kinds:
 *  - BUTTON : large tappable ribbon
 *  - SMALL  : compact tappable ribbon
 *  - TITLE  : non-interactive heading ribbon that sits on a card's top edge
 *  - BANNER : non-interactive empty ribbon; other views are laid over it
 */
class RibbonButtonView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Kind { BUTTON, SMALL, TITLE, BANNER }

    private val d = resources.displayMetrics.density

    var label: String = ""
        set(value) {
            field = value
            if (interactiveOrTitle()) contentDescription = value
            requestLayout()
            invalidate()
        }

    private var kind: Kind = Kind.BUTTON
    private var light = false
    private var iconDrawable: Drawable? = null
    private var textSizePx = 0f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        pathEffect = CornerPathEffect(5f * d)
    }
    private val lightFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFE6F4FD.toInt()
        pathEffect = CornerPathEffect(5f * d)
    }
    private val lightStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * d
        color = 0xFF8CCBF0.toInt()
        pathEffect = CornerPathEffect(5f * d)
    }
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val hatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * d
        strokeCap = Paint.Cap.ROUND
        color = 0x70FFFFFF
    }
    private val pressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x26000000
        pathEffect = CornerPathEffect(5f * d)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    private val raw = Path()
    private val rounded = Path()
    private val bandPath = Path()

    // geometry (px), recomputed in onSizeChanged
    private var gLeft = 0f
    private var gRight = 0f
    private var gTop = 0f
    private var gBottom = 0f
    private var gHeight = 0f
    private var gSlant = 0f

    init {
        var iconRes = 0
        var sizeAttr = -1f
        var kindOrdinal = 0
        var labelAttr: String? = null
        val a = context.obtainStyledAttributes(attrs, R.styleable.RibbonButtonView)
        try {
            kindOrdinal = a.getInt(R.styleable.RibbonButtonView_ribbonKind, 0)
            iconRes = a.getResourceId(R.styleable.RibbonButtonView_ribbonIcon, 0)
            sizeAttr = a.getDimension(R.styleable.RibbonButtonView_ribbonTextSize, -1f)
            light = a.getBoolean(R.styleable.RibbonButtonView_ribbonLight, false)
            labelAttr = a.getString(R.styleable.RibbonButtonView_ribbonLabel)
        } finally {
            a.recycle()
        }
        kind = Kind.values()[kindOrdinal.coerceIn(0, 3)]
        textSizePx = if (sizeAttr > 0f) sizeAttr else sp(
            when (kind) {
                Kind.BUTTON -> 19f
                Kind.SMALL -> 14f
                Kind.TITLE -> 16f
                Kind.BANNER -> 16f
            }
        )
        if (iconRes != 0) {
            iconDrawable = context.getDrawable(iconRes)?.mutate()
            iconDrawable?.setTint(if (light) 0xFF1B78C4.toInt() else 0xFFFFFFFF.toInt())
        }
        if (light) textPaint.color = 0xFF1B78C4.toInt()
        else textPaint.setShadowLayer(2f * d, 0f, 1f * d, 0x330A4A7A)

        val interactive = kind == Kind.BUTTON || kind == Kind.SMALL
        isClickable = interactive
        isFocusable = interactive
        if (kind == Kind.BANNER) importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        if (labelAttr != null) label = labelAttr
    }

    private fun interactiveOrTitle() = kind != Kind.BANNER

    private fun sp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private val padH get() = (if (kind == Kind.TITLE) 4f else 5f) * d
    private val padTop get() = 3f * d
    private val padBottom
        get() = when (kind) {
            Kind.BUTTON -> 11f * d
            Kind.SMALL -> 10f * d
            Kind.TITLE -> 7f * d
            Kind.BANNER -> 9f * d
        }
    private val defaultHeight
        get() = when (kind) {
            Kind.BUTTON -> 68f * d
            Kind.SMALL -> 58f * d
            Kind.TITLE -> 46f * d
            Kind.BANNER -> 76f * d
        }
    private val iconSize get() = (if (kind == Kind.SMALL) 18f else 22f) * d

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.45f
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = defaultHeight.toInt()
        textPaint.textSize = textSizePx
        val body = h - padTop - padBottom
        val icon = if (iconDrawable != null) iconSize + 8f * d else 0f
        val wanted = if (kind == Kind.TITLE) {
            textPaint.measureText(label) + icon + 2f * padH + body * 0.36f + 34f * d
        } else {
            200f * d
        }
        setMeasuredDimension(
            resolveSize(wanted.toInt(), widthMeasureSpec),
            resolveSize(h, heightMeasureSpec)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        gLeft = padH
        gRight = w - padH
        gTop = padTop
        gBottom = h - padBottom
        gHeight = gBottom - gTop
        gSlant = gHeight * 0.36f

        raw.reset()
        raw.moveTo(gLeft, gBottom)
        raw.lineTo(gLeft + gSlant, gTop)
        raw.lineTo(gRight, gTop)
        raw.lineTo(gRight - gSlant, gBottom)
        raw.close()
        rounded.reset()
        fillPaint.getFillPath(raw, rounded)

        fillPaint.shader = LinearGradient(
            gLeft, gTop, gRight, gBottom,
            0xFF25BDF6.toInt(), 0xFF0B74C4.toInt(), Shader.TileMode.CLAMP
        )
        glossPaint.shader = LinearGradient(
            0f, gTop, 0f, gTop + gHeight * 0.55f,
            0x3DFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP
        )
    }

    /** A slanted white band, [a]..[b] px in from the right edge, leaning like the ribbon. */
    private fun drawBand(c: Canvas, a: Float, b: Float, color: Int) {
        bandPath.reset()
        bandPath.moveTo(gRight - gSlant - b, gBottom)
        bandPath.lineTo(gRight - gSlant - a, gBottom)
        bandPath.lineTo(gRight - a, gTop)
        bandPath.lineTo(gRight - b, gTop)
        bandPath.close()
        bandPaint.color = color
        c.drawPath(bandPath, bandPaint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0 || gHeight <= 0f) return

        if (light) {
            canvas.drawPath(rounded, lightFill)
            canvas.drawPath(rounded, lightStroke)
        } else {
            fillPaint.setShadowLayer(6f * d, 0f, 3f * d, 0x400A74C6)
            canvas.drawPath(rounded, fillPaint)
            fillPaint.clearShadowLayer()

            canvas.save()
            canvas.clipPath(rounded)
            canvas.drawRect(gLeft, gTop, gRight, gTop + gHeight * 0.55f, glossPaint)
            drawBand(canvas, gHeight * 0.10f, gHeight * 0.56f, 0x2EFFFFFF)
            drawBand(canvas, gHeight * 0.66f, gHeight * 0.71f, 0x45FFFFFF)
            if (kind == Kind.BUTTON || kind == Kind.BANNER) {
                for (i in 0..2) {
                    val y = gBottom - (4f + i * 3.5f) * d
                    val shift = gSlant * (gBottom - y) / gHeight
                    val x2 = gRight - gSlant - gHeight * 0.06f + shift
                    val x1 = x2 - gHeight * (0.62f - i * 0.12f)
                    canvas.drawLine(x1, y, x2, y, hatchPaint)
                }
            }
            canvas.restore()

            if (kind == Kind.BUTTON || kind == Kind.SMALL) {
                val y0 = gBottom + 3.5f * d
                val t = 3f * d
                val x1 = gLeft + (gRight - gLeft) * 0.14f
                val x2 = gLeft + (gRight - gLeft) * 0.60f
                bandPath.reset()
                bandPath.moveTo(x1, y0 + t)
                bandPath.lineTo(x1 + t * 1.2f, y0)
                bandPath.lineTo(x2 + t * 1.2f, y0)
                bandPath.lineTo(x2, y0 + t)
                bandPath.close()
                bandPaint.color = 0x9954C4F6.toInt()
                canvas.drawPath(bandPath, bandPaint)
            }
        }

        if (isPressed && (kind == Kind.BUTTON || kind == Kind.SMALL)) {
            canvas.drawPath(rounded, pressPaint)
        }

        drawContent(canvas)
    }

    private fun drawContent(canvas: Canvas) {
        val icon = iconDrawable
        if (label.isEmpty() && icon == null) return

        val iconW = if (icon != null) iconSize else 0f
        val gap = if (icon != null && label.isNotEmpty()) 9f * d else 0f
        val avail = (gRight - gLeft) - gSlant - 14f * d - iconW - gap

        var size = textSizePx
        textPaint.textSize = size
        while (label.isNotEmpty() && textPaint.measureText(label) > avail && size > 10f * d) {
            size -= 0.5f * d
            textPaint.textSize = size
        }
        val textW = if (label.isEmpty()) 0f else textPaint.measureText(label)
        val total = iconW + gap + textW
        val cx = (gLeft + gRight) / 2f
        val cy = (gTop + gBottom) / 2f
        var x = cx - total / 2f

        if (icon != null) {
            icon.setBounds(
                x.toInt(), (cy - iconW / 2f).toInt(),
                (x + iconW).toInt(), (cy + iconW / 2f).toInt()
            )
            icon.draw(canvas)
            x += iconW + gap
        }
        if (label.isNotEmpty()) {
            val baseline = cy - (textPaint.ascent() + textPaint.descent()) / 2f
            canvas.drawText(label, x, baseline, textPaint)
        }
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        if (kind == Kind.BUTTON || kind == Kind.SMALL) {
            info.className = android.widget.Button::class.java.name
        }
    }
}
