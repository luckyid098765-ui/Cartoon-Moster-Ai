package com.example.ai

import com.example.model.CartoonDraftRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class ProviderHealthResult(
    val reachable: Boolean,
    val latencyMs: Long,
    val statusCode: Int,
    val message: String
)

class RemoteVideoProviderClient {

    suspend fun checkHealth(
        endpointUrl: String,
        bearerToken: String,
        timeoutSeconds: Long = 15L
    ): ProviderHealthResult = withContext(Dispatchers.IO) {
        val cleanUrl = endpointUrl.trim().trimEnd('/')
        if (cleanUrl.isBlank() || (!cleanUrl.startsWith("https://") && !cleanUrl.startsWith("http://"))) {
            return@withContext ProviderHealthResult(
                reachable = false,
                latencyMs = 0L,
                statusCode = 0,
                message = "Invalid endpoint URL. Must start with https:// or http://"
            )
        }
        val client = buildClient(timeoutSeconds)
        val start = System.currentTimeMillis()
        try {
            val reqBuilder = Request.Builder().url("$cleanUrl/health").get()
            if (bearerToken.isNotBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $bearerToken")
            }
            client.newCall(reqBuilder.build()).execute().use { resp ->
                val latency = System.currentTimeMillis() - start
                if (resp.isSuccessful) {
                    ProviderHealthResult(true, latency, resp.code, "Healthy (HTTP ${resp.code}, ${latency}ms)")
                } else {
                    ProviderHealthResult(false, latency, resp.code, "Server returned HTTP ${resp.code}")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ProviderHealthResult(
                reachable = false,
                latencyMs = System.currentTimeMillis() - start,
                statusCode = 0,
                message = e.localizedMessage ?: "Connection failed"
            )
        }
    }

    suspend fun generateViaRemoteBackend(
        endpointUrl: String,
        bearerToken: String,
        draft: CartoonDraftRequest,
        outputMp4File: File,
        timeoutSeconds: Long,
        maxRetries: Int,
        onProgress: suspend (percent: Int, status: String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val cleanUrl = endpointUrl.trim().trimEnd('/')
        require(cleanUrl.startsWith("https://") || cleanUrl.startsWith("http://")) {
            "Remote video backend URL is not configured."
        }
        val client = buildClient(timeoutSeconds)
        val payload = JSONObject().apply {
            put("title", draft.title)
            put("storyPrompt", draft.storyPrompt)
            put("characterPrompt", draft.characterPrompt)
            put("style", draft.style.name)
            put("language", draft.language.name)
            put("durationMinutes", draft.durationOption.minutes)
            put("quality", draft.quality.name)
            put("includeVoiceover", draft.includeVoiceover)
            put("includeBgm", draft.includeBgm)
            put("includeSfx", draft.includeSfx)
        }.toString()

        onProgress(10, "Submitting job to remote video provider...")
        val jobId = executeWithRetries(maxRetries) {
            val req = Request.Builder()
                .url("$cleanUrl/api/v1/videos")
                .apply {
                    if (bearerToken.isNotBlank()) addHeader("Authorization", "Bearer $bearerToken")
                }
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Remote API rejected job (HTTP ${resp.code}): $bodyStr")
                }
                val json = JSONObject(bodyStr)
                json.getString("jobId")
            }
        }

        // Poll remote job until completed or failed
        var cloudDownloadUrl = ""
        val maxPolls = 360
        for (poll in 0 until maxPolls) {
            delay(3000L)
            val statusJson = executeWithRetries(maxRetries) {
                val req = Request.Builder()
                    .url("$cleanUrl/api/v1/videos/$jobId")
                    .apply {
                        if (bearerToken.isNotBlank()) addHeader("Authorization", "Bearer $bearerToken")
                    }
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("Status poll failed (HTTP ${resp.code})")
                    }
                    JSONObject(bodyStr)
                }
            }
            val state = statusJson.optString("status", "RUNNING")
            val pct = statusJson.optInt("progress", 25).coerceIn(10, 92)
            val msg = statusJson.optString("message", "Rendering on cloud GPU...")
            onProgress(pct, msg)

            if (state.equals("COMPLETED", ignoreCase = true)) {
                cloudDownloadUrl = statusJson.getString("downloadUrl")
                break
            } else if (state.equals("FAILED", ignoreCase = true)) {
                throw IllegalStateException(
                    statusJson.optString("error", "Remote provider reported generation failure.")
                )
            }
        }

        if (cloudDownloadUrl.isBlank()) {
            throw IllegalStateException("Remote video generation timed out waiting for completion.")
        }

        onProgress(93, "Downloading verified MP4 from cloud storage...")
        executeWithRetries(maxRetries) {
            val req = Request.Builder().url(cloudDownloadUrl).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("MP4 download failed (HTTP ${resp.code})")
                }
                val body = resp.body ?: throw IllegalStateException("Empty video download body")
                outputMp4File.parentFile?.mkdirs()
                FileOutputStream(outputMp4File).use { fos ->
                    body.byteStream().copyTo(fos)
                }
            }
        }
        cloudDownloadUrl
    }

    private suspend fun <T> executeWithRetries(maxRetries: Int, block: () -> T): T {
        var attempt = 0
        var lastError: Exception? = null
        while (attempt <= maxRetries) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (attempt >= maxRetries) break
                attempt++
                delay(1500L * attempt)
            }
        }
        throw lastError ?: IllegalStateException("Remote API call failed.")
    }

    private fun buildClient(timeoutSeconds: Long): OkHttpClient {
        val clamped = timeoutSeconds.coerceIn(10L, 600L)
        return OkHttpClient.Builder()
            .connectTimeout(clamped, TimeUnit.SECONDS)
            .readTimeout(clamped, TimeUnit.SECONDS)
            .writeTimeout(clamped, TimeUnit.SECONDS)
            .build()
    }
}
