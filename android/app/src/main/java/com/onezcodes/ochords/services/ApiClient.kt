package com.onezcodes.ochords.services

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.onezcodes.ochords.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Where the API lives: `API_BASE_URL` from local.properties (per build type in app/build.gradle.kts). */
object APIConfig {
    val baseURL: HttpUrl? get() = BuildConfig.API_BASE_URL.trim().takeIf { it.isNotEmpty() }?.let { (if (it.endsWith("/")) it else "$it/").toHttpUrlOrNull() }
}

val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false; isLenient = true }

/**
 * The API token, encrypted with a key in the Android Keystore (this device only) and kept in app-private
 * preferences. Never logged.
 */
class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("ochords.api", Context.MODE_PRIVATE)
    private val alias = "com.onezcodes.ochords.api.token"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return gen.generateKey()
    }

    @Synchronized
    fun read(): String? {
        val packed = prefs.getString("token", null) ?: return null
        return runCatching {
            val bytes = Base64.decode(packed, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        }.getOrElse { clear(); null }
    }

    @Synchronized
    fun save(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val packed = Base64.encodeToString(cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        prefs.edit().putString("token", packed).apply()
    }

    @Synchronized
    fun clear() { prefs.edit().remove("token").apply() }
}

/** JSON over HTTP(S) with the bearer token. Maps HTTP failures onto `ServiceException`. */
class APIClient(val baseURL: HttpUrl, context: Context) {
    val tokens = TokenStore(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.SECONDS)
        .build()
    private val uploadClient = client.newBuilder().readTimeout(45, TimeUnit.SECONDS).writeTimeout(45, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    val hasToken: Boolean get() = tokens.read() != null

    suspend fun <T> get(path: String, serializer: KSerializer<T>, query: Map<String, String> = emptyMap()): T =
        json.decodeFromString(serializer, request("GET", path, query = query))

    suspend fun <B, T> send(method: String, path: String, body: B, bodySerializer: KSerializer<B>, serializer: KSerializer<T>): T =
        json.decodeFromString(serializer, request(method, path, body = json.encodeToString(bodySerializer, body).toRequestBody(jsonType)))

    /** For endpoints whose response body isn't needed (204s and acknowledgements). */
    suspend fun perform(method: String, path: String) { request(method, path, body = if (method == "GET") null else "".toRequestBody(null)) }

    suspend fun <B> perform(method: String, path: String, body: B, bodySerializer: KSerializer<B>) {
        request(method, path, body = json.encodeToString(bodySerializer, body).toRequestBody(jsonType))
    }

    /** Uploads one file as multipart/form-data (field `name`) and decodes the reply. */
    suspend fun <T> upload(path: String, file: ByteArray, name: String, filename: String, mimeType: String, serializer: KSerializer<T>): T {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart(name, filename, file.toRequestBody(mimeType.toMediaType())).build()
        return json.decodeFromString(serializer, request("POST", path, body = body, client = uploadClient))
    }

    private suspend fun request(method: String, path: String, query: Map<String, String> = emptyMap(), body: RequestBody? = null,
                                client: OkHttpClient = this.client): String = withContext(Dispatchers.IO) {
        val url = baseURL.newBuilder().addPathSegments(path).apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val sentToken = tokens.read()
        val req = Request.Builder().url(url).method(method, body).header("Accept", "application/json").apply {
            if (sentToken != null) header("Authorization", "Bearer $sentToken")
        }.build()
        val response = try { client.newCall(req).await() } catch (e: IOException) { throw ServiceException.offline }
        response.use {
            val text = it.body.string()
            if (it.isSuccessful) text else throw failure(it.code, text, sentToken)
        }
    }

    private fun failure(status: Int, text: String, sentToken: String?): ServiceException {
        val body = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        val message = body?.get("message")?.let { (it as? JsonPrimitive)?.content }
        return when (status) {
            401 -> {
                // Forget the token only if it's still the one this request used — a sign-in may have saved a new one meanwhile.
                if (sentToken != null && tokens.read() == sentToken) tokens.clear()
                ServiceException.signedOut
            }
            422 -> {
                val first = (body?.get("errors") as? JsonObject)?.values?.firstOrNull()?.let { v ->
                    runCatching { v.jsonArray.firstOrNull()?.jsonPrimitive?.content }.getOrNull()
                }
                ServiceException.server(first ?: message ?: "Please check what you entered.")
            }
            429 -> ServiceException.server("Too many tries. Wait a minute and try again.")
            in 400..499 -> ServiceException.server(message ?: "That didn’t work. Try again.")
            else -> ServiceException.server("Something went wrong on our side. Try again in a moment.")
        }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (cont.isActive) cont.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) { cont.resume(response) }
    })
    cont.invokeOnCancellation { cancel() }
}
