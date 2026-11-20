package com.viewtube.app.data

/** Tasks supported by the optional hybrid intelligence layer. */
enum class AiTask { TOPIC_CLASSIFICATION, VIDEO_SUMMARY, VIDEO_QUESTION, SMART_SEARCH, DIAGNOSTIC_ANALYSIS }

data class AiRequest(
    val task: AiTask,
    val prompt: String,
    val videoTitle: String? = null,
    val sourceText: String? = null,
    /** Must be explicitly enabled by the user for each request that may leave the device. */
    val allowCloud: Boolean = false,
)

data class AiResponse(
    val text: String,
    val provider: String,
    val groundedInSource: Boolean,
)

/** Implementations must not silently upload history or other personal data. */
interface LocalAiEngine {
    suspend fun execute(request: AiRequest): AiResponse?
}

/** Cloud providers are optional and must receive only the request explicitly approved by the caller. */
interface CloudAiProvider {
    val providerId: String
    suspend fun execute(request: AiRequest): AiResponse
}

/**
 * Routes supported requests to local intelligence first, then to an optional cloud provider.
 * A failed cloud request falls back to local output; cloud use is disabled by default.
 */
class HybridAiCoordinator(
    private val local: LocalAiEngine,
    private val cloudProvider: () -> CloudAiProvider?,
    private val cloudEnabled: () -> Boolean = { false },
) {
    suspend fun execute(request: AiRequest): AiResponse? {
        if (request.allowCloud && cloudEnabled()) {
            val provider = cloudProvider()
            if (provider != null) {
                val cloudResult = runCatching { provider.execute(request) }.getOrNull()
                if (cloudResult != null) return cloudResult
            }
        }
        return runCatching { local.execute(request) }.getOrNull()
    }
}
