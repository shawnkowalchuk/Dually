package com.shawnkowalchuk.milo.platform.pasthours

import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.setRunningTripCategory
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import com.shawnkowalchuk.milo.platform.trip.FakeTripDao
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The work-hours question (2026-11-03): the heads-up as the hours end, the question after. */
@OptIn(ExperimentalCoroutinesApi::class)
class PastHoursPromptTest {
    private val zone = ZoneOffset.UTC
    private val store = SettingsStore(FakeSettingsFile())
    private val dao = FakeTripDao()
    private val notices = RecordedNotices()

    private fun at(text: String): Long = LocalDateTime.parse(text).toInstant(zone).toEpochMilli()

    /** A prompt whose clock is the test's: [startMs] plus the virtual time that has passed. */
    private fun TestScope.prompt(startMs: Long) = PastHoursPrompt(
        settings = store,
        trips = TripRepository(dao),
        notices = notices,
        eventLog = EventLogRepository(FakeEventLogDao()),
        clock = { startMs + testScheduler.currentTime },
        zone = { zone },
    )

    @Test
    fun `the heads-up comes when the hours end, not before`() = runTest {
        // Monday 16:25, five minutes before the hours end at 16:30.
        val prompt = prompt(at("2026-11-02T16:25:00"))
        launch { prompt.duringTrip(tripId = 7, startedAtMs = at("2026-11-02T16:25:00")) }

        advanceTimeBy(4 * 60_000L)
        runCurrent()
        assertEquals(emptyList<String>(), notices.calls)

        advanceTimeBy(2 * 60_000L)
        runCurrent()
        assertEquals(listOf("during 7 at ${at("2026-11-02T16:30:00")}"), notices.calls)
    }

    @Test
    fun `a trip chosen on Home is not asked about`() = runTest {
        store.setRunningTripCategory(7, TripCategory.PERSONAL)
        val prompt = prompt(at("2026-11-02T16:25:00"))
        launch { prompt.duringTrip(tripId = 7, startedAtMs = at("2026-11-02T16:25:00")) }

        advanceTimeBy(10 * 60_000L)
        runCurrent()

        assertEquals(emptyList<String>(), notices.calls)
    }

    @Test
    fun `a trip the hours make Personal has no heads-up`() = runTest {
        val prompt = prompt(at("2026-11-02T17:00:00"))
        launch { prompt.duringTrip(tripId = 7, startedAtMs = at("2026-11-02T17:00:00")) }

        advanceTimeBy(60 * 60_000L)
        runCurrent()

        assertEquals(emptyList<String>(), notices.calls)
    }

    @Test
    fun `a Business trip that ran past the hours is asked about when it ends`() = runTest {
        dao.rows += finished(id = 7, byHand = false)

        prompt(at("2026-11-02T17:06:00")).afterTrip(7)

        assertEquals(
            listOf("after 7 ${at("2026-11-02T16:25:00")}-${at("2026-11-02T17:06:00")}"),
            notices.calls,
        )
    }

    @Test
    fun `an answered trip is not asked again, and the heads-up goes`() = runTest {
        dao.rows += finished(id = 7, byHand = true)

        prompt(at("2026-11-02T17:06:00")).afterTrip(7)

        assertEquals(listOf("cancel"), notices.calls)
    }

    private fun finished(id: Long, byHand: Boolean) = Trip(
        id = id,
        startedAtMs = at("2026-11-02T16:25:00"),
        endedAtMs = at("2026-11-02T17:06:00"),
        status = TripStatus.FINISHED,
        startedBy = TripStartCause.TRUCK,
        truckSeen = true,
        category = TripCategory.BUSINESS,
        categorySetByHand = byHand,
        ranPastSchedule = !byHand,
    )

    private class RecordedNotices : PastHoursNotices {
        val calls = mutableListOf<String>()

        override fun duringTrip(tripId: Long, hoursEndMs: Long) {
            calls += "during $tripId at $hoursEndMs"
        }

        override fun afterTrip(tripId: Long, startedAtMs: Long, endedAtMs: Long) {
            calls += "after $tripId $startedAtMs-$endedAtMs"
        }

        override fun cancel() {
            calls += "cancel"
        }
    }
}
