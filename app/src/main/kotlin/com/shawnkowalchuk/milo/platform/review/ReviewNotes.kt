package com.shawnkowalchuk.milo.platform.review

import com.shawnkowalchuk.milo.core.review.NotAskedWhy
import com.shawnkowalchuk.milo.core.review.ReviewFacts
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.crash.CrashRecord
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import java.io.IOException
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

/**
 * What [ReviewPrompts] writes to the event log, and the one place where whatever fails in it is
 * caught. It was part of that class until the file reached its size limit (ENGINEERING_STANDARDS
 * section 3), as [SaidAtHome] below was.
 *
 * **Nothing that goes wrong around the review card goes any further.** Left alone, a failure
 * would end the process, and the trip service runs in it.
 *
 * @param crashFileStore where a line goes if the event log itself cannot be written.
 * @param clock MilO's own clock (ADR-006), which dates every line.
 */
internal class ReviewNotes(
    private val eventLog: EventLogRepository,
    private val crashFileStore: CrashFileStore,
    private val clock: () -> Long,
) {
    /**
     * Runs one piece of the review card's work and catches whatever it throws.
     *
     * **A settings file that cannot be read is not written down here:** nothing is asked for
     * then, at every look, and the trip controller's own line already says that the file
     * cannot be read.
     *
     * @param otherwise what the work comes to when it failed.
     */
    suspend fun <T> keptApart(otherwise: T, work: suspend () -> T): T = try {
        work()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: IOException) {
        otherwise
    } catch (failure: Exception) {
        // Every kind of failure, on purpose: see the class comment.
        report("MilO could not look at the moments of Google Play's review card", failure)
        otherwise
    }

    /** One `ERROR` line for something that failed and was caught. */
    suspend fun report(message: String, failure: Exception) {
        write(EventCategory.ERROR, message, failure.stackTraceToString(), failure)
    }

    /**
     * One line for the event log. If the log is what fails, the line goes to a crash file, as
     * the one-year party's does, and whoever asked carries on.
     *
     * @param unlogged the failure the line is about, if it is about one, for the crash file.
     */
    suspend fun write(
        category: EventCategory,
        message: String,
        detail: String? = null,
        unlogged: Exception? = null,
    ) {
        val atMs = clock()
        try {
            eventLog.add(atMs, category, message, detail)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (logFailure: Exception) {
            val lost = IllegalStateException(message, unlogged)
            lost.addSuppressed(logFailure)
            crashFileStore.write(CrashRecord.from(atMs, Thread.currentThread().name, lost))
        }
    }
}

/**
 * What one run of MilO has said, at Home, of a moment that waits there: Home is opened many
 * times while a moment waits, and the Log is to say why once, not at every opening.
 *
 * TODO(debt): in memory only, so the line is written once for each run of MilO's process, not
 *  once: through the thirty days after a launch, and on the day of a request Google Play gave
 *  no flow for. Storing what was said would take a sixth value in the settings file. See
 *  docs/FINDINGS_LOG.md, 2026-10-11.
 */
internal class SaidAtHome {
    /** The launch whose thirty days were last said to be waited out. */
    private var waitingSince: Long? = null

    /** The day it was last said that MilO has been to Google Play today. */
    private var onceTodayOn: LocalDate? = null

    /** A request Google Play gave no flow for has just said "not before tomorrow", on [day]. */
    fun notBeforeTomorrow(day: LocalDate) {
        onceTodayOn = day
    }

    /**
     * Whether this run has said before that a moment waits for [why]: for this launch's thirty
     * days, or for the rest of this day. If it has not, it counts as said from now on.
     */
    fun already(facts: ReviewFacts, why: NotAskedWhy): Boolean = when (why) {
        NotAskedWhy.ASKED_WITHIN_THIRTY_DAYS -> {
            val launch = facts.stored.lastLaunchAtMs
            (waitingSince == launch).also { waitingSince = launch }
        }

        NotAskedWhy.ONCE_TODAY_ALREADY -> {
            val today = localDateOf(facts.nowMs, facts.zone)
            (onceTodayOn == today).also { onceTodayOn = today }
        }

        // Said once ever, or never at Home: neither is remembered here.
        NotAskedWhy.NOT_FROM_GOOGLE_PLAY, NotAskedWhy.MAY_BE_DRIVING -> false
    }
}
