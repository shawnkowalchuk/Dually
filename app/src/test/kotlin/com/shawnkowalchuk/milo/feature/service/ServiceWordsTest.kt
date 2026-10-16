package com.shawnkowalchuk.milo.feature.service

import com.shawnkowalchuk.milo.core.util.DistanceUnit
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServiceWordsTest {
    @Test
    fun `a cost is read in dollars and kept in cents`() {
        assertEquals(5_999L, parseCost("59.99"))
        assertEquals(120_000L, parseCost("$1,200"))
        assertEquals(8_050L, parseCost(" 80.5 "))
        assertEquals(99_999_999L, parseCost("999999.99"))
    }

    @Test
    fun `what is not a cost is refused`() {
        for (typed in listOf("", ".", "12.345", "abc", "-5", "1000000", "1.2.3")) {
            assertNull(typed, parseCost(typed))
        }
    }

    @Test
    fun `a cost is written with two decimals`() {
        assertEquals("$1,200.00", formatCost(120_000, Locale.CANADA))
        assertEquals("$59.99", formatCost(5_999, Locale.CANADA))
    }

    @Test
    fun `kilometres are shown in the unit chosen and typed back`() {
        assertEquals(8_000L, kmToShown(8_000, DistanceUnit.KILOMETRES))
        assertEquals(4_971L, kmToShown(8_000, DistanceUnit.MILES))
        assertEquals(8_047L, shownToKm(5_000, DistanceUnit.MILES))
        assertEquals(5_000L, kmToShown(shownToKm(5_000, DistanceUnit.MILES), DistanceUnit.MILES))
    }
}
