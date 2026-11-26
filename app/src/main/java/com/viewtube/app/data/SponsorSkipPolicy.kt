package com.viewtube.app.data

/** Small, deterministic SponsorBlock selection rules shared by playback and unit tests. */
object SponsorSkipPolicy {
    const val EDGE_GUARD_MS = 350L

    fun key(segment: SponsorBlock.Segment): String =
        "${segment.category}:${segment.startMs}:${segment.endMs}"

    fun isEnabled(segment: SponsorBlock.Segment, enabledCategories: Set<String>): Boolean =
        segment.category in enabledCategories && segment.endMs > segment.startMs

    fun findAt(
        segments: List<SponsorBlock.Segment>,
        positionMs: Long,
        enabledCategories: Set<String>,
    ): SponsorBlock.Segment? = segments.firstOrNull { segment ->
        isEnabled(segment, enabledCategories) &&
            positionMs >= segment.startMs &&
            positionMs < segment.endMs - EDGE_GUARD_MS
    }
}
