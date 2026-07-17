package com.neilpontecorvo.soundcloudfiretv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvInteractionRulesTest {
    @Test
    fun referenceFramePreservesAuthoredGeometry() {
        val reference = TvDesignMetrics(1920, 1080)
        assertEquals(1f, reference.scale)
        assertEquals(284, reference.px(284))
        assertEquals(152, reference.px(152))
    }

    @Test
    fun sixColumnGridMaintainsRowAndColumnIntent() {
        assertEquals(5, TvInteractionRules.gridNeighbor(4, TvInteractionRules.GridDirection.RIGHT, 18))
        assertEquals(10, TvInteractionRules.gridNeighbor(4, TvInteractionRules.GridDirection.DOWN, 18))
        assertEquals(4, TvInteractionRules.gridNeighbor(10, TvInteractionRules.GridDirection.UP, 18))
        assertNull(TvInteractionRules.gridNeighbor(0, TvInteractionRules.GridDirection.LEFT, 18))
        assertNull(TvInteractionRules.gridNeighbor(17, TvInteractionRules.GridDirection.DOWN, 18))
    }

    @Test
    fun searchNormalizationRejectsWhitespaceAtCallSite() {
        assertEquals("anelo", TvInteractionRules.normalizeSearchQuery("  anelo  "))
        assertEquals("", TvInteractionRules.normalizeSearchQuery("   "))
    }

    @Test
    fun queueAndSeekStayInsideRealBounds() {
        assertEquals(2, TvInteractionRules.queueTargetIndex(1, 1, 3))
        assertNull(TvInteractionRules.queueTargetIndex(2, 1, 3))
        assertEquals(0L, TvInteractionRules.clampedSeek(5_000L, 60_000L, -10_000L))
        assertEquals(60_000L, TvInteractionRules.clampedSeek(58_000L, 60_000L, 10_000L))
        assertNull(TvInteractionRules.clampedSeek(0L, 0L, 10_000L))
        assertEquals(60_000L, TvInteractionRules.scanStepMs(0))
        assertEquals(120_000L, TvInteractionRules.scanStepMs(2))
        assertEquals(300_000L, TvInteractionRules.scanStepMs(8))
        assertEquals(600_000L, TvInteractionRules.scanStepMs(15))
    }

    @Test
    fun artworkFallbackPrefersProviderArtworkThenCreatorAvatar() {
        assertEquals("art", TvInteractionRules.artworkCandidate("art", "avatar"))
        assertEquals("avatar", TvInteractionRules.artworkCandidate(null, "avatar"))
        assertNull(TvInteractionRules.artworkCandidate(null, null))
    }
}
