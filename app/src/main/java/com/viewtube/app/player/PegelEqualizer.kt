package com.viewtube.app.player

import android.media.audiofx.DynamicsProcessing
import kotlin.math.sqrt

/**
 * Player-session equalizer adapted from Pegel's MIT-licensed AndroidEffectChain and
 * Presets implementations (https://github.com/pepperonas/pegel).
 *
 * The effect is created once for the ExoPlayer audio session. Merely opening the EQ
 * controls never enables, disables, or recreates the effect.
 */
class PegelEqualizer private constructor(
    private val effect: DynamicsProcessing,
) {
    val numberOfBands: Short = BAND_CENTERS.size.toShort()
    val numberOfPresets: Short = PRESETS.size.toShort()
    val bandLevelRange: ShortArray = shortArrayOf(-1200, 1200)
    private val gainsDb = FloatArray(BAND_CENTERS.size)

    var enabled: Boolean
        get() = runCatching { effect.enabled }.getOrDefault(false)
        set(value) { runCatching { effect.enabled = value } }

    fun getBandLevel(band: Short): Short =
        (gainsDb.getOrNull(band.toInt())?.times(100f) ?: 0f).toInt().toShort()

    fun setBandLevel(band: Short, level: Short) {
        val index = band.toInt()
        require(index in BAND_CENTERS.indices)
        val db = (level.toInt() / 100f).coerceIn(-12f, 12f)
        effect.setPostEqBandAllChannelsTo(index, DynamicsProcessing.EqBand(true, cutoff(index), db))
        gainsDb[index] = db
    }

    fun getCenterFreq(band: Short): Int =
        (BAND_CENTERS.getOrNull(band.toInt())?.times(1000f) ?: 0f).toInt()

    fun getPresetName(preset: Short): String =
        PRESETS.getOrNull(preset.toInt())?.first ?: "Preset ${preset.toInt()}"

    fun usePreset(preset: Short) {
        val values = PRESETS.getOrNull(preset.toInt())?.second ?: return
        values.forEachIndexed { index, db -> setBandLevel(index.toShort(), (db * 100f).toInt().toShort()) }
    }

    fun release() {
        runCatching { effect.enabled = false }
        runCatching { effect.release() }
    }

    private fun cutoff(index: Int): Float =
        if (index == BAND_CENTERS.lastIndex) 20000f
        else sqrt(BAND_CENTERS[index] * BAND_CENTERS[index + 1])

    companion object {
        private val BAND_CENTERS = List(10) { 31.25f * (1 shl it) }
        private val PRESETS = listOf(
            "Flat" to listOf(0f,0f,0f,0f,0f,0f,0f,0f,0f,0f),
            "Bass" to listOf(6f,5f,4f,2f,0f,0f,0f,0f,0f,0f),
            "Treble" to listOf(0f,0f,0f,0f,0f,0f,2f,4f,5f,6f),
            "V-shape" to listOf(6f,4f,2f,0f,-2f,-2f,0f,2f,4f,6f),
            "Pop" to listOf(-1f,0f,2f,3f,4f,3f,1f,0f,-1f,-1f),
            "Rock" to listOf(5f,4f,2f,0f,-1f,-1f,1f,3f,4f,5f),
            "Vocal" to listOf(-2f,-2f,-1f,1f,3f,4f,4f,3f,1f,0f),
            "Speech" to listOf(-4f,-3f,-1f,1f,3f,4f,4f,2f,0f,-2f),
            "Movie" to listOf(4f,3f,1f,0f,0f,1f,2f,2f,2f,1f),
            "Night" to listOf(-5f,-3f,-1f,0f,1f,2f,2f,1f,0f,-1f),
        )

        fun create(sessionId: Int): PegelEqualizer? {
            if (sessionId < 0) return null
            return runCatching {
                val config = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2, true, 2, false, 0, true, BAND_CENTERS.size, true,
                ).setPreferredFrameDuration(10f).build()
                val engine = DynamicsProcessing(Int.MAX_VALUE, sessionId, config)
                val result = PegelEqualizer(engine)
                engine.setPreEqBandAllChannelsTo(0, DynamicsProcessing.EqBand(true, 80f, 0f))
                engine.setPreEqBandAllChannelsTo(1, DynamicsProcessing.EqBand(true, 20000f, 0f))
                BAND_CENTERS.indices.forEach { i ->
                    engine.setPostEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, result.cutoff(i), 0f))
                }
                engine.enabled = false
                result
            }.getOrNull()
        }
    }
}
