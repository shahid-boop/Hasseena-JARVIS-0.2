package com.hasseena.jarvis

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.math.min
import kotlin.random.Random

class GeminiClient(private val prefs: Prefs) {

    companion object {
        private const val MAX_RETRIES = 4
        private const val INITIAL_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 8_000L
    }

    fun ask(userText: String, callback: (Result<String>) -> Unit) {
        Thread {
            try {
                val key = prefs.apiKey
                require(key.isNotBlank()) {
                    "Gemini API key is missing. Open Settings and add it."
                }

                val requestedModel = prefs.model.ifBlank { AppConfig.DEFAULT_MODEL }
                val first = requestWithRetry(requestedModel, key, userText)

                // Model-access errors are not transient. Try the known current fallback once.
                // Transient errors are already retried with exponential backoff first.
                val result = if (first.isFailure && isModelAccessError(first.exceptionOrNull())) {
                    requestWithRetry(AppConfig.FALLBACK_MODEL, key, userText)
                } else {
                    first
                }

                callback(result)
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }.start()
    }

    private fun requestWithRetry(model: String, key: String, userText: String): Result<String> {
        var last: Result<String> = Result.failure(IllegalStateException("Gemini request failed."))

        for (attempt in 0 until MAX_RETRIES) {
            last = request(model, key, userText)
            if (last.isSuccess) return last

            val error = last.exceptionOrNull()
            if (!isRetryableError(error) || attempt == MAX_RETRIES - 1) break

            // Google recommends exponential backoff with jitter for transient 429/5xx errors.
            val exponential = INITIAL_BACKOFF_MS * (1L shl attempt)
            val delayMs = min(MAX_BACKOFF_MS, exponential) + Random.nextLong(0, 500)
            Thread.sleep(delayMs)
        }

        return last
    }

    private fun request(model: String, key: String, userText: String): Result<String> {
        var conn: HttpURLConnection? = null
        return try {
            val safeModel = model.trim().removePrefix("models/")
            require(safeModel.isNotBlank()) { "Gemini model is empty." }
            val endpoint = "${AppConfig.GEMINI_ENDPOINT}${URLEncoder.encode(safeModel, "UTF-8")}:generateContent"

            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 45_000
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("x-goog-api-key", key)
                doOutput = true
            }

            val system = buildString {
                append("You are Hasseena, a mature, confident, respectful female AI companion ")
                append("inspired by the architecture of a desktop JARVIS assistant. ")
                append("Be helpful, concise, warm and intelligent. ")
                append("When the user speaks Urdu or Roman Urdu, reply naturally in simple, warm Pakistani Urdu script unless the user asks for English. ")
                append("Avoid stiff literal translations and avoid unnecessary English words. ")
                append("Keep spoken replies short, friendly and conversational so Android Urdu TTS sounds natural. ")
                append("Do not claim abilities Android has not granted. ")
                append("User memory: ")
                append(prefs.memory)
            }

            val body = JSONObject()
                .put(
                    "system_instruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", system))
                    )
                )
                .put(
                    "contents",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put("parts", JSONArray().put(JSONObject().put("text", userText)))
                    )
                )
                .toString()

            conn.outputStream.use {
                it.write(body.toByteArray(StandardCharsets.UTF_8))
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                val message = try {
                    JSONObject(response)
                        .optJSONObject("error")
                        ?.optString("message")
                        ?.takeIf { it.isNotBlank() }
                } catch (_: Exception) {
                    null
                }

                return Result.failure(
                    GeminiHttpException(
                        code,
                        message ?: response.ifBlank { "No error details returned." }
                    )
                )
            }

            val candidates = JSONObject(response).optJSONArray("candidates")
            val text = buildString {
                for (i in 0 until (candidates?.length() ?: 0)) {
                    val partsArray = candidates?.optJSONObject(i)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                    for (j in 0 until (partsArray?.length() ?: 0)) {
                        val partText = partsArray?.optJSONObject(j)?.optString("text").orEmpty()
                        if (partText.isNotBlank()) {
                            if (isNotEmpty()) append('\n')
                            append(partText)
                        }
                    }
                }
            }.trim()

            if (text.isBlank()) {
                Result.failure(IllegalStateException("Gemini returned no text."))
            } else {
                Result.success(text)
            }
        } catch (e: Exception) {
            // Socket/time-out failures are also transient and will be retried by requestWithRetry().
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    private fun isRetryableError(error: Throwable?): Boolean {
        if (error is GeminiHttpException) {
            return error.httpCode == 408 || error.httpCode == 429 || error.httpCode in 500..599
        }

        val message = error?.message.orEmpty().lowercase()
        return message.contains("timeout") ||
            message.contains("timed out") ||
            message.contains("connection reset") ||
            message.contains("connection refused") ||
            message.contains("unable to resolve host")
    }

    private fun isModelAccessError(error: Throwable?): Boolean {
        val message = error?.message.orEmpty().lowercase()
        return message.contains("404") ||
            message.contains("not found") ||
            message.contains("model") && message.contains("not available")
    }

    private class GeminiHttpException(
        val httpCode: Int,
        message: String
    ) : IllegalStateException("Gemini HTTP $httpCode: $message")
}
