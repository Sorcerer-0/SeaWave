package com.seawave.serialcall

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.io.IOException

class MainActivity : Activity() {

    private lateinit var store: RecordStore
    private lateinit var statusView: TextView
    private lateinit var importButton: Button
    private lateinit var slInput: EditText
    private lateinit var callButton: Button
    private lateinit var resultView: TextView

    private var dataset: Dataset = Dataset.EMPTY
    private var lastCallAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusView = findViewById(R.id.statusText)
        importButton = findViewById(R.id.importButton)
        slInput = findViewById(R.id.slInput)
        callButton = findViewById(R.id.callButton)
        resultView = findViewById(R.id.resultText)

        store = RecordStore(applicationContext)
        dataset = store.load()
        updateStatus()

        importButton.setOnClickListener { openPicker() }
        callButton.setOnClickListener { onCallPressed() }

        // Typing only previews the matching record. It never places a call.
        slInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = preview()
        })
    }

    // ---------------------------------------------------------------- import

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
            render(null, getString(R.string.err_no_picker))
        }
    }

    @Deprecated("Framework API; no AndroidX dependency is used on purpose.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PICK || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        importFrom(uri)
    }

    private fun importFrom(uri: Uri) {
        importButton.isEnabled = false
        render(null, getString(R.string.importing))
        Thread {
            var parsed: Dataset? = null
            var error: String? = null
            try {
                val text = contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) }
                    ?: throw IOException("openInputStream returned null")
                val ds = Dataset.parse(text)   // validate first
                store.save(text)               // persist privately; throws on failure
                parsed = ds
            } catch (e: JsonFormatException) {
                error = e.message ?: getString(R.string.err_import_generic)
            } catch (e: IOException) {
                error = getString(R.string.err_read_file)
            } catch (e: SecurityException) {
                error = getString(R.string.err_read_file)
            } catch (e: OutOfMemoryError) {
                error = getString(R.string.err_too_large)
            } catch (e: Exception) {
                error = getString(R.string.err_import_generic)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                importButton.isEnabled = true
                val ds = parsed
                if (ds != null) {
                    dataset = ds          // only replaced after full success
                    updateStatus()
                    val sb = StringBuilder(getString(R.string.imported_n, ds.size))
                    if (ds.skipped > 0) sb.append('\n').append(getString(R.string.skipped_n, ds.skipped))
                    if (ds.duplicateSlCount > 0) sb.append('\n').append(getString(R.string.duplicates_n, ds.duplicateSlCount))
                    render(null, sb.toString())
                } else {
                    render(null, getString(R.string.import_failed, error) + "\n" + getString(R.string.previous_kept))
                }
            }
        }.start()
    }

    // ---------------------------------------------------------------- lookup / preview

    private fun updateStatus() {
        statusView.text = getString(R.string.records_loaded, dataset.size)
    }

    private fun preview() {
        val sl = slInput.text.toString().trim()
        if (sl.isEmpty() || dataset.size == 0) {
            render(null, null)
            return
        }
        val m = dataset.lookup(sl)
        when {
            m.isEmpty() -> render(null, getString(R.string.no_record, sl))
            m.size > 1 -> render(null, getString(R.string.ambiguous, sl, m.size))
            else -> render(m[0], null)
        }
    }

    private fun render(rec: SeaRecord?, message: String?) {
        val sb = StringBuilder()
        if (rec != null) {
            sb.append(rec.name.ifEmpty { getString(R.string.no_name) }).append('\n')
            sb.append(getString(R.string.sl_label, rec.sl)).append('\n')
            sb.append(getString(R.string.mobile_label, rec.mobile.ifEmpty { "-" }))
        }
        if (message != null) {
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append(message)
        }
        resultView.text = sb.toString()
    }

    // ---------------------------------------------------------------- calling

    private fun onCallPressed() {
        val sl = slInput.text.toString().trim()
        if (sl.isEmpty()) return render(null, getString(R.string.err_enter_sl))
        if (!sl.all { it in '0'..'9' }) return render(null, getString(R.string.err_invalid_sl))
        if (dataset.size == 0) return render(null, getString(R.string.err_no_dataset))

        val matches = dataset.lookup(sl)
        if (matches.isEmpty()) return render(null, getString(R.string.no_record, sl))
        if (matches.size > 1) return render(null, getString(R.string.ambiguous, sl, matches.size))

        val rec = matches[0]
        val number = Phone.dialable(rec.mobile)
        if (number == null) {
            render(rec, getString(R.string.no_valid_mobile))
            return
        }

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            render(rec, getString(R.string.err_no_telephony))
            return
        }

        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            render(rec, getString(R.string.permission_needed))
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), REQ_CALL_PERMISSION)
            return
        }

        // Guard against an accidental double-tap placing two calls for one press.
        val now = SystemClock.elapsedRealtime()
        if (now - lastCallAt < DOUBLE_TAP_GUARD_MS) return
        lastCallAt = now

        render(rec, getString(R.string.calling))
        try {
            startActivity(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null)))
        } catch (e: SecurityException) {
            render(rec, getString(R.string.permission_needed))
        } catch (e: ActivityNotFoundException) {
            render(rec, getString(R.string.err_call_failed))
        } catch (e: Exception) {
            render(rec, getString(R.string.err_call_failed))
        }
        // Deliberately nothing else: SL is left unchanged, no next call, no call monitoring.
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_CALL_PERMISSION) return
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Complete the single call the user asked for with the CALL press that triggered the prompt.
            onCallPressed()
        } else {
            render(null, getString(R.string.permission_denied))
            if (!shouldShowRequestPermissionRationale(Manifest.permission.CALL_PHONE)) {
                // Android will no longer show the prompt; offer the app settings page.
                AlertDialog.Builder(this)
                    .setMessage(R.string.permission_denied_settings)
                    .setPositiveButton(R.string.open_settings) { _, _ ->
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                        )
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    private companion object {
        const val REQ_PICK = 1001
        const val REQ_CALL_PERMISSION = 1002
        const val DOUBLE_TAP_GUARD_MS = 1500L
    }
}
