package com.viewtube.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalRecommendationEngineTest {
    @Test fun preservesFeedWhenThereIsNoHistory() {
        val videos = listOf(video("1", "Kotlin basics"), video("2", "Travel Kerala"))
        assertEquals(videos, LocalRecommendationEngine.rank(videos, emptyList(), emptyList()))
    }

    @Test fun ranksRelatedTopicsAheadOfUnrelatedVideos() {
        val history = listOf(video("h", "Kotlin Android development"))
        val candidates = listOf(video("a", "Kerala beach travel"), video("b", "Advanced Kotlin Android"))
        assertEquals("b", LocalRecommendationEngine.rank(candidates, history, emptyList()).first().id)
    }

    private fun video(id: String, title: String) = VideoItem(
        id = id, title = title, channelName = "Channel", channelUrl = null,
        channelAvatar = null, thumbnail = null,
    )
}
