package com.viewtube.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubscriptionImportTest {

    @Test
    fun parsesGoogleTakeoutCsvWithCommasAndEscapedQuotes() {
        val csv = """Channel Id,Channel Url,Channel Title
"UC12345678901","https://www.youtube.com/channel/UC12345678901","News, ""World"""
        val result = SubscriptionImport.parse(csv)

        assertEquals(1, result.size)
        assertEquals("UC12345678901", result.single().id)
        assertEquals("News, \"World", result.single().name)
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
    fun extractsVideoIdsFromSupportedLinkForms() {
        assertEquals("abcdefghijk", videoIdFromUrl("https://www.youtube.com/watch?v=abcdefghijk"))
        assertEquals("abcdefghijk", videoIdFromUrl("https://youtu.be/abcdefghijk"))
        assertEquals("abcdefghijk", videoIdFromUrl("https://www.youtube.com/shorts/abcdefghijk"))
        assertNull(videoIdFromUrl("https://example.com/not-a-video"))
    }
}
