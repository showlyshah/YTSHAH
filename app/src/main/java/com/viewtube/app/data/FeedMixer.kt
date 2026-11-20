package com.viewtube.app.data

import kotlinx.coroutines.CancellationException

/**
 * Endless discovery feed. Each topic owns an independent continuation cursor.
 *
 * A failed request does not consume its cursor: the next call can retry it. Each invocation
 * tries each currently active topic at most once, so a broken endpoint cannot create a tight
 * retry loop or block the UI.
 */
class FeedMixer(
    topics: List<String>,
    private val repo: YouTubeRepo,
    private val accept: (VideoItem) -> Boolean,
) {
    private class Cursor(
        val topic: String,
        var next: (suspend () -> Paged<Item>)? = null,
        var started: Boolean = false,
    )

    private val cursors = topics.map(String::trim).filter(String::isNotEmpty).distinct()
        .map { Cursor(it) }.toMutableList()
    private val seen = HashSet<String>()
    private var turn = 0

    /** Last transient error, if any. Cleared at the beginning of the next load attempt. */
    var lastError: Throwable? = null
        private set

    val exhausted: Boolean get() = cursors.isEmpty()

    suspend fun more(min: Int = 10): List<VideoItem> {
        if (min <= 0 || cursors.isEmpty()) return emptyList()
        lastError = null
        val out = mutableListOf<VideoItem>()
        val count = cursors.size
        val start = Math.floorMod(turn, count)
        turn = (start + 1) % count
        val batch = (0 until count).map { cursors[(start + it) % count] }

        for (cursor in batch) {
            if (out.size >= min) break
            try {
                val page = if (cursor.started) {
                    cursor.next?.invoke()
                } else {
                    repo.search(cursor.topic, FILTER_VIDEOS)
                }
                if (page == null) {
                    cursors.remove(cursor)
                    continue
                }
                cursor.started = true
                cursor.next = page.next
                if (page.next == null) cursors.remove(cursor)
                page.items.filterIsInstance<VideoItem>().forEach { video ->
                    if (accept(video) && seen.add(video.id)) out += video
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                // Keep this cursor exactly where it was so a later call can retry it.
                lastError = error
            }
        }
        return out
    }
}
