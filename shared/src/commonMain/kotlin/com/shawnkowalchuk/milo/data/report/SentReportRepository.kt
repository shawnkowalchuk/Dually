package com.shawnkowalchuk.milo.data.report

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.time.epochDay
import com.shawnkowalchuk.milo.core.time.firstDay
import com.shawnkowalchuk.milo.core.time.lastDay
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

/**
 * The only way the rest of the app reads or writes the list of sent reports.
 *
 * It can be added to and read, and a report that was recorded by mistake can be removed from
 * it. Whether a month is submitted is not stored anywhere: it is worked out from this list
 * (`monthSubmission`), so the two can never disagree, and a month whose only report is removed
 * is "not submitted" again by that alone.
 *
 * With the online backup on more than one phone, the list holds the other phones' sent reports
 * too ([others], issue #164): a month sent on any phone of the account is submitted on every
 * one. They come from the backup at each sync, and only this phone's own are written here.
 *
 * @param others the other phones' sent reports (`SettingsStore.otherPhonesSent`), each
 * [SentReport.fromOtherPhone]. None for a phone without the online backup, and in tests.
 */
class SentReportRepository(
    private val dao: SentReportDao,
    private val others: Flow<List<SentReport>> = flowOf(emptyList()),
) {
    /**
     * Every sent report, this phone's and the other phones', newest first, and again each time
     * one is added or removed, here or by a sync.
     */
    fun observeSent(): Flow<List<SentReport>> = combine(dao.observeAll(), others) { own, theirs ->
        if (theirs.isEmpty()) {
            own
        } else {
            (own + theirs).sortedWith(
                compareByDescending<SentReport> { it.sentAtMs }.thenByDescending { it.id },
            )
        }
    }

    /** Every sent report as the list stands right now, newest first. */
    suspend fun currentSent(): List<SentReport> = observeSent().first()

    /**
     * Removes one report from the list, for an "I sent it" that was a mistake. Nothing else is
     * written: the reports that stay keep their revision numbers, and the next report for the
     * period takes the number after the highest one still listed (`nextRevision`).
     *
     * Safe to repeat: a second call finds no such row and changes nothing.
     *
     * A report another phone sent ([SentReport.fromOtherPhone]) is not removed here: null, as
     * for an id the list does not hold. It is removed on the phone that sent it.
     *
     * @return what was removed and what is left for its period, the other phones' reports of it
     * included, or null if this phone's own list holds no report with this [id].
     */
    suspend fun remove(id: Long): RemovedReport? {
        if (id < 0) return null
        val removed = dao.remove(id) ?: return null
        val theirs = others.first().filter {
            it.kind == removed.report.kind &&
                it.firstDay == removed.report.firstDay &&
                it.lastDay == removed.report.lastDay
        }
        return if (theirs.isEmpty()) {
            removed
        } else {
            removed.copy(
                leftForPeriod =
                    removed.leftForPeriod + theirs,
            )
        }
    }

    /**
     * Records that Shawn sent a report. Call it once, when he has said so: it is **not** safe
     * to repeat, because a second call records a second report, as a revision of the first.
     *
     * @param sentAtMs when the email app was opened with the report: the day it counts as
     * sent.
     * @param tripCount and [distanceMetres] are what the report listed and added up to.
     * @param unit the unit the report was printed in, which the total is a whole number of
     * tenths of.
     * @return the row as stored, with the revision number it was given.
     */
    suspend fun recordSent(
        period: ReportPeriod,
        sentAtMs: Long,
        tripCount: Int,
        distanceMetres: Double,
        unit: DistanceUnit,
    ): SentReport {
        require(sentAtMs >= 0) { "A timestamp cannot be negative: $sentAtMs ms" }
        require(tripCount >= 0) { "A report cannot list a negative number of trips: $tripCount" }
        require(distanceMetres.isFinite() && distanceMetres >= 0.0) {
            "A report's total must be a finite, non-negative distance, but was $distanceMetres"
        }
        return dao.insertAsNextRevision(
            SentReport(
                kind = period.kind,
                firstDay = period.firstDay.epochDay(),
                lastDay = period.lastDay.epochDay(),
                sentAtMs = sentAtMs,
                tripCount = tripCount,
                distanceMetres = distanceMetres,
                // At least the number after the other phones' reports of the period (issue #164);
                // the insert's transaction raises it past this phone's own.
                revision = nextRevision(sentFor(period, others.first())),
                distanceUnit = unit,
            ),
        )
    }
}
