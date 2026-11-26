# Archived ViewTube code — SponsorBlock watcher (pre-experiment)

**Date:** 2026-11-27  
**Source baseline:** `rebuild/clean-baseline` at `c7708d21bbcdf15c55c4fc9cb5a89329a8711711`  
**Reason archived:** The watcher was replaced in the SponsorBlock experiment with category filtering, manual skip, undo, suppression after undo, and duplicate-skip protection. This version is retained for rollback/reference and is not compiled.

```kotlin
private fun startWatching(d: VideoDetails) {
    watcher?.cancel()
    watcher = scope.launch {
        while (true) {
            delay(400)
            if (!Graph.store.sponsorSkip.value || !player.isPlaying) continue
            val pos = player.currentPosition
            val seg = segments.firstOrNull { pos >= it.startMs && pos < it.endMs - 500 } ?: continue
            player.seekTo(seg.endMs)
            _skipNotice.value = "Skipped ${label(seg.category)}"
            scope.launch { delay(2500); _skipNotice.value = null }
        }
    }
    saver?.cancel()
    saver = scope.launch {
        while (true) {
            delay(5000)
            saveProgress()
        }
    }
}
```

The protected baseline branch remains unchanged.
