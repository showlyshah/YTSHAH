package com.viewtube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Lightweight deterministic on-device AI fallback; no network access and no personal-data upload. */
class BasicLocalAiEngine : LocalAiEngine {
    override suspend fun execute(request: AiRequest): AiResponse? {
        val source = request.sourceText.orEmpty().trim()
        val title = request.videoTitle.orEmpty()
        return when (request.task) {
            AiTask.VIDEO_SUMMARY -> {
                if (source.isBlank()) null else {
                    val sentences = source.replace(Regex("\\s+"), " ")
                        .split(Regex("(?<=[.!?])\\s+"))
                        .map { it.trim() }.filter { it.length > 24 }
                    val summary = sentences.sortedByDescending { it.length }.take(4)
                        .joinToString("\\n• ", prefix = "• ")
                    AiResponse(summary.ifBlank { source.take(700) }, "On-device extractive", true)
                }
            }
            AiTask.VIDEO_QUESTION -> {
                if (source.isBlank()) null else {
                    val terms = Regex("[\\p{L}\\p{N}]{3,}")
                        .findAll(request.prompt.lowercase()).map { it.value }.toSet()
                    val matches = source.split(Regex("(?<=[.!?])\\s+"))
                        .map { it.trim() }
                        .map { sentence -> sentence to terms.count { term -> sentence.contains(term, true) } }
                        .filter { it.second > 0 }.sortedByDescending { it.second }.take(3)
                    if (matches.isEmpty()) AiResponse(
                        "I couldn't find an answer in the available video description. A transcript or explicitly enabled cloud analysis may be needed.",
                        "On-device", false
                    ) else AiResponse(matches.joinToString("\\n") { it.first }, "On-device source matching", true)
                }
            }
            AiTask.TOPIC_CLASSIFICATION, AiTask.SMART_SEARCH -> {
                val text = listOf(title, source).joinToString(" ")
                val terms = Regex("[\\p{L}\\p{N}]{4,}").findAll(text.lowercase())
                    .map { it.value }.distinct().take(10).toList()
                if (terms.isEmpty()) null else AiResponse(terms.joinToString(", "), "On-device", true)
            }
            AiTask.DIAGNOSTIC_ANALYSIS -> AiResponse(
                "Local diagnostic mode: inspect the failing component, network state, source response and recent playback logs. No code changes are made automatically.",
                "On-device rules", false
            )
        }
    }
}

/** OpenAI-compatible chat-completions provider. Secrets are read only from Android Keystore-backed storage. */
class OpenAiCompatibleProvider(
    private val client: OkHttpClient,
    private val secure: SecureStore,
) : CloudAiProvider {
    override val providerId: String get() = "Cloud chat-completions"

    override suspend fun execute(request: AiRequest): AiResponse = withContext(Dispatchers.IO) {
        require(request.allowCloud) { "Cloud processing was not approved for this request." }
        val apiKey = secure.get(KEY_API_KEY)?.takeIf { it.isNotBlank() }
            ?: error("Add an AI API key in ViewTube settings first.")
        val baseUrl = secure.get(KEY_BASE_URL)?.ifBlank { null } ?: DEFAULT_BASE_URL
        require(baseUrl.startsWith("https://", ignoreCase = true)) { "AI provider URL must use HTTPS." }
        val model = secure.get(KEY_MODEL)?.ifBlank { null } ?: DEFAULT_MODEL
        val source = request.sourceText.orEmpty().take(MAX_SOURCE_CHARS)
        val userContent = buildString {
            append("Task: ").append(request.task.name).append("\\n")
            request.videoTitle?.let { append("Video title: ").append(it.take(300)).append("\\n") }
            append("Source material (may be incomplete):\\n").append(source)
            append("\\n\\nUser request: ").append(request.prompt.take(2000))
        }
        val payload = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().put(
                JSONObject().put("role", "system").put(
                    "content",
                    "You are ViewTube's video assistant. Ground answers only in supplied source material. " +
                        "If the source is insufficient, clearly say so. Do not invent transcript details, timestamps, " +
                        "or claims. Keep summaries structured and concise."
                )
            ).put(JSONObject().put("role", "user").put("content", userContent)))
            put("temperature", 0.2)
        }
        val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val httpRequest = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()
        client.newCall(httpRequest).execute().use { response ->
            val body = response.body?.string().orEmpty()
            check(response.isSuccessful) {
                "AI provider returned HTTP ${response.code}: ${body.take(300)}"
            }
            val text = JSONObject(body).optJSONArray("choices")
                ?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                ?.takeIf { it.isNotBlank() } ?: error("AI provider returned an empty answer.")
            AiResponse(text, providerId, source.isNotBlank())
        }
    }

    companion object {
        const val KEY_API_KEY = "viewtube_ai_api_key"
        const val KEY_BASE_URL = "viewtube_ai_base_url"
        const val KEY_MODEL = "viewtube_ai_model"
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-4o-mini"
        const val MAX_SOURCE_CHARS = 18_000
    }
}
