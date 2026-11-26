package com.viewtube.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SponsorSkipPolicyTest {
    private val sponsor = SponsorBlock.Segment(10_000, 25_000, "sponsor")
    private val intro = SponsorBlock.Segment(30_000, 40_000, "intro")

    @Test fun detectsInsideSegmentAndNotBeforeIt() {
        assertNull(SponsorSkipPolicy.findAt(listOf(sponsor), 9_999, setOf("sponsor")))
        assertEquals(sponsor, SponsorSkipPolicy.findAt(listOf(sponsor), 10_000, setOf("sponsor")))
        assertEquals(sponsor, SponsorSkipPolicy.findAt(listOf(sponsor), 24_000, setOf("sponsor")))
    }

    @Test fun appliesEndGuardToAvoidSeekingAtSegmentTail() {
        assertNull(SponsorSkipPolicy.findAt(listOf(sponsor), 24_650, setOf("sponsor")))
        assertNull(SponsorSkipPolicy.findAt(listOf(sponsor), 25_000, setOf("sponsor")))
    }

    @Test fun filtersByUserEnabledCategory() {
        assertNull(SponsorSkipPolicy.findAt(listOf(sponsor), 12_000, setOf("intro")))
        assertEquals(intro, SponsorSkipPolicy.findAt(listOf(intro), 35_000, setOf("intro")))
    }

    @Test fun rejectsMalformedIntervals() {
        val malformed = SponsorBlock.Segment(50_000, 49_000, "sponsor")
        assertNull(SponsorSkipPolicy.findAt(listOf(malformed), 49_500, setOf("sponsor")))
    }

    @Test fun keysAreStableAndCategorySpecific() {
        assertEquals("sponsor:10000:25000", SponsorSkipPolicy.key(sponsor))
        assertEquals("intro:30000:40000", SponsorSkipPolicy.key(intro))
    }
}
