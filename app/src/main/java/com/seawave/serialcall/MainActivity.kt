package com.seawave.serialcall

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.IOException
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private enum class Page { HOME, RESULT, SETTINGS }

    private lateinit var store: RecordStore
    private var dataset: Dataset = Dataset.EMPTY
    private var selected: SeaRecord? = null
    private var page = Page.HOME
    private var homeNotice: String? = null
    private var lastCallAt = 0L
    private var pendingExportText: String? = null
    private var importing = false

    // scale: 1 reference pixel (design is 776px wide) -> device pixels
    private var u = 1f

    // views
    private lateinit var root: FrameLayout
    private lateinit var mainCol: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var homePage: LinearLayout
    private lateinit var resultPage: LinearLayout
    private lateinit var settingsPage: LinearLayout
    private lateinit var totalCount: TextView
    private lateinit var slInput: EditText
    private lateinit var clearBtn: ImageView
    private lateinit var emptyTitle: TextView
    private lateinit var emptySub: TextView
    private lateinit var emptyImport: RibbonView
    private lateinit var recordCounter: TextView
    private lateinit var nameView: TextView
    private lateinit var noView: TextView
    private lateinit var slBadge: TextView
    private lateinit var rowMobile: TextView
    private lateinit var rowDivision: TextView
    private lateinit var rowUni: TextView
    private lateinit var rowEmail: TextView
    private lateinit var rowRemark: TextView
    private lateinit var callBtn: RibbonView
    private lateinit var resultMsg: TextView
    private lateinit var settingsMsg: TextView
    private lateinit var settingsCount: TextView

    private var backCallback: Any? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        val dm = resources.displayMetrics
        val maxW = minOf(dm.widthPixels, (520 * dm.density).toInt())
        u = maxW / REF_W

        store = RecordStore(applicationContext)
        dataset = store.load()

        buildUi()
        setContentView(root)
        refreshTotals()
        refreshHomeCard()
        showPage(Page.HOME)

        if (Build.VERSION.SDK_INT >= 33) {
            val cb = OnBackInvokedCallback { handleBack() }
            backCallback = cb
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb)
        }
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= 33) {
            (backCallback as? OnBackInvokedCallback)?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        }
        super.onDestroy()
    }

    @Deprecated("Used on Android 12 and below")
    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT >= 33) { handleBack(); return }
        if (page != Page.HOME) showPage(Page.HOME) else super.onBackPressed()
    }

    private fun handleBack() {
        if (page != Page.HOME) showPage(Page.HOME) else finish()
    }

    // ------------------------------------------------------------------ helpers

    private fun r(v: Float): Int = (v * u).roundToInt()
    private fun r(v: Int): Int = r(v.toFloat())
    private fun d(v: Int): Int = if (v < 0) v else r(v)

    private fun lin(w: Int, h: Int, l: Int = 0, t: Int = 0, rr: Int = 0, b: Int = 0, g: Int = -1): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(d(w), d(h))
        p.setMargins(r(l), r(t), r(rr), r(b))
        if (g != -1) p.gravity = g
        return p
    }

    private fun flp(w: Int, h: Int, l: Int = 0, t: Int = 0, rr: Int = 0, b: Int = 0, g: Int = Gravity.TOP or Gravity.START): FrameLayout.LayoutParams {
        val p = FrameLayout.LayoutParams(d(w), d(h), g)
        p.setMargins(r(l), r(t), r(rr), r(b))
        return p
    }

    private fun tv(text: String, sizeRef: Float, color: Int, style: Int = REGULAR): TextView = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_PX, sizeRef * u)
        setTextColor(color)
        includeFontPadding = false
        typeface = when (style) {
            BOLD -> Typeface.create("sans-serif", Typeface.BOLD)
            MEDIUM -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            else -> Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }

    private fun rounded(fill: Int, radiusRef: Float, stroke: Int = 0, strokeRef: Float = 0f): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radiusRef * u
            if (stroke != 0) setStroke(max(1, (strokeRef * u).roundToInt()), stroke)
        }

    private fun icon(res: Int, sizeRef: Int, padRef: Int = 0): ImageView = ImageView(this).apply {
        setImageResource(res)
        scaleType = ImageView.ScaleType.FIT_CENTER
        val p = r(padRef)
        setPadding(p, p, p, p)
    }

    private fun ribbonText(rb: RibbonView, iconRes: Int?, text: String, sizeRef: Float, iconRef: Int, gapRef: Int): TextView {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        if (iconRes != null) row.addView(icon(iconRes, iconRef), lin(iconRef, iconRef, rr = gapRef))
        val t = tv(text, sizeRef, Color.WHITE, BOLD).apply {
            setSingleLine(true)
            setShadowLayer(r(3).toFloat(), 0f, r(2).toFloat(), 0x440A3A66)
        }
        row.addView(t, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        rb.addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return t
    }

    private fun softShadow(v: View) {
        v.elevation = r(7).toFloat()
        if (Build.VERSION.SDK_INT >= 28) {
            v.outlineAmbientShadowColor = 0x334A78A8
            v.outlineSpotShadowColor = 0x334A78A8
        }
    }

    @Suppress("DEPRECATION")
    private fun insetsOf(ins: WindowInsets): IntArray =
        if (Build.VERSION.SDK_INT >= 30) {
            val sb = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val ime = ins.getInsets(WindowInsets.Type.ime())
            intArrayOf(sb.left, sb.top, sb.right, sb.bottom, ime.bottom)
        } else {
            intArrayOf(ins.systemWindowInsetLeft, ins.systemWindowInsetTop, ins.systemWindowInsetRight, ins.stableInsetBottom, ins.stableInsetBottom)
        }

    private fun fmt(n: Int): String = NumberFormat.getInstance(Locale.US).format(n)

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(slInput.windowToken, 0)
    }

    // ------------------------------------------------------------------ UI

    private fun buildUi() {
        root = FrameLayout(this).apply { setBackgroundResource(R.drawable.bg_page) }

        // wave (background art) and footer
        val wave = ImageView(this).apply {
            setImageResource(R.drawable.wave)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_XY
        }
        root.addView(wave, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply { bottomMargin = r(66) })

        val footer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        footer.addView(tv("One press = one call  •  Manual serial control", 21f, 0xFF4F657B.toInt()))
        footer.addView(tv("Developed by Abdullah Al Saad", 20f, 0xFF2A6FA8.toInt(), MEDIUM), lin(-2, -2, t = 7))
        root.addView(footer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply { bottomMargin = r(14) })

        // main column: header + scrolling pages
        mainCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        mainCol.addView(buildHeader(), LinearLayout.LayoutParams(r(776), r(115)))
        scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER }
        mainCol.addView(scroll, LinearLayout.LayoutParams(r(776), 0, 1f))

        val pages = FrameLayout(this)
        homePage = buildHome()
        resultPage = buildResult()
        settingsPage = buildSettings()
        pages.addView(homePage, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        pages.addView(resultPage, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        pages.addView(settingsPage, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        scroll.addView(pages, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(mainCol, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply { bottomMargin = r(165) })

        root.setOnApplyWindowInsetsListener { v, ins ->
            val a = insetsOf(ins)
            v.setPadding(a[0], a[1], a[2], a[3])
            val extra = max(0, a[4] - a[3] - r(165))
            (mainCol.layoutParams as FrameLayout.LayoutParams).bottomMargin = r(165) + extra
            mainCol.requestLayout()
            ins
        }
    }

    private fun buildHeader(): View {
        val h = FrameLayout(this)
        h.addView(ImageView(this).apply { setImageResource(R.drawable.logo); scaleType = ImageView.ScaleType.FIT_CENTER },
            flp(120, 80, l = 50, t = 30))
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(tv("SeaWave", 46f, 0xFF10284A.toInt(), BOLD))
        titles.addView(tv("SERIAL CONTACT DIALER", 19.5f, 0xFF3E5670.toInt()).apply { letterSpacing = 0.02f }, lin(-2, -2, t = 5))
        h.addView(titles, flp(-2, -2, l = 188, t = 30))
        val gear = icon(R.drawable.ic_settings, 62, 8).apply {
            setOnClickListener { showPage(if (page == Page.SETTINGS) Page.HOME else Page.SETTINGS) }
        }
        h.addView(gear, flp(62, 62, l = 670, t = 43))
        return h
    }

    // ---- HOME ----

    private fun buildHome(): LinearLayout {
        val p = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // total records banner
        val banner = RibbonView(this, R.drawable.ribbon_total, 0.084f, 0f, 0.80f, 1f, pressable = false)
        val bRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        bRow.addView(icon(R.drawable.ic_database_white, 52), lin(52, 52, rr = 48))
        val bCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bCol.addView(tv("মোট রেকর্ড", 26f, Color.WHITE))
        totalCount = tv("0", 40f, Color.WHITE, BOLD)
        bCol.addView(totalCount, lin(-2, -2, t = 4))
        bRow.addView(bCol)
        banner.addView(bRow, FrameLayout.LayoutParams(-1, -1))
        p.addView(banner, lin(690, -2, l = 45, t = 45))

        // serial number card
        val box = FrameLayout(this).apply { clipChildren = false }
        val card = View(this).apply { background = rounded(Color.WHITE, 24f, 0xFFE4EDF6.toInt(), 2f) }
        softShadow(card)
        box.addView(card, flp(685, 248, l = 3, t = 15))

        val inputBox = FrameLayout(this).apply { background = rounded(Color.WHITE, 16f, 0xFFB5CFE3.toInt(), 2f) }
        slInput = EditText(this).apply {
            background = null
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 44f * u)
            setTextColor(0xFF10243C.toInt())
            setHintTextColor(0xFF9FB2C4.toInt())
            hint = "সিরিয়াল নম্বর"
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setSingleLine(true)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            setPadding(r(40), 0, r(90), 0)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) { doSearch(); true } else false
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    clearBtn.visibility = if (s.isNullOrEmpty()) View.INVISIBLE else View.VISIBLE
                    if (homeNotice != null) { homeNotice = null; refreshHomeCard() }
                }
            })
        }
        inputBox.addView(slInput, FrameLayout.LayoutParams(-1, -1))
        clearBtn = icon(R.drawable.ic_close, 70, 19).apply {
            visibility = View.INVISIBLE
            setOnClickListener { slInput.setText(""); slInput.requestFocus() }
        }
        inputBox.addView(clearBtn, flp(70, 70, rr = 18, g = Gravity.END or Gravity.CENTER_VERTICAL))
        box.addView(inputBox, flp(605, 97, l = 45, t = 133))

        val tab = RibbonView(this, R.drawable.ribbon_serial_tab, 0.15f, 0.08f, 0.67f, 0.92f, pressable = false)
        ribbonText(tab, null, "সিরিয়াল নম্বর দিন", 40f, 0, 0)
        box.addView(tab, flp(560, -2))
        p.addView(box, lin(690, 263, l = 45, t = 55))

        // search button
        val search = RibbonView(this, R.drawable.btn_search, 0.12f, 0.12f, 0.80f, 0.72f)
        ribbonText(search, R.drawable.ic_search, "সন্ধান করুন", 50f, 54, 40)
        search.setOnClickListener { doSearch() }
        p.addView(search, lin(690, -2, l = 45, t = 27))

        p.addView(tv("সম্পর্কে ফলাফল", 29f, 0xFF4B6278.toInt()), lin(-2, -2, l = 52, t = 40))

        // empty / notice card
        val emptyCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = rounded(Color.WHITE, 24f, 0xFFE4EDF6.toInt(), 2f)
            minimumHeight = r(392)
            setPadding(r(30), r(73), r(30), r(30))
        }
        softShadow(emptyCard)
        emptyCard.addView(icon(R.drawable.ic_avatar, 110), lin(110, 110))
        emptyTitle = tv("", 34f, 0xFF1F3A57.toInt(), MEDIUM).apply { gravity = Gravity.CENTER }
        emptySub = tv("", 27f, 0xFF5A7188.toInt()).apply { gravity = Gravity.CENTER }
        emptyCard.addView(emptyTitle, lin(-2, -2, t = 31))
        emptyCard.addView(emptySub, lin(-2, -2, t = 17))
        emptyImport = RibbonView(this, R.drawable.btn_export, 0.10f, 0.05f, 0.92f, 1f)
        ribbonText(emptyImport, R.drawable.ic_upload_white, "JSON ইম্পোর্ট", 26f, 38, 14)
        emptyImport.setOnClickListener { openPicker() }
        emptyCard.addView(emptyImport, lin(334, -2, t = 18))
        p.addView(emptyCard, lin(686, -2, l = 46, t = 22, b = 40))
        return p
    }

    private fun refreshHomeCard() {
        val notice = homeNotice
        when {
            notice != null -> {
                emptyTitle.text = notice
                emptySub.visibility = View.GONE
                emptyImport.visibility = if (dataset.size == 0) View.VISIBLE else View.GONE
            }
            dataset.size == 0 -> {
                emptyTitle.text = "কোনো ডেটা লোড করা নেই"
                emptySub.text = "একটি JSON ফাইল ইম্পোর্ট করুন"
                emptySub.visibility = View.VISIBLE
                emptyImport.visibility = View.VISIBLE
            }
            else -> {
                emptyTitle.text = "এখনো কোনো রেকর্ড সিলেক্ট করা হয়নি"
                emptySub.text = "সিরিয়াল নম্বর দিয়ে “সন্ধান করুন” চাপুন"
                emptySub.visibility = View.VISIBLE
                emptyImport.visibility = View.GONE
            }
        }
    }

    private fun refreshTotals() {
        totalCount.text = fmt(dataset.size)
        settingsCount.text = fmt(dataset.size)
    }

    // ---- RESULT ----

    private fun navRow(label: String, onBack: () -> Unit, rightText: TextView?): View {
        val row = FrameLayout(this)
        val back = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { onBack() }
        }
        back.addView(icon(R.drawable.ic_back, 40), lin(40, 40, l = 48, rr = 26))
        back.addView(tv(label, 30f, 0xFF28425E.toInt()))
        row.addView(back, flp(-2, 60, g = Gravity.START or Gravity.CENTER_VERTICAL))
        if (rightText != null) row.addView(rightText, flp(-2, -2, rr = 29, g = Gravity.END or Gravity.CENTER_VERTICAL))
        return row
    }

    private fun infoRow(iconRes: Int, label: String, valueSize: Float, actionIcon: Int?, onAction: (() -> Unit)?, divider: Boolean): Pair<FrameLayout, TextView> {
        val row = FrameLayout(this)
        row.addView(icon(iconRes, 38), flp(38, 38, l = 39, g = Gravity.START or Gravity.CENTER_VERTICAL))
        row.addView(tv(label, 27f, 0xFF3C5470.toInt()).apply { setSingleLine(true); ellipsize = TextUtils.TruncateAt.END },
            flp(215, -2, l = 118, g = Gravity.START or Gravity.CENTER_VERTICAL))
        val value = tv("—", valueSize, 0xFF10243C.toInt(), if (valueSize >= 30f) BOLD else MEDIUM).apply {
            setSingleLine(true); ellipsize = TextUtils.TruncateAt.END
        }
        row.addView(value, flp(290, -2, l = 338, g = Gravity.START or Gravity.CENTER_VERTICAL))
        if (actionIcon != null) {
            row.addView(icon(actionIcon, 64, 15).apply { setOnClickListener { onAction?.invoke() } },
                flp(64, 64, l = 631, g = Gravity.START or Gravity.CENTER_VERTICAL))
        }
        if (divider) {
            row.addView(View(this).apply { setBackgroundColor(0xFFE3EBF3.toInt()) },
                FrameLayout.LayoutParams(r(640), max(1, r(1.5f)), Gravity.BOTTOM or Gravity.START).apply { leftMargin = r(36) })
        }
        return Pair(row, value)
    }

    private fun buildResult(): LinearLayout {
        val p = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        recordCounter = tv("", 30f, 0xFF28425E.toInt())
        p.addView(navRow("নতুনভাবে খুঁজুন", { newSearch() }, recordCounter), lin(-1, 60, t = 45))

        val box = FrameLayout(this).apply { clipChildren = false }
        val card = FrameLayout(this).apply { background = rounded(Color.WHITE, 24f, 0xFFE4EDF6.toInt(), 2f) }
        softShadow(card)
        box.addView(card, flp(708, 764, l = 4, t = 13))

        card.addView(icon(R.drawable.ic_avatar, 116), flp(116, 116, l = 33, t = 105))
        nameView = tv("", 42f, 0xFF10243C.toInt(), BOLD).apply { setSingleLine(true); ellipsize = TextUtils.TruncateAt.END }
        card.addView(nameView, flp(350, -2, l = 185, t = 112))
        noView = tv("", 28f, 0xFF4B6278.toInt()).apply { setSingleLine(true); ellipsize = TextUtils.TruncateAt.END }
        card.addView(noView, flp(350, -2, l = 185, t = 172))
        slBadge = tv("", 24f, Color.WHITE, MEDIUM).apply {
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_badge)
            setSingleLine(true)
        }
        card.addView(slBadge, flp(116, 38, l = 560, t = 124))
        card.addView(View(this).apply { setBackgroundColor(0xFFE3EBF3.toInt()) }, flp(491, 2, l = 185, t = 232))

        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val m = infoRow(R.drawable.ic_phone, "মোবাইল নম্বর", 36f, R.drawable.ic_copy, { copyText(rowMobile.text.toString()) }, true)
        val dv = infoRow(R.drawable.ic_building, "ডিভিশন", 36f, null, null, true)
        val un = infoRow(R.drawable.ic_school, "বিশ্ববিদ্যালয়", 36f, null, null, true)
        val em = infoRow(R.drawable.ic_mail, "ইমেইল", 25f, R.drawable.ic_copy, { copyText(rowEmail.text.toString()) }, true)
        val rm = infoRow(R.drawable.ic_doc, "মন্তব্য (Remarks)", 36f, R.drawable.ic_edit, { editRemark() }, false)
        rowMobile = m.second; rowDivision = dv.second; rowUni = un.second; rowEmail = em.second; rowRemark = rm.second
        for (x in listOf(m, dv, un, em, rm)) rows.addView(x.first, lin(-1, 102))
        card.addView(rows, flp(-1, -2, t = 232))

        val tab = RibbonView(this, R.drawable.ribbon_contact_tab, 0.15f, 0f, 0.645f, 0.88f, pressable = false)
        ribbonText(tab, null, "যোগাযোগের তথ্য", 38f, 0, 0)
        box.addView(tab, flp(547, -2))
        p.addView(box, lin(708, 777, l = 44, t = 27))

        callBtn = RibbonView(this, R.drawable.btn_call, 0.12f, 0f, 0.84f, 1f)
        ribbonText(callBtn, R.drawable.ic_call_white, "কল করুন", 52f, 56, 42)
        callBtn.setOnClickListener { onCallPressed() }
        p.addView(callBtn, lin(710, -2, l = 46, t = 41))

        val small = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val edit = RibbonView(this, R.drawable.btn_edit_remark, 0.10f, 0.05f, 0.915f, 1f)
        ribbonText(edit, R.drawable.ic_edit_note_white, "মন্তব্য যোগ/সম্পাদনা", 26f, 38, 18)
        edit.setOnClickListener { editRemark() }
        val exp = RibbonView(this, R.drawable.btn_export, 0.10f, 0.05f, 0.92f, 1f)
        ribbonText(exp, R.drawable.ic_download_white, "JSON এক্সপোর্ট", 26f, 38, 18)
        exp.setOnClickListener { exportJson() }
        small.addView(edit, lin(373, -2))
        small.addView(exp, lin(334, -2, l = 3))
        p.addView(small, lin(-2, -2, l = 48, t = 7))

        resultMsg = pill()
        p.addView(resultMsg, lin(676, -2, l = 50, t = 22, b = 30))
        return p
    }

    private fun pill(): TextView = tv("", 25f, 0xFF1D4F78.toInt()).apply {
        background = rounded(0xFFEAF5FD.toInt(), 16f, 0xFFB8DAF0.toInt(), 1.5f)
        setPadding(r(26), r(18), r(26), r(18))
        visibility = View.GONE
    }

    private fun setPill(v: TextView, text: String?) {
        v.text = text.orEmpty()
        v.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    private fun bindRecord(rec: SeaRecord) {
        nameView.text = rec.name.ifBlank { "নাম নেই" }
        noView.text = "No: " + rec.no.ifBlank { "—" }
        slBadge.text = "SL: ${rec.sl}"
        recordCounter.text = "রেকর্ড: ${rec.sl} / ${fmt(dataset.size)}"
        rowMobile.text = rec.mobile.ifBlank { "—" }
        rowDivision.text = rec.division.ifBlank { "—" }
        rowUni.text = rec.university.ifBlank { "—" }
        rowEmail.text = rec.email.ifBlank { "—" }
        rowRemark.text = rec.remarks.ifBlank { "—" }
        callBtn.isEnabled = Phone.dialable(rec.mobile) != null
    }

    private fun copyText(text: String) {
        if (text.isBlank() || text == "—") return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("SeaWave", text))
        Toast.makeText(this, "কপি করা হয়েছে", Toast.LENGTH_SHORT).show()
    }

    // ---- SETTINGS ----

    private fun buildSettings(): LinearLayout {
        val p = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        p.addView(navRow("সেটিংস", { showPage(Page.HOME) }, null), lin(-1, 60, t = 45))

        val box = FrameLayout(this).apply { clipChildren = false }
        val card = FrameLayout(this).apply { background = rounded(Color.WHITE, 24f, 0xFFE4EDF6.toInt(), 2f) }
        softShadow(card)
        box.addView(card, flp(708, 290, l = 4, t = 13))
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val a = infoRow(R.drawable.ic_database, "লোড করা রেকর্ড", 36f, null, null, true)
        val b = infoRow(R.drawable.ic_doc, "অ্যাপ", 30f, null, null, false)
        settingsCount = a.second
        b.second.text = "SeaWave 1.0"
        rows.addView(a.first, lin(-1, 102))
        rows.addView(b.first, lin(-1, 102))
        card.addView(rows, flp(-1, -2, t = 62))
        val tab = RibbonView(this, R.drawable.ribbon_contact_tab, 0.15f, 0f, 0.645f, 0.88f, pressable = false)
        ribbonText(tab, null, "সেটিংস", 38f, 0, 0)
        box.addView(tab, flp(547, -2))
        p.addView(box, lin(708, 303, l = 44, t = 27))

        val imp = RibbonView(this, R.drawable.btn_search, 0.12f, 0.12f, 0.80f, 0.72f)
        ribbonText(imp, R.drawable.ic_upload_white, "JSON ইম্পোর্ট", 46f, 54, 36)
        imp.setOnClickListener { openPicker() }
        p.addView(imp, lin(690, -2, l = 45, t = 41))

        val exp = RibbonView(this, R.drawable.btn_call, 0.12f, 0f, 0.84f, 1f)
        ribbonText(exp, R.drawable.ic_download_white, "JSON এক্সপোর্ট", 46f, 54, 36)
        exp.setOnClickListener { exportJson() }
        p.addView(exp, lin(710, -2, l = 46, t = 14))

        settingsMsg = pill()
        p.addView(settingsMsg, lin(676, -2, l = 50, t = 22, b = 30))
        return p
    }

    // ------------------------------------------------------------------ navigation

    private fun showPage(p: Page) {
        page = p
        homePage.visibility = if (p == Page.HOME) View.VISIBLE else View.GONE
        resultPage.visibility = if (p == Page.RESULT) View.VISIBLE else View.GONE
        settingsPage.visibility = if (p == Page.SETTINGS) View.VISIBLE else View.GONE
        if (p != Page.RESULT) setPill(resultMsg, null)
        if (p != Page.SETTINGS) setPill(settingsMsg, null)
        scroll.scrollTo(0, 0)
        if (p != Page.HOME && ::slInput.isInitialized) hideKeyboard()
    }

    private fun say(text: String?) {
        when (page) {
            Page.HOME -> { homeNotice = text; refreshHomeCard() }
            Page.RESULT -> setPill(resultMsg, text)
            Page.SETTINGS -> setPill(settingsMsg, text)
        }
    }

    private fun newSearch() {
        selected = null
        slInput.setText("")
        homeNotice = null
        refreshHomeCard()
        showPage(Page.HOME)
        slInput.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(slInput, InputMethodManager.SHOW_IMPLICIT)
    }

    // ------------------------------------------------------------------ search

    private fun doSearch() {
        val sl = slInput.text.toString().trim()
        hideKeyboard()
        if (sl.isEmpty()) return say("প্রথমে সিরিয়াল নম্বর লিখুন।")
        if (!sl.all { it in '0'..'9' }) return say("সিরিয়াল নম্বর শুধু সংখ্যা হতে হবে।")
        if (dataset.size == 0) return say("আগে একটি JSON ফাইল ইম্পোর্ট করুন।")
        val matches = dataset.lookup(sl)
        when {
            matches.isEmpty() -> say("সিরিয়াল $sl এর কোনো রেকর্ড পাওয়া যায়নি।")
            matches.size > 1 -> say("সিরিয়াল $sl একাধিকবার আছে, তাই কোনো রেকর্ড সিলেক্ট করা হয়নি।")
            else -> {
                selected = matches[0]
                homeNotice = null
                refreshHomeCard()
                bindRecord(matches[0])
                showPage(Page.RESULT)
            }
        }
    }

    // ------------------------------------------------------------------ import / export

    private fun openPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/json", "text/plain", "application/octet-stream"))
        }
        try { startActivityForResult(intent, REQ_PICK) }
        catch (e: ActivityNotFoundException) { say("এই ডিভাইসে ফাইল বাছাই করার অ্যাপ নেই।") }
    }

    private fun exportJson() {
        if (dataset.size == 0) return say("এক্সপোর্ট করার মতো ডেটা নেই। আগে JSON ইম্পোর্ট করুন।")
        pendingExportText = dataset.toJson()
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "seawave_updated.json")
        }
        try { startActivityForResult(intent, REQ_EXPORT) }
        catch (e: ActivityNotFoundException) { say("এক্সপোর্টের জন্য ফাইল অ্যাপ পাওয়া যায়নি।") }
    }

    @Deprecated("Framework API used to keep the app dependency-free.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQ_PICK -> importFrom(uri)
            REQ_EXPORT -> {
                val content = pendingExportText ?: dataset.toJson()
                try {
                    contentResolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                        ?: throw IOException("Could not open output stream")
                    say("আপডেট করা JSON সফলভাবে এক্সপোর্ট হয়েছে।")
                } catch (e: Exception) {
                    say("এক্সপোর্ট ব্যর্থ হয়েছে। অন্য জায়গা বেছে নিন।")
                } finally { pendingExportText = null }
            }
        }
    }

    private fun importFrom(uri: Uri) {
        if (importing) return
        importing = true
        say("JSON ইম্পোর্ট হচ্ছে…")
        Thread {
            var parsed: Dataset? = null
            var error = "ফাইলটি ইম্পোর্ট করা যায়নি।"
            try {
                val text = contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) }
                    ?: throw IOException("Cannot read file")
                val ds = Dataset.parse(text)
                store.save(text)
                parsed = ds
            } catch (e: JsonFormatException) {
                error = when {
                    e.message?.contains("not valid JSON") == true -> "ফাইলটি সঠিক JSON নয়।"
                    e.message?.contains("records") == true && e.message?.contains("array") == true -> "JSON-এ \"records\" অ্যারে নেই।"
                    e.message?.contains("usable") == true -> "ব্যবহারযোগ্য কোনো রেকর্ড পাওয়া যায়নি (প্রতিটি রেকর্ডে SL থাকতে হবে)।"
                    else -> error
                }
            } catch (e: Exception) { error = "নির্বাচিত JSON ফাইলটি পড়া বা সংরক্ষণ করা যায়নি।" }
            runOnUiThread {
                importing = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                val ds = parsed
                if (ds != null) {
                    dataset = ds
                    selected = null
                    refreshTotals()
                    showPage(Page.HOME)
                    var msg = "${fmt(ds.size)}টি রেকর্ড ইম্পোর্ট হয়েছে।"
                    if (ds.skipped > 0) msg += " ${ds.skipped}টি এন্ট্রি বাদ গেছে (SL নেই)।"
                    if (ds.duplicateSlCount > 0) msg += " ${ds.duplicateSlCount}টি SL একাধিকবার আছে, সেগুলোতে কল করা যাবে না।"
                    say(msg)
                } else {
                    say("ইম্পোর্ট ব্যর্থ: $error\nআগের ডেটা অক্ষত আছে।")
                }
            }
        }.start()
    }

    // ------------------------------------------------------------------ dialogs

    private fun newDialog(): Pair<Dialog, FrameLayout> {
        val dlg = Dialog(this, R.style.SeaDialog)
        val dim = FrameLayout(this).apply { setBackgroundColor(0x9E1C344A.toInt()) }
        dim.setOnApplyWindowInsetsListener { v, ins ->
            val a = insetsOf(ins)
            v.setPadding(a[0], a[1], a[2], max(a[3], a[4]))
            ins
        }
        dim.setOnClickListener { dlg.dismiss() }
        dlg.setContentView(dim)
        dlg.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            if (Build.VERSION.SDK_INT >= 30) setDecorFitsSystemWindows(false)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        return Pair(dlg, dim)
    }

    private fun editRemark() {
        val rec = selected ?: return
        val (dlg, dim) = newDialog()
        val card = FrameLayout(this).apply { background = rounded(Color.WHITE, 26f); isClickable = true }
        dim.addView(card, FrameLayout.LayoutParams(r(720), r(573), Gravity.CENTER))

        val title = RibbonView(this, R.drawable.ribbon_dialog_title, 0.14f, 0f, 0.81f, 0.90f, pressable = false)
        ribbonText(title, null, "মন্তব্য (Remarks) সম্পাদনা", 30f, 0, 0)
        card.addView(title, flp(542, -2, l = 18, t = 33))

        card.addView(icon(R.drawable.ic_close, 68, 18).apply { setOnClickListener { dlg.dismiss() } }, flp(68, 68, l = 626, t = 32))

        val inputBox = FrameLayout(this).apply { background = rounded(Color.WHITE, 20f, 0xFF3BA5D6.toInt(), 2f) }
        val counter = tv("", 23f, 0xFF4E6378.toInt())
        val input = EditText(this).apply {
            background = null
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 30f * u)
            setTextColor(0xFF14263C.toInt())
            setHintTextColor(0xFF9FB2C4.toInt())
            hint = "মন্তব্য লিখুন"
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            includeFontPadding = false
            setLineSpacing(r(8).toFloat(), 1f)
            setPadding(r(32), r(24), r(32), r(24))
            setText(rec.remarks)
            setSelection(text.length)
            // The limit applies to new typing only; a longer existing remark is never cut silently.
            filters = arrayOf(InputFilter.LengthFilter(MAX_REMARK))
        }
        counter.text = "${input.text.length}/$MAX_REMARK"
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { counter.text = "${s?.length ?: 0}/$MAX_REMARK" }
        })
        inputBox.addView(input, FrameLayout.LayoutParams(-1, -1))
        card.addView(inputBox, flp(642, 212, l = 38, t = 146))
        card.addView(counter, flp(-2, -2, rr = 40, t = 366, g = Gravity.TOP or Gravity.END))

        val cancel = RibbonView(this, R.drawable.btn_cancel, 0.14f, 0.05f, 0.86f, 0.95f)
        val ct = ribbonText(cancel, null, "বাতিল", 32f, 0, 0)
        ct.setTextColor(0xFF1B88CC.toInt()); ct.setShadowLayer(0f, 0f, 0f, 0)
        cancel.setOnClickListener { dlg.dismiss() }
        val save = RibbonView(this, R.drawable.btn_save, 0.17f, 0.05f, 0.81f, 0.95f)
        ribbonText(save, null, "সংরক্ষণ করুন", 32f, 0, 0)
        save.setOnClickListener {
            saveRemark(rec, input.text.toString().trim())
            dlg.dismiss()
        }
        card.addView(save, flp(405, -2, l = 300, t = 433))
        card.addView(cancel, flp(302, -2, l = 18, t = 433))

        dlg.show()
        input.requestFocus()
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    private fun saveRemark(rec: SeaRecord, remark: String) {
        try {
            val updated = dataset.withRemark(rec.sl, remark)
            store.save(updated.toJson())
            dataset = updated
            val fresh = dataset.lookup(rec.sl).singleOrNull() ?: rec
            selected = fresh
            bindRecord(fresh)
            say("মন্তব্য সংরক্ষিত হয়েছে। ফাইল হিসেবে নিতে “JSON এক্সপোর্ট” ব্যবহার করুন।")
        } catch (e: Exception) {
            say("মন্তব্য সংরক্ষণ করা যায়নি।")
        }
    }

    private fun showPermissionDialog() {
        val (dlg, dim) = newDialog()
        val card = FrameLayout(this).apply { background = rounded(Color.WHITE, 26f); isClickable = true }
        dim.addView(card, FrameLayout.LayoutParams(r(720), r(470), Gravity.CENTER))
        val title = RibbonView(this, R.drawable.ribbon_dialog_title, 0.14f, 0f, 0.81f, 0.90f, pressable = false)
        ribbonText(title, null, "ফোন পারমিশন দরকার", 30f, 0, 0)
        card.addView(title, flp(542, -2, l = 18, t = 33))
        card.addView(icon(R.drawable.ic_close, 68, 18).apply { setOnClickListener { dlg.dismiss() } }, flp(68, 68, l = 626, t = 32))
        val msg = tv("সরাসরি কল করতে ফোন পারমিশন লাগে। Android আর অনুমতি চাইবে না, তাই অ্যাপ সেটিংস থেকে পারমিশন চালু করুন।", 28f, 0xFF1F3A57.toInt()).apply {
            setLineSpacing(r(8).toFloat(), 1f)
        }
        card.addView(msg, flp(644, -2, l = 38, t = 160))
        val cancel = RibbonView(this, R.drawable.btn_cancel, 0.14f, 0.05f, 0.86f, 0.95f)
        val ct = ribbonText(cancel, null, "বাতিল", 32f, 0, 0)
        ct.setTextColor(0xFF1B88CC.toInt()); ct.setShadowLayer(0f, 0f, 0f, 0)
        cancel.setOnClickListener { dlg.dismiss() }
        val open = RibbonView(this, R.drawable.btn_save, 0.17f, 0.05f, 0.81f, 0.95f)
        ribbonText(open, null, "সেটিংস খুলুন", 32f, 0, 0)
        open.setOnClickListener {
            dlg.dismiss()
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
        card.addView(open, flp(405, -2, l = 300, t = 330))
        card.addView(cancel, flp(302, -2, l = 18, t = 330))
        dlg.show()
    }

    // ------------------------------------------------------------------ calling

    private fun onCallPressed() {
        val current = selected ?: return say("প্রথমে একটি রেকর্ড সন্ধান করুন।")
        if (dataset.size == 0) return say("আগে একটি JSON ফাইল ইম্পোর্ট করুন।")
        val matches = dataset.lookup(current.sl)
        if (matches.isEmpty()) return say("সিরিয়াল ${current.sl} এর কোনো রেকর্ড পাওয়া যায়নি।")
        if (matches.size > 1) return say("সিরিয়াল ${current.sl} একাধিকবার আছে, তাই কল করা হয়নি।")
        val rec = matches[0]
        val number = Phone.dialable(rec.mobile) ?: return say("এই সিরিয়ালের জন্য সঠিক মোবাইল নম্বর নেই।")
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) return say("এই ডিভাইসে ফোন কল সমর্থিত নয়।")
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            say("সরাসরি কল করতে ফোন পারমিশন দরকার।")
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), REQ_CALL_PERMISSION)
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastCallAt < DOUBLE_TAP_GUARD_MS) return
        lastCallAt = now
        say("কল শুরু হচ্ছে…")
        try {
            startActivity(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null)))
        } catch (e: SecurityException) {
            say("ফোন পারমিশন প্রয়োজন।")
        } catch (e: ActivityNotFoundException) {
            say("কল শুরু করা যায়নি। সিম ও নেটওয়ার্ক দেখুন।")
        } catch (e: Exception) {
            say("কল শুরু করা যায়নি।")
        }
        // Never advance the serial number or initiate another call automatically.
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_CALL_PERMISSION) return
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            onCallPressed()
        } else {
            say("সরাসরি কল করতে ফোন পারমিশন লাগে। আবার “কল করুন” চাপুন।")
            if (!shouldShowRequestPermissionRationale(Manifest.permission.CALL_PHONE)) showPermissionDialog()
        }
    }

    private companion object {
        const val REF_W = 776f
        const val REGULAR = 0
        const val MEDIUM = 1
        const val BOLD = 2
        const val MAX_REMARK = 200
        const val REQ_PICK = 1001
        const val REQ_CALL_PERMISSION = 1002
        const val REQ_EXPORT = 1003
        const val DOUBLE_TAP_GUARD_MS = 1500L
    }
}
