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
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import java.io.IOException
import java.util.Locale

class MainActivity : Activity() {

    private enum class Page { HOME, RESULT, SETTINGS }

    private lateinit var store: RecordStore
    private var dataset: Dataset = Dataset.EMPTY
    private var selectedRecord: SeaRecord? = null
    private var lastCallAt = 0L
    private var pendingExportText: String? = null
    private var page = Page.HOME
    private var pageBeforeSettings = Page.HOME

    private lateinit var scroll: ScrollView
    private lateinit var messageView: TextView
    private lateinit var pageHome: View
    private lateinit var pageResult: View
    private lateinit var pageSettings: View

    // home
    private lateinit var totalCountText: TextView
    private lateinit var slInput: EditText
    private lateinit var clearButton: View
    private lateinit var searchButton: RibbonButtonView
    private lateinit var emptyTitle: TextView
    private lateinit var emptySub: TextView
    private lateinit var importHomeButton: RibbonButtonView

    // result
    private lateinit var resultPosition: TextView
    private lateinit var resultName: TextView
    private lateinit var resultNo: TextView
    private lateinit var slBadge: TextView
    private lateinit var rowMobile: View
    private lateinit var rowDivision: View
    private lateinit var rowUni: View
    private lateinit var rowEmail: View
    private lateinit var valMobile: TextView
    private lateinit var valDivision: TextView
    private lateinit var valUni: TextView
    private lateinit var valEmail: TextView
    private lateinit var valRemarks: TextView
    private lateinit var callButton: RibbonButtonView
    private lateinit var editRemarkButton: RibbonButtonView
    private lateinit var exportButton: RibbonButtonView

    // settings
    private lateinit var settingsCount: TextView
    private lateinit var importSettingsButton: RibbonButtonView
    private lateinit var exportSettingsButton: RibbonButtonView

    private val hideMessageRunnable = Runnable { messageView.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()

        store = RecordStore(applicationContext)
        dataset = store.load()

        wireHome()
        wireResult()
        wireSettings()
        findViewById<View>(R.id.settingsButton).setOnClickListener {
            if (page == Page.SETTINGS) navigateBack() else showPage(Page.SETTINGS)
        }

        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                if (!navigateBack()) finish()
            }
        }

        updateCounts()
        showHomeStatus()
        showPage(Page.HOME)
    }

    private fun bindViews() {
        scroll = findViewById(R.id.scroll)
        messageView = findViewById(R.id.messageText)
        pageHome = findViewById(R.id.pageHome)
        pageResult = findViewById(R.id.pageResult)
        pageSettings = findViewById(R.id.pageSettings)

        totalCountText = findViewById(R.id.totalCountText)
        slInput = findViewById(R.id.slInput)
        clearButton = findViewById(R.id.clearButton)
        searchButton = findViewById(R.id.searchButton)
        emptyTitle = findViewById(R.id.emptyTitle)
        emptySub = findViewById(R.id.emptySub)
        importHomeButton = findViewById(R.id.importHomeButton)

        resultPosition = findViewById(R.id.resultPosition)
        resultName = findViewById(R.id.resultName)
        resultNo = findViewById(R.id.resultNo)
        slBadge = findViewById(R.id.slBadge)
        rowMobile = findViewById(R.id.rowMobile)
        rowDivision = findViewById(R.id.rowDivision)
        rowUni = findViewById(R.id.rowUni)
        rowEmail = findViewById(R.id.rowEmail)
        valMobile = findViewById(R.id.valMobile)
        valDivision = findViewById(R.id.valDivision)
        valUni = findViewById(R.id.valUni)
        valEmail = findViewById(R.id.valEmail)
        valRemarks = findViewById(R.id.valRemarks)
        callButton = findViewById(R.id.callButton)
        editRemarkButton = findViewById(R.id.editRemarkButton)
        exportButton = findViewById(R.id.exportButton)

        settingsCount = findViewById(R.id.settingsCount)
        importSettingsButton = findViewById(R.id.importSettingsButton)
        exportSettingsButton = findViewById(R.id.exportSettingsButton)
    }

    // ------------------------------------------------------------------ wiring

    private fun wireHome() {
        searchButton.setOnClickListener { performSearch() }
        importHomeButton.setOnClickListener { openPicker() }
        clearButton.setOnClickListener {
            slInput.setText("")
            showHomeStatus()
        }
        slInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                clearButton.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        })
        slInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                performSearch()
                true
            } else false
        }
    }

    private fun wireResult() {
        findViewById<View>(R.id.backButton).setOnClickListener { navigateBack() }
        callButton.setOnClickListener { onCallPressed() }
        editRemarkButton.setOnClickListener { editRemark() }
        exportButton.setOnClickListener { exportJson() }
        findViewById<View>(R.id.copyMobile).setOnClickListener {
            copyText(getString(R.string.mobile_number), valMobile.text.toString())
        }
        findViewById<View>(R.id.copyEmail).setOnClickListener {
            copyText(getString(R.string.email), valEmail.text.toString())
        }
        findViewById<View>(R.id.editRemarkInline).setOnClickListener { editRemark() }
    }

    private fun wireSettings() {
        findViewById<View>(R.id.settingsBack).setOnClickListener { navigateBack() }
        importSettingsButton.setOnClickListener { openPicker() }
        exportSettingsButton.setOnClickListener { exportJson() }
    }

    // -------------------------------------------------------------- navigation

    private fun showPage(p: Page) {
        if (p == Page.SETTINGS && page != Page.SETTINGS) pageBeforeSettings = page
        page = p
        pageHome.visibility = if (p == Page.HOME) View.VISIBLE else View.GONE
        pageResult.visibility = if (p == Page.RESULT) View.VISIBLE else View.GONE
        pageSettings.visibility = if (p == Page.SETTINGS) View.VISIBLE else View.GONE
        hideKeyboard()
        scroll.scrollTo(0, 0)
        hideMessage()
    }

    /** Returns true when a page change was handled; false when already on the first page. */
    private fun navigateBack(): Boolean = when (page) {
        Page.HOME -> false
        Page.RESULT -> { backToSearch(); true }
        Page.SETTINGS -> { showPage(pageBeforeSettings); true }
    }

    @Deprecated("Used only below Android 13; newer versions use OnBackInvokedCallback.")
    override fun onBackPressed() {
        if (!navigateBack()) super.onBackPressed()
    }

    private fun backToSearch() {
        selectedRecord = null
        slInput.setText("")
        showHomeStatus()
        showPage(Page.HOME)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val token = (currentFocus ?: slInput).windowToken
        if (token != null) imm.hideSoftInputFromWindow(token, 0)
    }

    // ----------------------------------------------------------------- messages

    private fun showMessage(text: String) {
        messageView.removeCallbacks(hideMessageRunnable)
        messageView.text = text
        messageView.visibility = View.VISIBLE
        messageView.postDelayed(hideMessageRunnable, 6000L)
    }

    private fun hideMessage() {
        messageView.removeCallbacks(hideMessageRunnable)
        messageView.visibility = View.GONE
    }

    private fun formatCount(n: Int): String = String.format(Locale.US, "%,d", n)

    private fun updateCounts() {
        val total = formatCount(dataset.size)
        totalCountText.text = total
        settingsCount.text = getString(R.string.settings_records, total)
        selectedRecord?.let { resultPosition.text = getString(R.string.record_position, it.sl, total) }
    }

    /** Home status card: default hint, "no data" hint, or an error message. */
    private fun showHomeStatus(title: String? = null, sub: String? = null) {
        val noData = dataset.size == 0
        val t = title ?: getString(if (noData) R.string.no_data_title else R.string.empty_title)
        val s = if (title != null) sub else getString(if (noData) R.string.no_data_sub else R.string.empty_sub)
        emptyTitle.text = t
        emptySub.text = s.orEmpty()
        emptySub.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
        importHomeButton.visibility = if (noData) View.VISIBLE else View.GONE
    }

    // ------------------------------------------------------------------- search

    private fun performSearch() {
        hideKeyboard()
        val sl = slInput.text.toString().trim()
        if (sl.isEmpty()) return failSearch(getString(R.string.err_enter_sl))
        if (!sl.all { it in '0'..'9' }) return failSearch(getString(R.string.err_invalid_sl))
        if (dataset.size == 0) return failSearch(getString(R.string.err_no_dataset))
        val matches = dataset.lookup(sl)
        when {
            matches.isEmpty() -> failSearch(getString(R.string.no_record, sl))
            matches.size > 1 -> failSearch(getString(R.string.ambiguous, sl))
            else -> openRecord(matches[0])
        }
    }

    private fun failSearch(message: String) {
        selectedRecord = null
        showHomeStatus(message, null)
    }

    private fun openRecord(rec: SeaRecord) {
        selectedRecord = rec
        bindRecord(rec)
        showPage(Page.RESULT)
    }

    private fun bindRecord(rec: SeaRecord) {
        resultName.text = rec.name.ifBlank { getString(R.string.unnamed_contact) }
        resultNo.text = getString(R.string.no_prefix, rec.no)
        resultNo.visibility = if (rec.no.isBlank()) View.GONE else View.VISIBLE
        slBadge.text = getString(R.string.sl_badge, rec.sl)

        setRow(rowMobile, valMobile, rec.mobile)
        setRow(rowDivision, valDivision, rec.division)
        setRow(rowUni, valUni, rec.university)
        setRow(rowEmail, valEmail, rec.email)
        valRemarks.text = rec.remarks.ifBlank { "—" }

        callButton.isEnabled = Phone.dialable(rec.mobile) != null
        editRemarkButton.isEnabled = true
        updateCounts()
    }

    private fun setRow(row: View, value: TextView, text: String) {
        row.visibility = if (text.isBlank()) View.GONE else View.VISIBLE
        value.text = text
    }

    private fun copyText(label: String, text: String) {
        if (text.isBlank()) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        // Android 13+ shows its own copy confirmation.
        if (Build.VERSION.SDK_INT < 33) {
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------ import/export

    private fun setImportEnabled(enabled: Boolean) {
        importHomeButton.isEnabled = enabled
        importSettingsButton.isEnabled = enabled
    }

    private fun openPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("application/json", "text/json", "text/plain", "application/octet-stream")
            )
        }
        try {
            startActivityForResult(intent, REQ_PICK)
        } catch (e: ActivityNotFoundException) {
            showMessage(getString(R.string.err_no_picker))
        }
    }

    private fun exportJson() {
        if (dataset.size == 0) {
            showMessage(getString(R.string.err_export_no_data))
            return
        }
        pendingExportText = dataset.toJson()
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "seawave_updated.json")
        }
        try {
            startActivityForResult(intent, REQ_EXPORT)
        } catch (e: ActivityNotFoundException) {
            showMessage(getString(R.string.err_no_picker_export))
        }
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
                    contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(content.toByteArray(Charsets.UTF_8))
                    } ?: throw IOException("Could not open output stream")
                    showMessage(getString(R.string.export_ok))
                } catch (e: Exception) {
                    showMessage(getString(R.string.export_failed))
                } finally {
                    pendingExportText = null
                }
            }
        }
    }

    private fun importFrom(uri: Uri) {
        setImportEnabled(false)
        showMessage(getString(R.string.importing))
        Thread {
            var parsed: Dataset? = null
            var error = getString(R.string.err_import_generic)
            try {
                val text = contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) }
                    ?: throw IOException("Cannot read file")
                val ds = Dataset.parse(text)
                store.save(text)
                parsed = ds
            } catch (e: JsonFormatException) {
                error = e.message ?: error
            } catch (e: Exception) {
                error = getString(R.string.err_import_read_save)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                setImportEnabled(true)
                val result = parsed
                if (result != null) {
                    dataset = result
                    selectedRecord = null
                    pageBeforeSettings = Page.HOME
                    updateCounts()
                    showHomeStatus()
                    showMessage(getString(R.string.imported_n, formatCount(dataset.size)))
                } else {
                    showMessage(getString(R.string.import_failed, error))
                }
            }
        }.start()
    }

    // ----------------------------------------------------------------- remarks

    private fun editRemark() {
        val rec = selectedRecord ?: return
        val content = LayoutInflater.from(this).inflate(R.layout.dialog_remark, null)
        val input = content.findViewById<EditText>(R.id.remarkInput)
        val counter = content.findViewById<TextView>(R.id.remarkCounter)
        val maxLen = maxOf(REMARK_LIMIT, rec.remarks.length)

        input.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(maxLen))
        input.setText(rec.remarks)
        input.setSelection(input.text.length)
        counter.text = getString(R.string.remark_counter, input.text.length, maxLen)
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                counter.text = getString(R.string.remark_counter, s?.length ?: 0, maxLen)
            }
        })

        val dialog = createDialog(content, showKeyboard = true)
        content.findViewById<View>(R.id.remarkClose).setOnClickListener { dialog.dismiss() }
        content.findViewById<View>(R.id.remarkCancel).setOnClickListener { dialog.dismiss() }
        content.findViewById<View>(R.id.remarkClear).setOnClickListener {
            saveRemark(rec, "")
            dialog.dismiss()
        }
        content.findViewById<View>(R.id.remarkSave).setOnClickListener {
            saveRemark(rec, input.text.toString().trim())
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun saveRemark(rec: SeaRecord, remark: String) {
        try {
            val updated = dataset.withRemark(rec.sl, remark)
            store.save(updated.toJson())
            dataset = updated
            val fresh = dataset.lookup(rec.sl).singleOrNull()
            if (fresh != null) {
                selectedRecord = fresh
                bindRecord(fresh)
            }
            showMessage(getString(R.string.remark_saved))
        } catch (e: Exception) {
            showMessage(getString(R.string.remark_save_failed))
        }
    }

    // ------------------------------------------------------------------- dialogs

    private fun createDialog(content: View, showKeyboard: Boolean): Dialog {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(content)
        dialog.window?.let { w ->
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            w.setDimAmount(0.55f)
            var mode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            if (showKeyboard) mode = mode or WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
            w.setSoftInputMode(mode)
        }
        return dialog
    }

    private fun showMessageDialog(title: String, message: String, positive: String, onPositive: () -> Unit) {
        val content = LayoutInflater.from(this).inflate(R.layout.dialog_message, null)
        content.findViewById<RibbonButtonView>(R.id.dialogTitle).label = title
        content.findViewById<TextView>(R.id.dialogMessage).text = message
        content.findViewById<RibbonButtonView>(R.id.dialogPositive).label = positive
        val dialog = createDialog(content, showKeyboard = false)
        content.findViewById<View>(R.id.dialogNegative).setOnClickListener { dialog.dismiss() }
        content.findViewById<View>(R.id.dialogPositive).setOnClickListener {
            dialog.dismiss()
            onPositive()
        }
        dialog.show()
    }

    // --------------------------------------------------------------------- call

    private fun onCallPressed() {
        val selected = selectedRecord ?: return
        val matches = dataset.lookup(selected.sl)
        if (matches.size != 1) return showMessage(getString(R.string.ambiguous_call, selected.sl))
        val rec = matches[0]
        val number = Phone.dialable(rec.mobile)
            ?: return showMessage(getString(R.string.no_valid_mobile))
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return showMessage(getString(R.string.err_no_telephony))
        }
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            showMessage(getString(R.string.permission_needed))
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), REQ_CALL_PERMISSION)
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastCallAt < DOUBLE_TAP_GUARD_MS) return
        lastCallAt = now
        showMessage(getString(R.string.calling))
        try {
            startActivity(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null)))
        } catch (e: SecurityException) {
            showMessage(getString(R.string.err_call_permission))
        } catch (e: ActivityNotFoundException) {
            showMessage(getString(R.string.err_call_failed))
        } catch (e: Exception) {
            showMessage(getString(R.string.err_call_generic))
        }
        // Never advance the serial number or initiate another call automatically.
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_CALL_PERMISSION) return
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            onCallPressed()
        } else {
            showMessage(getString(R.string.permission_denied))
            if (!shouldShowRequestPermissionRationale(Manifest.permission.CALL_PHONE)) {
                showMessageDialog(
                    getString(R.string.permission_dialog_title),
                    getString(R.string.permission_denied_settings),
                    getString(R.string.open_settings)
                ) {
                    startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", packageName, null)
                        )
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        messageView.removeCallbacks(hideMessageRunnable)
        super.onDestroy()
    }

    private companion object {
        const val REQ_PICK = 1001
        const val REQ_CALL_PERMISSION = 1002
        const val REQ_EXPORT = 1003
        const val DOUBLE_TAP_GUARD_MS = 1500L
        const val REMARK_LIMIT = 200
    }
}
