package com.shawnkowalchuk.milo.platform.car

import com.shawnkowalchuk.milo.core.schedule.DEFAULT_WORK_SCHEDULE
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.schedule.categoryWhileRecorded
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.settings.RunningTripCategory
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The car screen's Business/Personal and its odometer (since 2026-11-12, Shawn: "i couldnt see
 * if it was a business or personal trip and or switch it on the screen", "vehicle mileage
 * would be nice"). What the trip will be saved as is Home's rule, not one of the car's own.
 */
class CarTripCategoryTest {
    private val edmonton = ZoneId.of("America/Edmonton")
    private val recording = TripActivity(trip = carTrip(), truckConnected = true)

    @Test
    fun `without a choice the work hours decide, exactly as Home's pill says`() {
        val shown = carTripCategory(recording, running = null, DEFAULT_WORK_SCHEDULE, edmonton)
        val home = categoryWhileRecorded(STARTED_AT_MS, null, DEFAULT_WORK_SCHEDULE, edmonton)

        assertEquals(home, shown)
    }

    @Test
    fun `a choice stored for this trip wins over the hours`() {
        for (chosen in TripCategory.entries) {
            val running = RunningTripCategory(tripId = 7, chosen)

            assertEquals(
                chosen,
                carTripCategory(recording, running, DEFAULT_WORK_SCHEDULE, edmonton),
            )
        }
    }

    @Test
    fun `a choice left from another trip is not this trip's`() {
        val left = RunningTripCategory(tripId = 6, TripCategory.PERSONAL)
        val byHours = carTripCategory(recording, null, DEFAULT_WORK_SCHEDULE, edmonton)

        assertEquals(byHours, carTripCategory(recording, left, DEFAULT_WORK_SCHEDULE, edmonton))
    }

    @Test
    fun `no trip, or no schedule read yet, has no category`() {
        assertNull(carTripCategory(TripActivity(), null, DEFAULT_WORK_SCHEDULE, edmonton))
        assertNull(carTripCategory(recording, null, schedule = null, zone = edmonton))
    }

    @Test
    fun `the second button offers the other category for the trip it was drawn for`() {
        val business = carContent(recording).copyWith(TripCategory.BUSINESS)
        val personal = carContent(recording).copyWith(TripCategory.PERSONAL)

        assertEquals(CategorySwitch(7, TripCategory.PERSONAL), business.switchTo)
        assertEquals(CategorySwitch(7, TripCategory.BUSINESS), personal.switchTo)
        assertEquals(TripCategory.BUSINESS, business.category)
    }

    @Test
    fun `no second button and no category without a trip, or without a category`() {
        val idle = carScreenContent(
            TripActivity(truckConnected = true),
            businessOf(),
            setupNeedsAttention = false,
            NOW_MS,
            Locale.CANADA,
            DistanceUnit.KILOMETRES,
            category = TripCategory.BUSINESS,
        )

        assertNull(idle.category)
        assertNull(idle.switchTo)
        assertNull(carContent(recording).switchTo)
    }

    @Test
    fun `a switch of the category is drawn at once`() {
        val before = carContent(recording).copyWith(TripCategory.BUSINESS)
        val after = carContent(recording).copyWith(TripCategory.PERSONAL)

        assertFalse(before.differsOnlyInTripFigures(after))
    }

    @Test
    fun `the odometer counting up during a trip waits for the gap, like the trip's figures`() {
        val before = carContent(recording).withOdometer("102,684")
        val aKilometreOn = carContent(recording).withOdometer("102,685")

        assertTrue(before.differsOnlyInTripFigures(aKilometreOn))
    }

    @Test
    fun `an odometer in another unit than the screen's is not shown`() {
        val inMiles = CarOdometer("63,804", DistanceUnit.MILES)
        val shown = carScreenContent(
            recording,
            businessOf(),
            setupNeedsAttention = false,
            NOW_MS,
            Locale.CANADA,
            DistanceUnit.KILOMETRES,
            odometer = inMiles,
        )

        assertNull(shown.odometer)
    }

    @Test
    fun `the short month name is the language's own`() {
        val figures = businessFigures(
            BusinessInput(LocalDate.of(2026, 11, 12), edmonton, emptyList(), 70),
            Locale.CANADA,
            DistanceUnit.KILOMETRES,
        )

        assertEquals("November", figures.monthName)
        assertTrue(figures.shortMonthName.startsWith("Nov"))
        assertTrue(figures.shortMonthName.length <= 4)
    }

    private fun CarScreenContent.copyWith(category: TripCategory): CarScreenContent =
        carScreenContent(
            recording,
            business,
            setupNeedsAttention = false,
            NOW_MS,
            Locale.CANADA,
            unit,
            category = category,
        )

    private fun CarScreenContent.withOdometer(value: String): CarScreenContent =
        copy(odometer = CarOdometer(value, unit))
}
