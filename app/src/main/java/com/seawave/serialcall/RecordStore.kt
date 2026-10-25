package com.seawave.serialcall

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class SeaRecord(
    val sl: String,
    val name: String,
    val no: String,
    val division: String,
    val university: String,
    val mobile: String,
    val email: String,
    val remarks: String
)

class JsonFormatException(message: String) : Exception(message)

/** Holds parsed records and the complete original JSON so unknown fields are preserved. */
class Dataset(
    val records: List<SeaRecord>,
    val skipped: Int,
    private val rootJson: JSONObject
) {
    private val index: Map<String, List<SeaRecord>> = records.groupBy { it.sl }
    val size: Int get() = records.size
    val duplicateSlCount: Int get() = index.count { it.value.size > 1 }
    fun lookup(sl: String): List<SeaRecord> = index[sl.trim()] ?: emptyList()
    fun toJson(): String = rootJson.toString(2)

    /** Update Remarks for the unique SL, preserving every other JSON field. */
    fun withRemark(sl: String, remark: String): Dataset {
        val matches = lookup(sl)
        require(matches.size == 1) { "SL must identify exactly one record." }
        val array = rootJson.optJSONArray("records") ?: throw IllegalStateException("Missing records array")
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (scalarToString(obj.opt("SL")).trim() == sl.trim()) {
                obj.put("Remarks", remark)
                break
            }
        }
        val updated = records.map { if (it.sl == sl.trim()) it.copy(remarks = remark) else it }
        return Dataset(updated, skipped, rootJson)
    }

    companion object {
        val EMPTY = Dataset(emptyList(), 0, JSONObject().put("columns", JSONArray()).put("records", JSONArray()))

        fun parse(text: String): Dataset {
            val root = try { JSONObject(text.removePrefix("\uFEFF")) }
            catch (e: JSONException) { throw JsonFormatException("File is not valid JSON.") }
            val arr = root.optJSONArray("records")
                ?: throw JsonFormatException("JSON has no \"records\" array.")
            val out = ArrayList<SeaRecord>(arr.length())
            var skipped = 0
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                if (obj == null) { skipped++; continue }
                val sl = scalarToString(obj.opt("SL")).trim()
                if (sl.isEmpty()) { skipped++; continue }
                out.add(
                    SeaRecord(
                        sl = sl,
                        name = scalarToString(obj.opt("Name")).trim(),
                        no = scalarToString(obj.opt("No")).trim(),
                        division = scalarToString(obj.opt("Division")).trim(),
                        university = scalarToString(obj.opt("Uni")).trim(),
                        mobile = scalarToString(obj.opt("Mobile")).trim(),
                        email = scalarToString(obj.opt("Email")).trim(),
                        remarks = scalarToString(obj.opt("Remarks")).trim()
                    )
                )
            }
            if (out.isEmpty()) throw JsonFormatException("No usable records found (each record needs an SL).")
            return Dataset(out, skipped, root)
        }

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
    fun dialable(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        if (DIALABLE.matches(t)) return t
        val cleaned = t.replace(SEPARATORS, "")
        return if (DIALABLE.matches(cleaned)) cleaned else null
    }
}

class RecordStore(context: Context) {
    private val dir = context.filesDir
    private val file = File(dir, "dataset.json")
    private val tmp = File(dir, "dataset.json.tmp")
    fun load(): Dataset = try {
        if (file.exists()) Dataset.parse(file.readText(Charsets.UTF_8)) else Dataset.EMPTY
    } catch (e: Exception) { Dataset.EMPTY }

    @Throws(IOException::class)
    fun save(text: String) {
        tmp.writeText(text, Charsets.UTF_8)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
