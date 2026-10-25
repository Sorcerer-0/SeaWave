package com.seawave.serialcall

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.IOException

class MainActivity : Activity() {
    private lateinit var store: RecordStore
    private lateinit var statusView: TextView
    private lateinit var importButton: RibbonButtonView
    private lateinit var exportButton: RibbonButtonView
    private lateinit var slInput: EditText
    private lateinit var callButton: RibbonButtonView
    private lateinit var editRemarkButton: RibbonButtonView
    private lateinit var resultContainer: LinearLayout
    private lateinit var messageView: TextView

    private var dataset: Dataset = Dataset.EMPTY
    private var selectedRecord: SeaRecord? = null
    private var lastCallAt = 0L
    private var pendingExportText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusView = findViewById(R.id.statusText)
        importButton = findViewById(R.id.importButton)
        exportButton = findViewById(R.id.exportButton)
        slInput = findViewById(R.id.slInput)
        callButton = findViewById(R.id.callButton)
        editRemarkButton = findViewById(R.id.editRemarkButton)
        resultContainer = findViewById(R.id.resultContainer)
        messageView = findViewById(R.id.messageText)

        importButton.label = "IMPORT JSON"
        exportButton.label = "EXPORT JSON"
        callButton.label = "CALL NOW"
        editRemarkButton.label = "ADD / EDIT REMARK"

        store = RecordStore(applicationContext)
        dataset = store.load()
        updateStatus()
        importButton.setOnClickListener { openPicker() }
        exportButton.setOnClickListener { exportJson() }
        callButton.setOnClickListener { onCallPressed() }
        editRemarkButton.setOnClickListener { editRemark() }
        slInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = preview()
        })
        render(null, if (dataset.size == 0) "Import a JSON file to get started." else null)
    }

    private fun openPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/json", "text/plain", "application/octet-stream"))
        }
        try { startActivityForResult(intent, REQ_PICK) }
        catch (e: ActivityNotFoundException) { render(null, "No file picker is available on this device.") }
    }

    private fun exportJson() {
        if (dataset.size == 0) {
            render(null, "No dataset to export. Import a JSON file first.")
            return
        }
        pendingExportText = dataset.toJson()
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "seawave_updated.json")
        }
        try { startActivityForResult(intent, REQ_EXPORT) }
        catch (e: ActivityNotFoundException) { render(selectedRecord, "No file picker is available for export.") }
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
                    render(selectedRecord, "Updated JSON exported successfully.")
                } catch (e: Exception) {
                    render(selectedRecord, "Export failed. Please choose another location.")
                } finally { pendingExportText = null }
            }
        }
    }

    private fun importFrom(uri: Uri) {
        importButton.isEnabled = false
        render(null, "Importing JSON…")
        Thread {
            var parsed: Dataset? = null
            var error = "The file could not be imported."
            try {
                val text = contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) }
                    ?: throw IOException("Cannot read file")
                val ds = Dataset.parse(text)
                store.save(text)
                parsed = ds
            } catch (e: JsonFormatException) { error = e.message ?: error }
            catch (e: Exception) { error = "The selected JSON file could not be read or saved." }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                importButton.isEnabled = true
                if (parsed != null) {
                    dataset = parsed!!
                    selectedRecord = null
                    updateStatus()
                    render(null, "Imported ${dataset.size} records.")
                } else render(selectedRecord, "Import failed: $error\nPrevious data was kept.")
            }
        }.start()
    }

    private fun updateStatus() {
        statusView.text = "${dataset.size} records loaded"
        statusView.setTextColor(COLOR_MUTED)
    }

    private fun preview() {
        val sl = slInput.text.toString().trim()
        if (sl.isEmpty()) { render(null, null); return }
        if (dataset.size == 0) { render(null, "Import a JSON file first."); return }
        val matches = dataset.lookup(sl)
        when {
            matches.isEmpty() -> render(null, "No record found for SL $sl.")
            matches.size > 1 -> render(null, "SL $sl appears more than once. No record selected.")
            else -> render(matches[0], null)
        }
    }

    private fun render(rec: SeaRecord?, message: String?) {
        selectedRecord = rec
        resultContainer.removeAllViews()
        val hasRecord = rec != null
        callButton.isEnabled = hasRecord && Phone.dialable(rec!!.mobile) != null
        callButton.alpha = if (callButton.isEnabled) 1f else 0.45f
        editRemarkButton.isEnabled = hasRecord
        editRemarkButton.alpha = if (hasRecord) 1f else 0.45f

        if (rec != null) {
            val title = TextView(this).apply {
                text = rec.name.ifBlank { "Unnamed contact" }
                setTextColor(COLOR_INK)
                textSize = 23f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                setPadding(0, 0, 0, dp(5))
            }
            resultContainer.addView(title, matchWrap())
            addInfoRow("SERIAL NUMBER", rec.sl)
            addInfoRow("REGISTRATION / NO.", rec.no)
            addInfoRow("MOBILE NUMBER", rec.mobile)
            addInfoRow("DIVISION", rec.division)
            addInfoRow("UNIVERSITY", rec.university)
            addInfoRow("EMAIL", rec.email)
            addInfoRow("REMARKS", rec.remarks)
        }
        messageView.text = message.orEmpty()
        messageView.visibility = if (message.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    private fun addInfoRow(label: String, value: String) {
        if (value.isBlank()) return
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = getDrawable(R.drawable.bg_info_row)
        }
        val labelView = TextView(this).apply {
            text = label
            textSize = 10f
            letterSpacing = 0.08f
            setTextColor(COLOR_MUTED)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val valueView = TextView(this).apply {
            text = value
            textSize = if (label == "MOBILE NUMBER") 18f else 15f
            setTextColor(COLOR_INK)
            typeface = if (label == "MOBILE NUMBER") Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
            setTextIsSelectable(true)
        }
        row.addView(labelView, matchWrap())
        row.addView(valueView, matchWrap())
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(8)
        resultContainer.addView(row, lp)
    }

    private fun editRemark() {
        val rec = selectedRecord ?: return
        val input = EditText(this).apply {
            setText(rec.remarks)
            hint = "Add a note or edit the existing remark"
            minLines = 3
            maxLines = 6
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), 0)
            addView(input, matchWrap())
        }
        AlertDialog.Builder(this)
            .setTitle("Edit remark · SL ${rec.sl}")
            .setMessage("This remark will be saved in SeaWave's local JSON copy.")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Clear") { _, _ -> saveRemark(rec, "") }
            .setPositiveButton("Save") { _, _ -> saveRemark(rec, input.text.toString().trim()) }
            .show()
    }

    private fun saveRemark(rec: SeaRecord, remark: String) {
        try {
            val updated = dataset.withRemark(rec.sl, remark)
            store.save(updated.toJson())
            dataset = updated
            render(dataset.lookup(rec.sl).singleOrNull(), "Remark saved locally. Use Export JSON to save the updated file outside SeaWave.")
        } catch (e: Exception) {
            render(rec, "Could not save the remark.")
        }
    }

    private fun onCallPressed() {
        val sl = slInput.text.toString().trim()
        if (sl.isEmpty()) return render(null, "Enter an SL first.")
        if (!sl.all { it in '0'..'9' }) return render(null, "SL must be numeric.")
        if (dataset.size == 0) return render(null, "Import a JSON file first.")
        val matches = dataset.lookup(sl)
        if (matches.isEmpty()) return render(null, "No record found for SL $sl.")
        if (matches.size > 1) return render(null, "SL $sl appears more than once. No call placed.")
        val rec = matches[0]
        val number = Phone.dialable(rec.mobile)
        if (number == null) return render(rec, "No valid mobile number for this SL.")
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) return render(rec, "This device does not support phone calls.")
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            render(rec, "Phone permission is needed to call directly.")
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), REQ_CALL_PERMISSION)
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastCallAt < DOUBLE_TAP_GUARD_MS) return
        lastCallAt = now
        render(rec, "Starting call…")
        try {
            startActivity(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null)))
        } catch (e: SecurityException) {
            render(rec, "Phone permission is required.")
        } catch (e: ActivityNotFoundException) {
            render(rec, "The call could not be started. Check SIM and network.")
        } catch (e: Exception) {
            render(rec, "The call could not be started.")
        }
        // Never advance the serial number or initiate another call automatically.
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_CALL_PERMISSION) return
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            onCallPressed()
        } else {
            render(selectedRecord, "Direct calling requires the Phone permission.")
            if (!shouldShowRequestPermissionRationale(Manifest.permission.CALL_PHONE)) {
                AlertDialog.Builder(this)
                    .setMessage("Android will no longer ask for this permission. Open app settings to grant it.")
                    .setPositiveButton("Open settings") { _, _ ->
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val REQ_PICK = 1001
        const val REQ_CALL_PERMISSION = 1002
        const val REQ_EXPORT = 1003
        const val DOUBLE_TAP_GUARD_MS = 1500L
        const val COLOR_INK = 0xFF10283A.toInt()
        const val COLOR_MUTED = 0xFF587080.toInt()
    }
}
