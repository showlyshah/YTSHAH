package com.viewtube.app.player

import android.media.audiofx.Equalizer
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A small, stable wrapper around Android's session Equalizer.
 * The UI exposes only the 15 fixed sound profiles below; there are no user-adjustable bands.
 */
class PresetEqualizer private constructor(
    private val effect: Equalizer,
) {
    val numberOfPresets: Short = PRESETS.size.toShort()
    val activePreset: Int get() = selectedPreset

    private var selectedPreset: Int = 0

    var enabled: Boolean
        get() = runCatching { effect.enabled }.getOrDefault(false)
        set(value) { runCatching { effect.enabled = value } }

    fun getPresetName(preset: Short): String =
        PRESETS.getOrNull(preset.toInt())?.first ?: "Preset ${preset.toInt()}"

    fun usePreset(preset: Short) {
        val profile = PRESETS.getOrNull(preset.toInt())?.second ?: return
        val bands = effect.numberOfBands.toInt().coerceAtLeast(1)
        val range = effect.bandLevelRange
        for (band in 0 until bands) {
            val position = if (bands == 1) 2f else band.toFloat() * 4f / (bands - 1)
            val low = floor(position).toInt().coerceIn(0, 4)
            val high = (low + 1).coerceAtMost(4)
            val fraction = position - low
            val db = profile[low] + (profile[high] - profile[low]) * fraction
            val millibels = (db * 100f).roundToInt()
                .coerceIn(range[0].toInt(), range[1].toInt())
            effect.setBandLevel(band.toShort(), millibels.toShort())
        }
        selectedPreset = preset.toInt()
    }

    fun release() {
        runCatching { effect.enabled = false }
        runCatching { effect.release() }
    }

    companion object {
        // Five anchor points, from bass through treble. Adapted to the device's actual band count.
        private val PRESETS = listOf(
            "Flat" to floatArrayOf(0f, 0f, 0f, 0f, 0f),
            "Acoustic" to floatArrayOf(3f, 2f, 0f, 2f, 3f),
            "Bass Boost" to floatArrayOf(7f, 5f, 3f, 0f, -1f),
            "Bass Reducer" to floatArrayOf(-6f, -4f, -2f, 0f, 1f),
            "Classical" to floatArrayOf(4f, 3f, 0f, 3f, 5f),
            "Dance" to floatArrayOf(6f, 4f, 1f, 3f, 4f),
            "Deep" to floatArrayOf(7f, 5f, 2f, -1f, -3f),
            "Electronic" to floatArrayOf(5f, 3f, 0f, 2f, 5f),
            "Hip-Hop" to floatArrayOf(6f, 4f, 0f, 2f, 3f),
            "Jazz" to floatArrayOf(4f, 2f, -1f, 2f, 4f),
            "Latin" to floatArrayOf(4f, 1f, 0f, 3f, 4f),
            "Lounge" to floatArrayOf(-2f, 0f, 2f, 3f, 2f),
            "Pop" to floatArrayOf(-1f, 2f, 4f, 2f, -1f),
            "Rock" to floatArrayOf(5f, 3f, -1f, 3f, 5f),
            "Vocal" to floatArrayOf(-3f, 0f, 4f, 4f, 1f),
        )

        fun create(sessionId: Int): PresetEqualizer? {
            if (sessionId <= 0) return null
            return runCatching {
                PresetEqualizer(Equalizer(0, sessionId).apply { enabled = false })
            }.getOrNull()
        }
    }
}
