package com.viewtube.app.data

/**
 * Endless home feed. Round-robins over several topic searches, each with its own "next page"
 * cursor, so there is always more to load and the mix stays varied.
 */
class FeedMixer(
    topics: List<String>,
    private val repo: YouTubeRepo,
    private val accept: (VideoItem) -> Boolean,
) {
    private class Cursor(val topic: String, var next: (suspend () -> Paged<Item>)?, var started: Boolean = false)

    private val cursors = topics.distinct().map { Cursor(it, null) }.toMutableList()
    private val seen = HashSet<String>()
    private var turn = 0

    val exhausted: Boolean get() = cursors.isEmpty()

    /** Loads until at least [min] new videos were found (or sources run out). */
    suspend fun more(min: Int = 10): List<VideoItem> {
        val out = mutableListOf<VideoItem>()
        var failures = 0
        while (out.size < min && cursors.isNotEmpty() && failures < 6) {
            val c = cursors[turn % cursors.size]
            turn++
            val page = try {
                if (!c.started) repo.search(c.topic, FILTER_VIDEOS)
                else c.next?.invoke() ?: run {
                    cursors.remove(c)
                    null
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                // A transient network/HTTP failure must not permanently discard this topic.
                failures++
                null
            }
            if (page == null) {
                if (cursors.contains(c) && !c.started) {
                    // Keep a failed first-page cursor so the next scroll can retry it.
                    continue
                }
                continue
            }
            c.started = true
            c.next = page.next
            if (page.next == null) cursors.remove(c)
            for (v in page.items.filterIsInstance<VideoItem>()) {
                if (accept(v) && seen.add(v.id)) out += v
            }
        }
        return out
    }
}
