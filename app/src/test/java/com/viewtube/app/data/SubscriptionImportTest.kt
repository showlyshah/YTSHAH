package com.viewtube.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubscriptionImportTest {

    @Test
    fun parsesGoogleTakeoutCsvWithCommasAndEscapedQuotes() {
        val csv = "Channel Id,Channel Url,Channel Title\n" +
            "\"UC12345678901\",\"https://www.youtube.com/channel/UC12345678901\",\"News, \"\"World\"\"\""
        val result = SubscriptionImport.parse(csv)

        assertEquals(1, result.size)
        assertEquals("UC12345678901", result.single().id)
        assertEquals("News, \"World\"", result.single().name)
    }

    @Test
    fun parsesNewPipeSubscriptionExport() {
        val json = """{"subscriptions":[{"service_id":0,"url":"https://www.youtube.com/channel/UC12345678901","name":"Example"}]}"""
        val result = SubscriptionImport.parse(json)

        assertEquals(1, result.size)
        assertEquals("UC12345678901", result.single().id)
        assertEquals("Example", result.single().name)
    }

    @Test
    fun parsesGoogleTakeoutJsonExport() {
        val json = """[{"snippet":{"resourceId":{"channelId":"UC12345678901"},"title":"Example"}}]"""
        val result = SubscriptionImport.parse(json)

        assertEquals(1, result.size)
        assertEquals("UC12345678901", result.single().id)
        assertEquals("Example", result.single().name)
    }

    @Test
    fun channelSerializationRetainsOptionalMetadata() {
        val original = ChannelItem(
            id = "UC12345678901",
            url = "https://www.youtube.com/channel/UC12345678901",
            name = "Example",
            avatar = "https://example.com/avatar.jpg",
            banner = "https://example.com/banner.jpg",
            subscribers = 1234,
            description = "Channel description",
        )

        val restored = ChannelItem.fromJson(original.toJson())

        assertEquals(original, restored)
    }

    @Test
    fun videoSerializationRetainsPlaybackMetadata() {
        val original = VideoItem(
            id = "abcdefghijk",
            title = "Example",
            channelName = "Creator",
            channelUrl = "https://www.youtube.com/channel/UC12345678901",
            channelAvatar = null,
            thumbnail = "https://example.com/thumb.jpg",
            durationSec = 123,
            views = 456,
            publishedText = "yesterday",
            publishedMs = 123456789L,
            isLive = false,
            isShort = true,
        )

        assertEquals(original, VideoItem.fromJson(original.toJson()))
    }

    @Test
    fun extractsVideoIdsFromSupportedLinkForms() {
        assertEquals("abcdefghijk", videoIdFromUrl("https://www.youtube.com/watch?v=abcdefghijk"))
        assertEquals("abcdefghijk", videoIdFromUrl("https://youtu.be/abcdefghijk"))
        assertEquals("abcdefghijk", videoIdFromUrl("https://www.youtube.com/shorts/abcdefghijk"))
        assertNull(videoIdFromUrl("https://example.com/not-a-video"))
        assertNull(videoIdFromUrl("https://notyoutube.com/watch?v=abcdefghijk"))
        assertNull(videoIdFromUrl("https://youtube.com.evil.example/watch?v=abcdefghijk"))
    }
}
