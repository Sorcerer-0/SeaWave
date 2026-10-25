package com.seawave.serialcall

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.widget.TextView

/** Bengali text uses the Bengali font, English letters/digits use the English font. */
object SeaFonts {
    lateinit var bn: Typeface
    lateinit var en: Typeface
    @Volatile var ready = false

    fun init(ctx: Context) {
        if (ready) return
        bn = ctx.resources.getFont(R.font.bn_font)
        en = ctx.resources.getFont(R.font.en_font)
        ready = true
    }

    private class FontSpan(val tf: Typeface) : MetricAffectingSpan() {
        override fun updateDrawState(p: TextPaint) { p.typeface = tf }
        override fun updateMeasureState(p: TextPaint) { p.typeface = tf }
    }

    private fun isBn(c: Char) = c in '\u0980'..'\u09FF' || c == '\u200C' || c == '\u200D'

    /** Applies per-script font spans in place (removes old ones first). */
    fun apply(sp: Spannable) {
        if (!ready) return
        for (old in sp.getSpans(0, sp.length, FontSpan::class.java)) sp.removeSpan(old)
        val n = sp.length
        if (n == 0) return
        var runStart = 0
        var runBn = false
        var started = false
        for (i in 0 until n) {
            val c = sp[i]
            val bnChar = isBn(c)
            val strong = bnChar || Character.isLetterOrDigit(c)
            if (!started) {
                if (strong) { runBn = bnChar; started = true }
                continue
            }
            if (strong && bnChar != runBn) {
                sp.setSpan(FontSpan(if (runBn) bn else en), runStart, i, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                runStart = i
                runBn = bnChar
            }
        }
        sp.setSpan(FontSpan(if (started && runBn) bn else en), runStart, n, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    fun spanned(cs: CharSequence): CharSequence {
        val sp = SpannableString(cs)
        apply(sp)
        return sp
    }
}

class SeaTextView(context: Context) : TextView(context) {
    override fun setText(text: CharSequence?, type: BufferType?) {
        if (text == null || !SeaFonts.ready) super.setText(text, type)
        else super.setText(SeaFonts.spanned(text), BufferType.SPANNABLE)
    }
}
