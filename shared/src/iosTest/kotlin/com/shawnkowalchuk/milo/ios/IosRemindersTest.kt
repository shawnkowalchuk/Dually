package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.text.Locale
import com.shawnkowalchuk.milo.core.time.dateOf
import com.shawnkowalchuk.milo.core.time.momentOf
import com.shawnkowalchuk.milo.core.time.systemZoneId
import com.shawnkowalchuk.milo.core.time.timeOf
import com.shawnkowalchuk.milo.core.time.yearMonthOf
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.report.SentReportRepository
import com.shawnkowalchuk.milo.data.settings.setNothingRecordedEnabled
import com.shawnkowalchuk.milo.data.trip.Trip
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The monthly reminder and the daily check on the iPhone (2026-11-15), planned by Android's
 * rules: the days the reminder comes on, and a work day with a trip losing its check.
 */
class IosRemindersTest {
    private val world = RecorderWorld()
    private val zone = systemZoneId()
    private val sent = SentReportRepository(world.sentReportDao())
    private val reminders =
        IosReminders(world.settings, world.trips, sent, { zone }, { Locale("en_CA") })

    @AfterTest
    fun cleanUp() = world.close()

    private fun at(day: Int, hour: Int, month: Int = 11) =
        momentOf(dateOf(2026, month, day), timeOf(hour, 0), zone, null)

    private suspend fun trip(startMs: Long, category: TripCategory, byHand: Boolean = false) {
        world.tripDao().insert(
            Trip(
                startedAtMs = startMs,
                endedAtMs = startMs + 1_800_000,
                status = TripStatus.FINISHED,
                startedBy = TripStartCause.TRUCK,
                truckSeen = true,
                distanceMetres = 12_000.0,
                category = category,
                addedByHand = byHand,
            ),
        )
    }

    @Test
    fun `the reminder comes at nine each day from its day until the month is sent`() = runTest {
        trip(at(day = 20, hour = 10, month = 10), TripCategory.BUSINESS)
        world.settings.setReminderDay(28)

        val early = reminders.reminderPlan(at(day = 10, hour = 8))
        assertFalse(early.dueNow, "the 28th has not come")
        assertEquals(listOf(28, 29, 30).map { at(day = it, hour = 9) }, early.fires)
        assertTrue(early.title.contains("October"))

        val onTheDay = reminders.reminderPlan(at(day = 28, hour = 10))
        assertTrue(onTheDay.dueNow)
        assertEquals(
            listOf(29, 30).map {
                at(day = it, hour = 9)
            },
            onTheDay.fires,
            "today's nine o'clock is past",
        )

        sent.recordSent(
            ReportPeriod.Month(yearMonthOf(2026, 10)),
            at(day = 28, hour = 11),
            1,
            12_000.0,
            DistanceUnit.KILOMETRES,
        )
        val afterSending = reminders.reminderPlan(at(day = 28, hour = 12))
        assertFalse(afterSending.dueNow)
        assertTrue(afterSending.fires.isEmpty())
    }

    @Test
    fun `no Business trip last month or the switch off brings no reminder`() = runTest {
        trip(at(day = 20, hour = 10, month = 10), TripCategory.PERSONAL)
        assertTrue(reminders.reminderPlan(at(day = 10, hour = 8)).fires.isEmpty())

        trip(at(day = 21, hour = 10, month = 10), TripCategory.BUSINESS)
        world.settings.setReminderEnabled(false)
        assertTrue(reminders.reminderPlan(at(day = 10, hour = 8)).fires.isEmpty())
    }

    @Test
    fun `a work day with a trip of Dually's loses its check and one added by hand does not`() =
        runTest {
            // Tuesday 10 November 2026, before noon: the default work week, checked at noon.
            val tuesdayMorning = at(day = 10, hour = 8)
            val plan = reminders.dailyCheckPlan(tuesdayMorning)
            assertEquals(at(day = 10, hour = 12), plan.fires.first(), "today at noon")
            assertFalse(at(day = 14, hour = 12) in plan.fires, "Saturday is not a work day")
            assertEquals("No trip recorded today", plan.title)

            trip(at(day = 10, hour = 7), TripCategory.BUSINESS, byHand = true)
            assertEquals(
                at(day = 10, hour = 12),
                reminders.dailyCheckPlan(tuesdayMorning).fires.first(),
            )

            trip(at(day = 10, hour = 7) + 600_000, TripCategory.BUSINESS)
            assertEquals(
                at(day = 11, hour = 12),
                reminders.dailyCheckPlan(tuesdayMorning).fires.first(),
            )

            world.settings.setNothingRecordedEnabled(false)
            assertTrue(reminders.dailyCheckPlan(tuesdayMorning).fires.isEmpty())
        }
}
