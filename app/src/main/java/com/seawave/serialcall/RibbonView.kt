package com.seawave.serialcall

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.FrameLayout
import kotlin.math.roundToInt

/**
 * A view whose background is one of the ribbon bitmaps cut from the design.
 * Height always follows the bitmap's aspect ratio, so the shape is never distorted.
 * Children are laid out inside the content rectangle (fractions of the bitmap bounds).
 */
class RibbonView(
    context: Context,
    drawableRes: Int,
    private val cl: Float = 0f,
    private val ct: Float = 0f,
    private val cr: Float = 1f,
    private val cb: Float = 1f,
    pressable: Boolean = true
) : FrameLayout(context) {

    private val ratio: Float

    init {
        val d = context.getDrawable(drawableRes)!!
        ratio = d.intrinsicHeight.toFloat() / d.intrinsicWidth.toFloat()
        (d as? BitmapDrawable)?.let {
            it.bitmap.setHasMipMap(true)
            it.paint.isFilterBitmap = true
        }
        background = d
        isClickable = pressable
        clipChildren = false
        clipToPadding = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (w * ratio).roundToInt()
        val l = (w * cl).roundToInt()
        val t = (h * ct).roundToInt()
        val r = (w * (1f - cr)).roundToInt()
        val b = (h * (1f - cb)).roundToInt()
        if (paddingLeft != l || paddingTop != t || paddingRight != r || paddingBottom != b) {
            setPadding(l, t, r, b)
        }
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        )
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        alpha = if (!isEnabled) 0.45f else if (pressed) 0.86f else 1f
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.45f
    }
}
