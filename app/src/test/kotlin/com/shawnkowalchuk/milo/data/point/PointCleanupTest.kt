package com.shawnkowalchuk.milo.data.point

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.kind
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeRawPointDao
import com.shawnkowalchuk.milo.platform.trip.FakeTripDao
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which GPS points a sent month's cleanup deletes, and what it leaves alone. */
class PointCleanupTest {
    private val zone = ZoneId.of("America/Edmonton")
    private val september = YearMonth.of(2026, 9)
    private val october = YearMonth.of(2026, 10)

    private val tripDao = FakeTripDao()
    private val pointDao = FakeRawPointDao()
    private val logDao = FakeEventLogDao()
    private val file = FakePointsFile(usedBytes = 1_000_000)
    private val cleanup =
        PointCleanup(
            trips = TripRepository(tripDao),
            points = RawPointRepository(pointDao),
            file = file,
            eventLog = EventLogRepository(logDao),
            clock = { 1_800_000_000_000L },
        )

    // --- Which months ---

    @Test
    fun `only a whole month's report that has ended counts`() {
        val today = LocalDate.of(2026, 10, 14)
        val sent =
            listOf(
                sent(ReportPeriod.Month(september)),
                // Sent before it ended: not yet.
                sent(ReportPeriod.Month(october)),
                // A date range marks no month.
                sent(ReportPeriod.Range(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))),
            )

        assertEquals(listOf(september), sentMonthsEnded(sent, today))
        // The first day of the next month, the month has ended.
        assertEquals(
            listOf(september, october),
            sentMonthsEnded(sent, LocalDate.of(2026, 11, 1)),
        )
    }

    @Test
    fun `a month sent again is one month, and the months come oldest first`() {
        val august = YearMonth.of(2026, 8)
        val sent =
            listOf(
                sent(ReportPeriod.Month(september)),
                sent(ReportPeriod.Month(august)),
                sent(ReportPeriod.Month(september), revision = 1),
            )

        assertEquals(listOf(august, september), sentMonthsEnded(sent, LocalDate.of(2026, 10, 2)))
    }

    @Test
    fun `the tile is offered for a sent month that ended, unless its points are kept`() {
        val today = LocalDate.of(2026, 10, 14)
        val sent = listOf(sent(ReportPeriod.Month(september)))

        assertTrue(offersCleanup(september, sent, kept = emptySet(), today))
        assertFalse(offersCleanup(september, sent, kept = setOf(september), today))
        assertFalse(offersCleanup(october, sent, kept = emptySet(), today))
        assertFalse(offersCleanup(september, emptyList(), kept = emptySet(), today))
    }

    @Test
    fun `room is shared out by the number of fixes`() {
        assertEquals(250_000L, shareOfBytes(1_000_000, part = 25, whole = 100))
        assertEquals(0L, shareOfBytes(1_000_000, part = 0, whole = 100))
        assertEquals(0L, shareOfBytes(1_000_000, part = 5, whole = 0))
        // Never more than the whole, should the count move between the two reads.
        assertEquals(1_000_000L, shareOfBytes(1_000_000, part = 120, whole = 100))
    }

    // --- Which points ---

    @Test
    fun `the trips that started in the month are counted, whatever became of them`() = runTest {
        trip(1, "2026-09-02T08:00", TripStatus.FINISHED, points = 30)
        trip(2, "2026-09-15T08:00", TripStatus.DISCARDED, points = 10)
        trip(3, "2026-09-20T08:00", TripStatus.DELETED, points = 5)
        // Started in October: not September's.
        trip(4, "2026-10-01T00:30", TripStatus.FINISHED, points = 40)
        // Started on the last evening of September by the clock in Edmonton.
        trip(5, "2026-09-30T23:30", TripStatus.FINISHED, points = 15)

        val held = cleanup.heldIn(listOf(september), zone)

        assertEquals(listOf(1L, 2L, 3L, 5L), held.tripIds.sorted())
        assertEquals(60L, held.points)
        assertEquals(1_000_000L * 60 / 100, held.bytes)
    }

    @Test
    fun `the trip being recorded is never counted`() = runTest {
        trip(1, "2026-09-30T23:50", TripStatus.OPEN, points = 20)
        trip(2, "2026-09-12T08:00", TripStatus.FINISHED, points = 10)

        val held = cleanup.heldIn(listOf(september), zone)

        assertEquals(listOf(2L), held.tripIds)
        assertEquals(10L, held.points)
    }

    @Test
    fun `a month with no trips holds nothing and reads no file`() = runTest {
        val held = cleanup.heldIn(listOf(september), zone)

        assertEquals(HeldPoints(listOf(september), emptyList(), 0, 0), held)
        assertEquals(0, file.reads)
    }

    // --- Deleting ---

    @Test
    fun `a delete removes those trips' points only, compacts the file and says so in the log`() =
        runTest {
            trip(1, "2026-09-02T08:00", TripStatus.FINISHED, points = 30)
            trip(2, "2026-10-02T08:00", TripStatus.FINISHED, points = 20)
            val held = cleanup.heldIn(listOf(september), zone)

            assertTrue(cleanup.delete(held, "on the Report screen"))

            assertEquals(listOf(2L), pointDao.rows.map { it.tripId }.distinct())
            assertEquals(20, pointDao.rows.size)
            assertEquals(1, file.compacts)
            // No trip was touched.
            assertEquals(2, tripDao.rows.size)
            val line = logDao.entries.single()
            assertEquals(EventCategory.REPORT, line.category)
            assertEquals(
                "GPS points of 2026-09 deleted on the Report screen, every month's report having " +
                    "been sent: 30 points of 1 trip(s), about 0.6 MB. The trips, their distances " +
                    "and the sent reports are as they were.",
                line.message,
            )
        }

    @Test
    fun `a delete that fails says so in the log, answers false, and leaves the points`() = runTest {
        trip(1, "2026-09-02T08:00", TripStatus.FINISHED, points = 30)
        val held = cleanup.heldIn(listOf(september), zone)
        pointDao.failNextDelete = IOException("disk full")

        assertFalse(cleanup.delete(held, "in Settings"))

        assertEquals(30, pointDao.rows.size)
        assertEquals(0, file.compacts)
        val line = logDao.entries.single()
        assertEquals(EventCategory.ERROR, line.category)
        assertEquals("Deleting the GPS points of 2026-09 in Settings failed", line.message)
    }

    @Test
    fun `a file that cannot be compacted afterwards is still a delete that worked`() = runTest {
        trip(1, "2026-09-02T08:00", TripStatus.FINISHED, points = 30)
        val held = cleanup.heldIn(listOf(september), zone)
        file.failCompact = IOException("disk full")

        assertTrue(cleanup.delete(held, "in Settings"))

        assertEquals(0, pointDao.rows.size)
        assertEquals(
            listOf(EventCategory.REPORT, EventCategory.ERROR),
            logDao.entries.map { it.category },
        )
        assertEquals(
            "Compacting the GPS points' file after deleting those of 2026-09 failed",
            logDao.entries.last().message,
        )
    }

    private suspend fun trip(id: Long, start: String, status: TripStatus, points: Int) {
        val startedAtMs = LocalDateTime.parse(start).atZone(zone).toInstant().toEpochMilli()
        tripDao.rows +=
            Trip(
                id = id,
                startedAtMs = startedAtMs,
                endedAtMs = if (status == TripStatus.OPEN) null else startedAtMs + 1_200_000,
                status = status,
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

    private fun sent(period: ReportPeriod, revision: Int = 0) = SentReport(
        kind = period.kind,
        firstDay = period.firstDay.toEpochDay(),
        lastDay = period.lastDay.toEpochDay(),
        sentAtMs = 1_000,
        tripCount = 21,
        distanceMetres = 231_400.0,
        revision = revision,
    )
}

/** The points file, as a count of what was asked of it. */
internal class FakePointsFile(private val usedBytes: Long) : PointsFile {
    var reads = 0
    var compacts = 0
    var failCompact: Exception? = null

    override suspend fun usedBytes(): Long {
        reads++
        return usedBytes
    }

    override suspend fun compact() {
        failCompact?.let { throw it }
        compacts++
    }
}
