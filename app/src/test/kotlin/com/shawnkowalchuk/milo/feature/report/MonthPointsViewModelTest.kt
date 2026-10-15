package com.shawnkowalchuk.milo.feature.report

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.point.FakePointsFile
import com.shawnkowalchuk.milo.data.point.PointCleanup
import com.shawnkowalchuk.milo.data.point.RawPoint
import com.shawnkowalchuk.milo.data.point.RawPointRepository
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.kind
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeRawPointDao
import com.shawnkowalchuk.milo.platform.trip.FakeTripDao
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Report screen's tile for a sent month's GPS points: when it is offered, and what Delete
 * and Keep do.
 */
// A ViewModel's coroutines run on the main dispatcher, which a plain JVM test has to supply
// (setMain), and runCurrent() lets them run. Both are marked experimental by the coroutines
// library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class MonthPointsViewModelTest {
    private val zone = ZoneId.of("America/Edmonton")
    private val september = YearMonth.of(2026, 9)
    private val october = YearMonth.of(2026, 10)

    /** 14 October 2026, noon in Edmonton. */
    private val nowMs = at("2026-10-14T12:00")

    private val tripDao = FakeTripDao()
    private val pointDao = FakeRawPointDao()
    private val sent = MutableStateFlow(listOf(monthSent(september), monthSent(october)))
    private val kept = MutableStateFlow(emptySet<YearMonth>())

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a sent month that has ended is offered with its points`() = runTest {
        val viewModel = watched()
        trip(1, "2026-09-10T08:00", points = 40)

        viewModel.onMonthShown(september)
        runCurrent()

        val offered = viewModel.state.value as MonthPointsState.Offered
        assertEquals(september, offered.month)
        assertEquals(40L, offered.held.points)
    }

    @Test
    fun `no tile for this month, for a date range, or for a month with no points left`() = runTest {
        val viewModel = watched()
        trip(1, "2026-10-02T08:00", points = 40)

        viewModel.onMonthShown(october)
        runCurrent()
        assertEquals(MonthPointsState.Hidden, viewModel.state.value)

        viewModel.onMonthShown(null)
        runCurrent()
        assertEquals(MonthPointsState.Hidden, viewModel.state.value)

        viewModel.onMonthShown(september)
        runCurrent()
        assertEquals(MonthPointsState.Hidden, viewModel.state.value)
    }

    @Test
    fun `Keep stores the month, and the tile goes`() = runTest {
        val viewModel = watched()
        trip(1, "2026-09-10T08:00", points = 40)
        viewModel.onMonthShown(september)
        runCurrent()

        viewModel.onKeep()
        runCurrent()

        assertEquals(setOf(september), kept.value)
        assertEquals(MonthPointsState.Hidden, viewModel.state.value)
        assertEquals(40, pointDao.rows.size)
    }

    @Test
    fun `Delete removes the month's points and says how much was freed`() = runTest {
        val viewModel = watched()
        trip(1, "2026-09-10T08:00", points = 40)
        trip(2, "2026-10-03T08:00", points = 60)
        viewModel.onMonthShown(september)
        runCurrent()

        viewModel.onDelete()
        runCurrent()

        val deleted = viewModel.state.value as MonthPointsState.Deleted
        assertEquals(september, deleted.month)
        assertEquals(400_000L, deleted.bytes)
        assertTrue(pointDao.rows.all { it.tripId == 2L })
        // Another month on the screen shows its own state, not September's.
        viewModel.onMonthShown(october)
        runCurrent()
        assertEquals(MonthPointsState.Hidden, viewModel.state.value)
    }

    private fun TestScope.watched(): MonthPointsViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val cleanup =
            PointCleanup(
                trips = TripRepository(tripDao),
                points = RawPointRepository(pointDao),
                file = FakePointsFile(usedBytes = 1_000_000),
                eventLog = EventLogRepository(FakeEventLogDao()),
                clock = { nowMs },
            )
        val viewModel =
            MonthPointsViewModel(
                cleanup = cleanup,
                sent = sent,
                kept = kept,
                keep = { month -> kept.value += month },
                clock = { nowMs },
                zone = { zone },
            )
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel
    }

    private suspend fun trip(id: Long, start: String, points: Int) {
        val startedAtMs = at(start)
        tripDao.rows +=
            Trip(
                id = id,
                startedAtMs = startedAtMs,
                endedAtMs = startedAtMs + 1_200_000,
                status = TripStatus.FINISHED,
                startedBy = TripStartCause.TRUCK,
                truckSeen = true,
            )
        repeat(points) { index ->
            pointDao.insert(
                RawPoint(
                    tripId = id,
                    wallClockMs = startedAtMs + index * 2_000L,
                    elapsedRealtimeMs = index * 2_000L,
                    latitude = 53.5,
                    longitude = -113.5,
                    accuracyMetres = 4f,
                    speedMetresPerSecond = 10f,
                ),
            )
        }
    }

    private fun at(local: String): Long =
        LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli()

    private fun monthSent(month: YearMonth): SentReport {
        val period = ReportPeriod.Month(month)
        return SentReport(
            kind = period.kind,
            firstDay = period.firstDay.toEpochDay(),
            lastDay = period.lastDay.toEpochDay(),
            sentAtMs = 1_000,
            tripCount = 21,
            distanceMetres = 231_400.0,
            revision = 0,
        )
    }
}
