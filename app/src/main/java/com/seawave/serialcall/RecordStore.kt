package com.seawave.serialcall

import android.content.Context
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Only the fields SeaWave needs. All other metadata in the file is ignored. */
data class SeaRecord(val sl: String, val name: String, val mobile: String)

class JsonFormatException(message: String) : Exception(message)

/** An immutable, fully validated dataset. */
class Dataset(val records: List<SeaRecord>, val skipped: Int) {
    private val index: Map<String, List<SeaRecord>> = records.groupBy { it.sl }

    val size: Int get() = records.size

    /** Number of distinct SL values that occur more than once. */
    val duplicateSlCount: Int get() = index.count { it.value.size > 1 }

    /** Exact match on the stored SL (both sides trimmed). Never falls back to array position. */
    fun lookup(sl: String): List<SeaRecord> = index[sl.trim()] ?: emptyList()

    companion object {
        val EMPTY = Dataset(emptyList(), 0)

        /** Parses and validates; throws [JsonFormatException] without side effects on any problem. */
        fun parse(text: String): Dataset {
            val root = try {
                JSONObject(text.removePrefix("\uFEFF"))
            } catch (e: JSONException) {
                throw JsonFormatException("File is not valid JSON.")
            }
            val arr = root.optJSONArray("records")
                ?: throw JsonFormatException("JSON has no \"records\" array.")

            val out = ArrayList<SeaRecord>(arr.length())
            var skipped = 0
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                if (obj == null) {
                    skipped++
                    continue
                }
                val sl = scalarToString(obj.opt("SL")).trim()
                if (sl.isEmpty()) {
                    skipped++
                    continue
                }
                out.add(
                    SeaRecord(
                        sl = sl,
                        name = scalarToString(obj.opt("Name")).trim(),
                        mobile = scalarToString(obj.opt("Mobile")).trim()
                    )
                )
            }
            if (out.isEmpty()) {
                throw JsonFormatException("No usable records found (each record needs an SL).")
            }
            return Dataset(out, skipped)
        }

        /** Strings stay strings (so "01712..." keeps its leading zero). Numbers are rendered without ".0". */
        private fun scalarToString(v: Any?): String = when (v) {
            null, JSONObject.NULL -> ""
            is String -> v
            is Double -> if (!v.isNaN() && !v.isInfinite() && v == Math.floor(v)) v.toLong().toString() else v.toString()
            is Float -> scalarToString(v.toDouble())
            is Number -> v.toString()
            else -> ""
        }
    }
}

object Phone {
    private val DIALABLE = Regex("^\\+?[0-9]{6,15}$")
    private val SEPARATORS = Regex("[\\s\\-().]")

    /**
     * Returns the string to dial, or null if the value is unusable.
     * The trimmed value is used as-is when it is already digits (optional leading '+');
     * otherwise only whitespace, hyphens, dots and parentheses are removed. Leading zeros are never touched.
     */
    fun dialable(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        if (DIALABLE.matches(t)) return t
        val cleaned = t.replace(SEPARATORS, "")
        return if (DIALABLE.matches(cleaned)) cleaned else null
    }
}

/** Persists the last successfully imported file in app-private storage. */
class RecordStore(context: Context) {
    private val dir = context.filesDir
    private val file = File(dir, "dataset.json")
    private val tmp = File(dir, "dataset.json.tmp")

    /** Returns the persisted dataset, or [Dataset.EMPTY] if none/unreadable. */
    fun load(): Dataset = try {
        if (file.exists()) Dataset.parse(file.readText(Charsets.UTF_8)) else Dataset.EMPTY
    } catch (e: Exception) {
        Dataset.EMPTY
    }

    /** Writes atomically so a failed write never destroys the previous dataset. */
    @Throws(IOException::class)
    fun save(text: String) {
        tmp.writeText(text, Charsets.UTF_8)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
