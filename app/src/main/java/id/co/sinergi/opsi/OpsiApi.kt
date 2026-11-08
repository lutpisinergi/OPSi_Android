package id.co.sinergi.opsi

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI

class OpsiApi(private val prefs: SharedPreferences) {
    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, BuildConfig.DEFAULT_API_URL) ?: BuildConfig.DEFAULT_API_URL
        set(value) { prefs.edit().putString(KEY_BASE_URL, normalizeBaseUrl(value)).apply() }

    val token: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""

    val savedUser: JSONObject?
        get() = prefs.getString(KEY_USER, null)?.let { runCatching { JSONObject(it) }.getOrNull() }

    val isLoggedIn: Boolean get() = token.isNotBlank() && savedUser != null

    fun saveSession(authToken: String, user: JSONObject) {
        prefs.edit().putString(KEY_TOKEN, authToken).putString(KEY_USER, user.toString()).apply()
    }

    fun logout() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_USER).apply()
    }

    suspend fun login(username: String, password: String): JSONObject = request(
        method = "POST",
        path = "/api/login",
        body = JSONObject().put("username", username.trim()).put("password", password),
        authenticated = false
    ).also { response ->
        if (!response.optBoolean("success")) throw OpsiApiException(response.optString("message", "Login gagal"))
        val authToken = response.optString("token")
        val user = response.optJSONObject("user") ?: throw OpsiApiException("Data pengguna dari server tidak lengkap")
        if (authToken.isBlank()) throw OpsiApiException("Token login tidak diterima")
        saveSession(authToken, user)
    }

    suspend fun syncAll(): JSONObject = request("GET", "/api/sync-all")
    suspend fun getAreas(): JSONArray = requestArray("GET", "/api/areas")
    suspend fun getUsers(): JSONObject = request("GET", "/api/users")
    suspend fun getVisits(period: String): JSONObject = request("GET", "/api/visit-reports?periode=$period")
    suspend fun createVisit(payload: JSONObject): JSONObject = request("POST", "/api/visit-reports", payload)
    suspend fun createReport(payload: JSONObject): JSONObject = request("POST", "/api/reports", payload)
    suspend fun updateReport(id: Long, payload: JSONObject): JSONObject = request("PUT", "/api/reports/$id", payload)
    suspend fun createItem(payload: JSONObject): JSONObject = request("POST", "/api/items", payload)
    suspend fun createBarangMasuk(payload: JSONObject): JSONObject = request("POST", "/api/barang-masuk", payload)
    suspend fun createBarangKeluar(payload: JSONObject): JSONObject = request("POST", "/api/barang-keluar", payload)
    suspend fun adjustStock(payload: JSONObject): JSONObject = request("POST", "/api/inventory/adjustments", payload)
    suspend fun createMr(payload: JSONObject): JSONObject = request("POST", "/api/mr", payload)
    suspend fun approveMr(id: Long, payload: JSONObject): JSONObject = request("PUT", "/api/mr/$id/approve", payload)
    suspend fun saveRkbTarget(payload: JSONObject): JSONObject = request("POST", "/api/rkb-target", payload)
    suspend fun createUser(payload: JSONObject): JSONObject = request("POST", "/api/users", payload)
    suspend fun updateUser(id: Long, payload: JSONObject): JSONObject = request("PUT", "/api/users/$id", payload)
    suspend fun deleteUser(id: Long): JSONObject = request("DELETE", "/api/users/$id")

    private suspend fun requestArray(method: String, path: String): JSONArray = withContext(Dispatchers.IO) {
        val root = normalizeBaseUrl(baseUrl)
        val connection = (URL(root + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 25_000
            readTimeout = 90_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.use { input -> BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() } }.orEmpty()
            if (isExpiredSession(status, responseText)) logout()
            if (status !in 200..299) throw OpsiApiException("Server merespons HTTP $status: ${responseText.take(300)}", status)
            JSONArray(responseText)
        } catch (e: OpsiApiException) {
            throw e
        } catch (e: Exception) {
            throw OpsiApiException("Tidak dapat mengambil daftar area dari server OPSi.", cause = e)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        authenticated: Boolean = true
    ): JSONObject = withContext(Dispatchers.IO) {
        val root = normalizeBaseUrl(baseUrl)
        val connection = (URL(root + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 25_000
            readTimeout = 90_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            if (authenticated && token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
            }.orEmpty()
            val json = if (responseText.isBlank()) JSONObject() else runCatching { JSONObject(responseText) }
                .getOrElse { JSONObject().put("message", responseText.take(500)) }
            // Backend OPSi memakai 403 untuk token kedaluwarsa; 403 hak akses biasa tidak menghapus sesi.
            if (isExpiredSession(status, responseText)) logout()
            if (status !in 200..299 || json.optBoolean("success") == false) {
                throw OpsiApiException(json.optString("message").ifBlank { "Server merespons HTTP $status" }, status)
            }
            json
        } catch (e: OpsiApiException) {
            throw e
        } catch (e: Exception) {
            throw OpsiApiException("Tidak dapat terhubung ke server OPSi. Periksa koneksi internet dan alamat server.", cause = e)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val KEY_BASE_URL = "opsi_base_url"
        private const val KEY_TOKEN = "opsi_jwt_token"
        private const val KEY_USER = "opsi_logged_user"

        private fun isExpiredSession(status: Int, responseText: String): Boolean {
            if (status == 401) return true
            if (status != 403) return false
            return responseText.contains("kadaluarsa", ignoreCase = true) ||
                responseText.contains("silakan masuk lagi", ignoreCase = true)
        }

        fun normalizeBaseUrl(value: String): String {
            val clean = value.trim().ifBlank { BuildConfig.DEFAULT_API_URL }.trimEnd('/') + "/"
            val parsed = runCatching { URI(clean) }.getOrElse {
                throw IllegalArgumentException("Alamat server OPSi tidak valid.")
            }
            require(parsed.scheme.equals("https", ignoreCase = true) && !parsed.host.isNullOrBlank()) {
                "Alamat server harus menggunakan HTTPS dan nama domain yang valid."
            }
            require(parsed.rawQuery == null && parsed.rawFragment == null) {
                "Alamat server tidak boleh memuat query atau fragment."
            }
            return clean
        }
    }
}

class OpsiApiException(message: String, val httpCode: Int = 0, cause: Throwable? = null) : Exception(message, cause)
