package com.viewtube.app.data

import com.google.mlkit.nl.translate.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

/** On-device caption translation. Language models are downloaded by ML Kit and used locally. */
class OnDeviceSubtitleTranslator private constructor(private val client: Translator) {
    suspend fun translate(text: String): String = client.translate(text).await()
    fun close() = client.close()

    companion object {
        suspend fun create(sourceTag: String, targetTag: String): OnDeviceSubtitleTranslator {
            val source = TranslateLanguage.fromLanguageTag(sourceTag)
                ?: throw IllegalArgumentException("Unsupported subtitle language: $sourceTag")
            val target = TranslateLanguage.fromLanguageTag(targetTag)
                ?: throw IllegalArgumentException("Unsupported translation language: $targetTag")
            require(source != target) { "Subtitles are already in the selected language." }
            val client = Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(source)
                    .setTargetLanguage(target)
                    .build()
            )
            try {
                client.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
                return OnDeviceSubtitleTranslator(client)
            } catch (error: Throwable) {
                client.close()
                throw error
            }
        }
    }
}
