package com.automatelinux.bursa.data

import com.automatelinux.bursa.data.model.ErrorResponse
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/** Small strings that survive a restart: the last good responses, and the user's own list. */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun getBytes(key: String): ByteArray?
    fun putBytes(key: String, value: ByteArray)
}

class ApiException(message: String, val status: Int? = null) : Exception(message)

expect fun createHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient

/** The bursa server (see API.md). Read-only: every call is a GET. */
class Api(baseUrl: String, private val cache: KeyValueStore) {
    private val base = baseUrl.trimEnd('/')

    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    private val client = createHttpClient {
        expectSuccess = false
        install(HttpTimeout) {
            // An index with 125 components is several upstream calls on a cold server.
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 8_000
        }
    }

    private suspend fun raw(path: String): String {
        val resp = try {
            client.get(base + path)
        } catch (e: CancellationException) {
            // A request dropped because its screen closed is not a network failure.
            throw e
        } catch (e: Exception) {
            throw ApiException("אין חיבור לשרת")
        }
        val text = resp.bodyAsText()
        if (!resp.status.isSuccess()) {
            val said = runCatching { json.decodeFromString(ErrorResponse.serializer(), text).error }.getOrNull()
            throw ApiException(said?.takeIf { it.isNotBlank() } ?: "שגיאת שרת ${resp.status.value}", resp.status.value)
        }
        return text
    }

    /**
     * A company's logo. Null means the exchange has none (a 404) — which is an answer, and is
     * remembered as one. A network failure throws instead, so it is retried next launch.
     */
    suspend fun logo(companyId: String): ByteArray? {
        val key = "logo:$companyId"
        cache.getBytes(key)?.let { return it.takeIf { b -> b.isNotEmpty() } }
        val resp = try {
            client.get("$base/api/logo/$companyId")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ApiException("אין חיבור לשרת")
        }
        if (resp.status.value == 404) {
            cache.putBytes(key, ByteArray(0))
            return null
        }
        if (!resp.status.isSuccess()) throw ApiException("שגיאת שרת ${resp.status.value}", resp.status.value)
        val bytes = resp.bodyAsBytes()
        cache.putBytes(key, bytes)
        return bytes
    }

    /** The last good response for [path], or null. */
    fun <T> cached(path: String, ser: KSerializer<T>): T? =
        cache.get("api:$path")?.let { runCatching { json.decodeFromString(ser, it) }.getOrNull() }

    /** Network GET. [remember] keeps the response for the next launch — off for search-as-you-type. */
    suspend fun <T> get(path: String, ser: KSerializer<T>, remember: Boolean = true): T {
        val text = raw(path)
        val value = try {
            json.decodeFromString(ser, text)
        } catch (e: Exception) {
            throw ApiException("תשובה לא צפויה מהשרת")
        }
        if (remember) cache.put("api:$path", text)
        return value
    }
}

/** A PNG/JPEG as something Compose can draw; null when the bytes are not an image. */
expect fun decodeImage(bytes: ByteArray): androidx.compose.ui.graphics.ImageBitmap?

/** Percent-encode a query value (Hebrew search text). */
fun encodeQuery(text: String): String {
    val out = StringBuilder()
    for (b in text.encodeToByteArray()) {
        val c = b.toInt() and 0xFF
        val ch = c.toChar()
        if (ch.isLetterOrDigit() && c < 128 || ch == '-' || ch == '_' || ch == '.' || ch == '~') out.append(ch)
        else out.append('%').append(c.toString(16).uppercase().padStart(2, '0'))
    }
    return out.toString()
}
