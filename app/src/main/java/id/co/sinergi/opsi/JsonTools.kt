package id.co.sinergi.opsi

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

fun JSONObject.array(name: String): JSONArray = optJSONArray(name) ?: JSONArray()
fun JSONObject.obj(name: String): JSONObject = optJSONObject(name) ?: JSONObject()
fun JSONObject.text(name: String, fallback: String = "-"): String {
    val value = opt(name)
    return if (value == null || value == JSONObject.NULL) fallback else value.toString().ifBlank { fallback }
}
fun JSONObject.long(name: String): Long = optLong(name, 0L)
fun JSONObject.double(name: String): Double = optDouble(name, 0.0)
fun JSONArray.toObjects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
fun JSONArray.toStrings(): List<String> = (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
fun todayIso(): String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
fun currentPeriod(): String = YearMonth.now().toString()
fun JSONObject.isHeadOffice(): Boolean = text("level", "User Area") == "Head Office" || text("role", "") == "Admin"
fun JSONObject.canVisit(): Boolean = text("role", "") == "Area Koordinator" || isHeadOffice() || text("role", "") in setOf("Direktur Operasional", "Operasional Manager", "Manager Operasional", "Admin Operasional")
fun JSONArray.findById(id: Long): JSONObject? = toObjects().firstOrNull { it.long("id") == id }

fun parseVisitTags(value: String): List<String> = runCatching {
    JSONArray(value).toStrings()
}.getOrDefault(listOf(value).filter { it.isNotBlank() })

fun jsonObjectOf(vararg values: Pair<String, Any?>): JSONObject = JSONObject().apply {
    values.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
}
