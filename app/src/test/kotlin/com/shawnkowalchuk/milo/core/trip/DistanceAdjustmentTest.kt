package com.shawnkowalchuk.milo.core.trip

import org.junit.Assert.assertEquals
import org.junit.Test

/** What a measured distance is multiplied by, and what Settings shows of it. */
class DistanceAdjustmentTest {
    @Test
    fun `Settings at 0 still raises a distance by the 2 percent built in`() {
        assertEquals(1.02, distanceFactor(0), 1e-12)
        assertEquals(0.0, shownAdjustPercent(0), 0.0)
    }

    @Test
    fun `each step is half a percent, up to 5 percent either way`() {
        assertEquals(1.025, distanceFactor(1), 1e-12)
        assertEquals(1.07, distanceFactor(10), 1e-12)
        assertEquals(0.97, distanceFactor(-10), 1e-12)
        assertEquals(1.0, distanceFactor(-4), 1e-12)
        assertEquals(-1.5, shownAdjustPercent(-3), 0.0)
    }

    @Test
    fun `a step count outside the range counts as its nearest end`() {
        assertEquals(distanceFactor(10), distanceFactor(25), 1e-12)
        assertEquals(distanceFactor(-10), distanceFactor(-25), 1e-12)
    }
}
