package com.shawnkowalchuk.milo.data.point

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.util.monthSpan
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.period
import com.shawnkowalchuk.milo.data.trip.TripRepository
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

/**
 * The GPS points that some months' trips hold.
 *
 * @param months the months, oldest first.
 * @param tripIds every trip that started in one of them and is no longer being recorded.
 * @param points how many fixes those trips have stored.
 * @param bytes about how much room the fixes take in the points file: its share of the file,
 * by the number of fixes.
 */
data class HeldPoints(
    val months: List<YearMonth>,
    val tripIds: List<Long>,
    val points: Long,
    val bytes: Long,
)

/**
 * The months whose report was sent and that have ended by [today], oldest first: the months
 * whose GPS points can be deleted. Only a report for the whole month counts, as only it marks
 * a month as submitted (`monthSubmission`). A month sent before it ended counts once it has.
 */
fun sentMonthsEnded(sent: List<SentReport>, today: LocalDate): List<YearMonth> {
    val thisMonth = YearMonth.from(today)
    return sent
        .mapNotNull { (it.period as? ReportPeriod.Month)?.month }
        .filter { it < thisMonth }
        .distinct()
        .sorted()
}

/**
 * Whether the Report screen offers to delete the GPS points of [month]: it is a sent month
 * that has ended, and Shawn has not said to keep its points. Whether any are left is asked of
 * storage afterwards.
 */
fun offersCleanup(
    month: YearMonth,
    sent: List<SentReport>,
    kept: Set<YearMonth>,
    today: LocalDate,
) = month !in kept && month in sentMonthsEnded(sent, today)

/** [part] of the [whole] fixes' share of [usedBytes]. 0 when there are none. */
fun shareOfBytes(usedBytes: Long, part: Long, whole: Long): Long =
    if (whole <= 0 || part <= 0) 0 else usedBytes * minOf(part, whole) / whole

/**
 * Deletes the GPS points of months whose report was sent (2026-10-14). Shawn: "I think a idea
 * of keeping them tell they are submitted to the accountant at the end of the month. or giving
 * the person the option to perge them after he submits his mileage."
 *
 * A closed trip's fixes are read by nothing but an export: its distance, its times and its
 * start and end positions are stored on the trip itself. So deleting them changes no screen, no
 * figure and no report. What is lost is the route, and the chance to measure the trip again
 * with better rules. The trip being recorded is never touched.
 */
class PointCleanup(
    private val trips: TripRepository,
    private val points: RawPointRepository,
    private val file: PointsFile,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
) {
    /** The points that the trips of [months] hold, read from storage now. */
    suspend fun heldIn(months: List<YearMonth>, zone: ZoneId): HeldPoints {
        val tripIds =
            months.flatMap { month ->
                val span = monthSpan(month, zone)
                trips
                    .observeTripsStartedBetween(span.fromMs, span.untilMs)
                    .first()
                    .filter { it.status != TripStatus.OPEN }
                    .map { it.id }
            }
        val held = if (tripIds.isEmpty()) 0 else points.countFor(tripIds)
        val bytes = if (held == 0L) 0 else shareOfBytes(file.usedBytes(), held, points.countAll())
        return HeldPoints(months.sorted(), tripIds, held, bytes)
    }

    /** About how much room every stored fix takes, of every trip. */
    suspend fun storedBytes(): Long = file.usedBytes()

    /**
     * Deletes the points [held] counted and writes one line to the event log, then hands their
     * room back to the phone. A failure is written there too.
     *
     * @param where where Shawn asked for it, for the log: "on the Report screen".
     * @return false if the points could not be deleted: they are as they were. A file that
     * could not be compacted afterwards is still a delete that worked: the points are gone, and
     * their room is used again by the next trips' points, so the file only grows later.
     */
    suspend fun delete(held: HeldPoints, where: String): Boolean {
        val months = held.months.joinToString(", ")
        val removed = attempt("Deleting the GPS points of $months $where") {
            points.removeFor(held.tripIds)
        } ?: return false
        eventLog.add(clock(), EventCategory.REPORT, deletedText(held, removed, months, where))
        attempt("Compacting the GPS points' file after deleting those of $months") {
            file.compact()
        }
        return true
    }

    /** [work]'s answer, or null after writing what failed to the event log. */
    private suspend fun <T : Any> attempt(what: String, work: suspend () -> T): T? = try {
        work()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        eventLog.add(clock(), EventCategory.ERROR, "$what failed", failure.stackTraceToString())
        null
    }
}

private fun deletedText(held: HeldPoints, removed: Long, months: String, where: String): String =
    "GPS points of $months deleted $where, every month's report having been sent: $removed " +
        "points of ${held.tripIds.size} trip(s), about ${megabytes(held.bytes)}. The trips, " +
        "their distances and the sent reports are as they were."

private const val BYTES_PER_MB = 1_000_000.0

private fun megabytes(bytes: Long): String = String.format(
    Locale.ROOT,
    "%.1f MB",
    bytes / BYTES_PER_MB,
)
