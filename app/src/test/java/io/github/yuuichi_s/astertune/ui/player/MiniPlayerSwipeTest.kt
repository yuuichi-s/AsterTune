package io.github.yuuichi_s.astertune.ui.player

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Thresholds of [resolveMiniPlayerSwipeDirection] and the target guard [canSeekToMiniPlayerSwipeTarget].
 *
 * Every value is a pixel value at the density given by [Thresholds], mirroring the conversion the
 * composable does before calling the function.
 */
class MiniPlayerSwipeTest {

    /** Pixel thresholds at a given density, as the composable derives them from dp. */
    private class Thresholds(density: Float) {
        val normalDistance = 64f * density
        val minimumFlingDistance = 24f * density
        val velocity = 1000f * density
    }

    private val mdpi = Thresholds(density = 1f)

    private fun resolve(
        distancePx: Float,
        velocityPxPerSecond: Float,
        thresholds: Thresholds = mdpi,
    ) = resolveMiniPlayerSwipeDirection(
        distancePx = distancePx,
        velocityPxPerSecond = velocityPxPerSecond,
        normalDistanceThresholdPx = thresholds.normalDistance,
        minimumFlingDistancePx = thresholds.minimumFlingDistance,
        velocityThresholdPxPerSecond = thresholds.velocity,
    )

    @Test
    fun leftBeyondDistanceThreshold_isNext() {
        assertEquals(MiniPlayerSwipeDirection.NEXT, resolve(distancePx = -120f, velocityPxPerSecond = 0f))
    }

    @Test
    fun rightBeyondDistanceThreshold_isPrevious() {
        assertEquals(MiniPlayerSwipeDirection.PREVIOUS, resolve(distancePx = 120f, velocityPxPerSecond = 0f))
    }

    @Test
    fun exactlyDistanceThreshold_completes() {
        assertEquals(MiniPlayerSwipeDirection.NEXT, resolve(distancePx = -64f, velocityPxPerSecond = 0f))
        assertEquals(MiniPlayerSwipeDirection.PREVIOUS, resolve(distancePx = 64f, velocityPxPerSecond = 0f))
    }

    @Test
    fun shortSlowDrag_doesNotComplete() {
        assertNull(resolve(distancePx = -63f, velocityPxPerSecond = -100f))
    }

    @Test
    fun zeroDistance_doesNotComplete() {
        assertNull(resolve(distancePx = 0f, velocityPxPerSecond = -5000f))
    }

    @Test
    fun flingShorterThanMinimumDistance_doesNotComplete() {
        assertNull(resolve(distancePx = -23f, velocityPxPerSecond = -5000f))
    }

    @Test
    fun flingAtMinimumDistanceAndVelocity_completes() {
        assertEquals(MiniPlayerSwipeDirection.NEXT, resolve(distancePx = -24f, velocityPxPerSecond = -1000f))
        assertEquals(MiniPlayerSwipeDirection.PREVIOUS, resolve(distancePx = 24f, velocityPxPerSecond = 1000f))
    }

    @Test
    fun flingBelowVelocityThreshold_doesNotComplete() {
        assertNull(resolve(distancePx = -30f, velocityPxPerSecond = -999f))
    }

    @Test
    fun flingWithOppositeVelocity_doesNotComplete() {
        assertNull(resolve(distancePx = -30f, velocityPxPerSecond = 5000f))
        assertNull(resolve(distancePx = 30f, velocityPxPerSecond = -5000f))
    }

    @Test
    fun longDragWithOppositeVelocity_completesByDistance() {
        assertEquals(MiniPlayerSwipeDirection.NEXT, resolve(distancePx = -80f, velocityPxPerSecond = 5000f))
    }

    @Test
    fun highDensity_scalesThresholdsWithPixels() {
        val xxhdpi = Thresholds(density = 3f)

        assertEquals(
            MiniPlayerSwipeDirection.NEXT,
            resolve(distancePx = -24f * 3f, velocityPxPerSecond = -1000f * 3f, thresholds = xxhdpi),
        )
        assertNull(resolve(distancePx = -23f * 3f, velocityPxPerSecond = -5000f * 3f, thresholds = xxhdpi))
    }

    @Test
    fun highDensity_velocityComparedInPixelsPerSecond() {
        val xxhdpi = Thresholds(density = 3f)

        // 1500 px/s clears the raw dp/s number 1000 but not the 3000 px/s threshold it converts to.
        assertNull(resolve(distancePx = -30f * 3f, velocityPxPerSecond = -1500f, thresholds = xxhdpi))
        assertEquals(
            MiniPlayerSwipeDirection.NEXT,
            resolve(distancePx = -30f * 3f, velocityPxPerSecond = -3000f, thresholds = xxhdpi),
        )
    }

    @Test
    fun unsetTargetIndex_cannotSeek() {
        assertFalse(canSeekToMiniPlayerSwipeTarget(targetIndex = C.INDEX_UNSET, currentIndex = 0))
    }

    @Test
    fun targetIndexEqualToCurrent_cannotSeek() {
        assertFalse(canSeekToMiniPlayerSwipeTarget(targetIndex = 2, currentIndex = 2))
    }

    @Test
    fun differentTargetIndex_canSeek() {
        assertTrue(canSeekToMiniPlayerSwipeTarget(targetIndex = 3, currentIndex = 2))
        assertTrue(canSeekToMiniPlayerSwipeTarget(targetIndex = 0, currentIndex = 4))
    }
}
