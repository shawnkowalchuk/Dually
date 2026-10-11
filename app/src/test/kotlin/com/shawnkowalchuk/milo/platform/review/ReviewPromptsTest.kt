package com.shawnkowalchuk.milo.platform.review

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.shawnkowalchuk.milo.core.review.MomentStanding
import com.shawnkowalchuk.milo.core.review.ReviewMoment
import com.shawnkowalchuk.milo.core.review.ReviewOccasion
import com.shawnkowalchuk.milo.core.review.ReviewsStored
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogDao
import com.shawnkowalchuk.milo.data.eventlog.EventLogEntry
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import com.shawnkowalchuk.milo.platform.trip.UnreadableSettingsFile
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private val EDMONTON: ZoneId = ZoneId.of("America/Edmonton")

private fun at(day: Int, hour: Int, minute: Int = 0): Long =
    LocalDateTime.of(2026, 10, day, hour, minute).atZone(EDMONTON).toInstant().toEpochMilli()

private val FIVE = setOf(ReviewMoment.FIVE_TRIPS)
private val HUNDRED = setOf(ReviewMoment.HUNDRED_TRIPS)
private val BOTH = setOf(ReviewMoment.FIVE_TRIPS, ReviewMoment.HUNDRED_TRIPS)
private val REPORT = setOf(ReviewMoment.REPORT_SENT)

private const val DECIDES =
    "Google Play decides whether the card appears, and MilO is not told. MilO does not ask " +
        "for this again"

/** A settings file that reads as [file] does and refuses every write, as a full disk would. */
private class ReadOnlyFile(private val file: FakeSettingsFile) : DataStore<Preferences> {
    override val data: Flow<Preferences> = file.data

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = throw IOException("the disk is full")
}

/**
 * [ReviewPrompts] on a settings file and an event log held in memory, with Google Play's card
 * replaced by [StandInCard]: when Google Play is asked, what counts as asked, what the Log
 * says, and that nothing that goes wrong here goes any further.
 */
// currentTime is how a test reads the scheduler's clock; the API is marked experimental.
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewPromptsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val log = FakeEventLogDao()
    private val file = FakeSettingsFile()
    private val settings = SettingsStore(file)
    private val trips = MutableStateFlow(0)
    private val driving = MutableStateFlow(false)
    private var fromGooglePlay = true
    private var nowMs = at(11, 12)
    private val card = StandInCard()
    private var stillFitting = true

    private fun promptsOn(
        file: DataStore<Preferences> = this.file,
        trips: Flow<Int> = this.trips,
        eventLog: EventLogDao = log,
    ) = ReviewPrompts(
        settings = SettingsStore(file),
        recordedTrips = trips,
        mayBeDriving = driving,
        fromGooglePlay = { fromGooglePlay },
        eventLog = EventLogRepository(eventLog),
        crashFileStore = CrashFileStore(folder.root),
        clock = { nowMs },
        zone = { EDMONTON },
    )

    private suspend fun ReviewPrompts.askAt(occasion: ReviewOccasion) =
        ask(occasion, card) { stillFitting }

    private fun lines(category: EventCategory = EventCategory.PROCESS) =
        log.entries.filter { it.category == category }.map { it.message }

    private suspend fun stored(): ReviewsStored = settings.current().reviews

    // ---- Asked ----------------------------------------------------------------------------------

    @Test
    fun `at five recorded trips Google Play is asked once, and that is remembered`() = runTest {
        val prompts = promptsOn()
        trips.value = 5

        assertEquals(FIVE, prompts.look(ReviewOccasion.HOME))
        // A look asks nothing of Google Play, stores nothing and says nothing.
        assertEquals(0, card.requests)
        assertEquals(MiloSettings(), settings.current())
        assertTrue(log.entries.isEmpty())

        prompts.askAt(ReviewOccasion.HOME)

        assertEquals(1, card.requests)
        assertEquals(1, card.launches)
        assertEquals(MomentStanding.ASKED, stored().of(ReviewMoment.FIVE_TRIPS))
        assertEquals(MomentStanding.OPEN, stored().of(ReviewMoment.HUNDRED_TRIPS))
        assertEquals(nowMs, stored().lastAttemptAtMs)
        assertEquals(
            listOf(
                "Review card: MilO holds 5 recorded trips, and asks at 5. MilO asked " +
                    "Google Play to show its review card. $DECIDES",
            ),
            lines(),
        )
        assertEquals(1, log.entries.size)
    }

    @Test
    fun `once asked for, a moment is never looked at again, on any day`() = runTest {
        val prompts = promptsOn()
        trips.value = 5
        prompts.askAt(ReviewOccasion.HOME)

        for (day in 12..31) {
            nowMs = at(day, 12)
            trips.value += 3
            assertEquals(emptySet<ReviewMoment>(), prompts.look(ReviewOccasion.HOME))
            prompts.askAt(ReviewOccasion.HOME)
        }

        assertEquals(1, card.requests)
        assertEquals(1, log.entries.size)
    }

    @Test
    fun `below five trips nothing is asked, stored or written`() = runTest {
        val prompts = promptsOn()
        trips.value = 4

        assertEquals(emptySet<ReviewMoment>(), prompts.look(ReviewOccasion.HOME))
        prompts.askAt(ReviewOccasion.HOME)

        assertEquals(0, card.requests)
        assertEquals(MiloSettings(), settings.current())
        assertTrue(log.entries.isEmpty())
    }

    @Test
    fun `a MilO updated with 150 trips asks once, and both numbers count as asked`() = runTest {
        val prompts = promptsOn()
        trips.value = 150

        assertEquals(BOTH, prompts.look(ReviewOccasion.HOME))
        prompts.askAt(ReviewOccasion.HOME)

        assertEquals(1, card.launches)
        assertEquals(MomentStanding.ASKED, stored().of(ReviewMoment.FIVE_TRIPS))
        assertEquals(MomentStanding.ASKED, stored().of(ReviewMoment.HUNDRED_TRIPS))
        assertEquals(
            listOf(
                "Review card: MilO holds 150 recorded trips, and asks at 5 and at 100. MilO " +
                    "asked Google Play to show its review card, once for all of them. $DECIDES",
            ),
            lines(),
        )
        nowMs = at(12, 12)
        assertEquals(emptySet<ReviewMoment>(), prompts.look(ReviewOccasion.HOME))
    }

    @Test
    fun `a hundred trips are asked for when they come, after the five`() = runTest {
        val prompts = promptsOn()
        trips.value = 5
        prompts.askAt(ReviewOccasion.HOME)

        nowMs = at(25, 17)
        trips.value = 99
        assertEquals(emptySet<ReviewMoment>(), prompts.look(ReviewOccasion.HOME))
        trips.value = 100
        assertEquals(HUNDRED, prompts.look(ReviewOccasion.HOME))
        prompts.askAt(ReviewOccasion.HOME)

        assertEquals(2, card.launches)
        assertEquals(
            "Review card: MilO holds 100 recorded trips, and asks at 100. MilO asked " +
                "Google Play to show its review card. $DECIDES",
            lines().last(),
        )
    }

    @Test
    fun `after I sent it Google Play is asked once, with any number of trips`() = runTest {
        val prompts = promptsOn()
        trips.value = 2

        assertEquals(REPORT, prompts.look(ReviewOccasion.REPORT_SENT))
        prompts.askAt(ReviewOccasion.REPORT_SENT)

        assertEquals(1, card.launches)
        assertEquals(MomentStanding.ASKED, stored().of(ReviewMoment.REPORT_SENT))
        assertEquals(
            listOf(
                "Review card: A report was recorded as sent with \"I sent it\". MilO asked " +
                    "Google Play to show its review card. $DECIDES",
            ),
            lines(),
        )
        // The next report, a month on: nothing.
        nowMs = at(30, 9)
        assertEquals(emptySet<ReviewMoment>(), prompts.look(ReviewOccasion.REPORT_SENT))
        assertEquals(1, log.entries.size)
    }

    // ---- Only a copy from Google Play -----------------------------------------------------------

    @Test
    fun `a copy Google Play did not install asks nothing, says why once, and uses nothing up`() =
        runTest {
            fromGooglePlay = false
            val prompts = promptsOn()
            trips.value = 5

            assertEquals(emptySet<ReviewMoment>(), prompts.look(ReviewOccasion.HOME))
            prompts.askAt(ReviewOccasion.HOME)

            assertEquals(0, card.requests)
            assertEquals(
                listOf(
                    "Review card: MilO holds 5 recorded trips, and asks at 5. MilO asked " +
                        "Google Play for nothing: this copy of MilO was not installed by Google " +
                        "Play, and Google Play shows its review card only in one that was. " +
                        "Nothing is used up: a copy from Google Play asks. This is written once",
                ),
                lines(),
            )
            val after = stored()
            assertEquals(
                MomentStanding.SAID_NOT_FROM_GOOGLE_PLAY,
                after.of(ReviewMoment.FIVE_TRIPS),
            )
            assertNull(after.lastAttemptAtMs)

            // Every later opening of Home, in this process and in the next: not a word more.
            repeat(3) { prompts.look(ReviewOccasion.HOME) }
            nowMs = at(14, 8)
            trips.value = 30
            promptsOn().look(ReviewOccasion.HOME)
            assertEquals(1, log.entries.size)

            // Google Play updates the copy: now it asks, for the moment that was only talked of.
            fromGooglePlay = true
            val fromPlay = promptsOn()
            assertEquals(FIVE, fromPlay.look(ReviewOccasion.HOME))
            fromPlay.askAt(ReviewOccasion.HOME)
            assertEquals(1, card.launches)
            assertEquals(MomentStanding.ASKED, stored().of(ReviewMoment.FIVE_TRIPS))
        }

    @Test
    fun `such a copy says it once more for each moment it reaches later`() = runTest {
        fromGooglePlay = false
        val prompts = promptsOn()
        trips.value = 5
        prompts.look(ReviewOccasion.HOME)

        trips.value = 100
        prompts.look(ReviewOccasion.HOME)
        prompts.look(ReviewOccasion.HOME)
        prompts.look(ReviewOccasion.REPORT_SENT)
        prompts.look(ReviewOccasion.REPORT_SENT)

        assertEquals(3, lines().size)
        assertTrue(
            lines()[1].startsWith(
                "Review card: MilO holds 100 recorded trips, and asks at 100.",
            ),
        )
        assertTrue(lines()[2].startsWith("Review card: A report was recorded as sent"))
        assertTrue(lines().all { it.endsWith("This is written once") })
        assertEquals(0, card.requests)
    }

    // ---- Never while he may be driving ----------------------------------------------------------

    @Test
    fun `at Home a drive holds the moment back without a word, and it is asked for afterwards`() =
        runTest {
            val prompts = promptsOn()
            trips.value = 5
            driving.value = true

            repeat(4) { assertEquals(emptySet<ReviewMoment>(), prompts.look(ReviewOccasion.HOME)) }
            prompts.askAt(ReviewOccasion.HOME)

            assertEquals(0, card.requests)
            assertEquals(MiloSettings(), settings.current())
            assertTrue(log.entries.isEmpty())

            driving.value = false
            assertEquals(FIVE, prompts.look(ReviewOccasion.HOME))
        }

    @Test
    fun `after I sent it a drive lets the moment pass, says so, and uses nothing up`() = runTest {
        val prompts = promptsOn()
        driving.value = true

        assertEquals(emptySet<ReviewMoment>(), prompts.look(ReviewOccasion.REPORT_SENT))

        assertEquals(0, card.requests)
        assertEquals(MiloSettings(), settings.current())
        assertEquals(
            listOf(
                "Review card: A report was recorded as sent with \"I sent it\". MilO asked " +
                    "Google Play for nothing: a trip is being recorded or a vehicle is " +
                    "connected, and MilO never asks while a drive may be under way. Nothing is " +
                    "used up. MilO asks after the next report that is recorded as sent with " +
                    "\"I sent it\"",
            ),
            lines(),
        )
        // The next report, with the truck parked and off.
        driving.value = false
        assertEquals(REPORT, prompts.look(ReviewOccasion.REPORT_SENT))
    }

    @Test
    fun `a drive that begins while Google Play is asked keeps the card from being shown`() =
        runTest {
            val prompts = promptsOn()
            trips.value = 5
            card.answer = {
                // The truck connects while the Play Store is answering.
                driving.value = true
                card.given()
            }

            prompts.askAt(ReviewOccasion.HOME)

            assertEquals(1, card.requests)
            assertEquals(0, card.launches)
            assertEquals(MiloSettings(), settings.current())
            assertEquals(
                listOf(
                    "Review card: MilO holds 5 recorded trips, and asks at 5. Google Play " +
                        "had a review card ready, but by then MilO had left that screen or a " +
                        "drive may have begun, so MilO did not have it shown. Nothing counts " +
                        "as asked, and MilO asks at the next such moment",
                ),
                lines(),
            )
        }

    @Test
    fun `a screen that was left while Google Play was asked is not shown the card either`() =
        runTest {
            val prompts = promptsOn()
            trips.value = 5
            stillFitting = false

            prompts.askAt(ReviewOccasion.HOME)

            assertEquals(0, card.launches)
            assertEquals(MiloSettings(), settings.current())
            assertEquals(1, lines().size)
            // Back on Home, the same day: asked.
            stillFitting = true
            assertEquals(FIVE, prompts.look(ReviewOccasion.HOME))
            prompts.askAt(ReviewOccasion.HOME)
            assertEquals(1, card.launches)
        }

    // ---- Never twice in one day -----------------------------------------------------------------

    @Test
    fun `the same day's second moment is held, and the Log says why`() = runTest {
        val prompts = promptsOn()
        trips.value = 5
        nowMs = at(11, 8)
        prompts.askAt(ReviewOccasion.HOME)

        // That afternoon the month's report is sent.
        nowMs = at(11, 16)
        assertEquals(emptySet<ReviewMoment>(), prompts.look(ReviewOccasion.REPORT_SENT))
        prompts.askAt(ReviewOccasion.REPORT_SENT)

        assertEquals(1, card.requests)
        assertEquals(MomentStanding.OPEN, stored().of(ReviewMoment.REPORT_SENT))
        assertEquals(at(11, 8), stored().lastAttemptAtMs)
        assertEquals(
            "Review card: A report was recorded as sent with \"I sent it\". MilO asked Google " +
                "Play for nothing: it went to Google Play for the card once already today, and " +
                "never does twice in one day. Nothing is used up. MilO asks after the next " +
                "report that is recorded as sent with \"I sent it\"",
            lines().last(),
        )
        // The next report is sent on another day: asked.
        nowMs = at(12, 9)
        assertEquals(REPORT, prompts.look(ReviewOccasion.REPORT_SENT))
        prompts.askAt(ReviewOccasion.REPORT_SENT)
        assertEquals(2, card.launches)
    }

    @Test
    fun `at Home the day's hold is said once a day, however often Home is opened`() = runTest {
        val prompts = promptsOn()
        nowMs = at(11, 8)
        prompts.askAt(ReviewOccasion.REPORT_SENT)
        trips.value = 5

        nowMs = at(11, 17)
        repeat(5) { assertEquals(emptySet<ReviewMoment>(), prompts.look(ReviewOccasion.HOME)) }

        assertEquals(2, lines().size)
        assertEquals(
            "Review card: MilO holds 5 recorded trips, and asks at 5. MilO asked Google " +
                "Play for nothing: it went to Google Play for the card once already today, and " +
                "never does twice in one day. Nothing is used up. MilO asks the next time Home " +
                "is open on another day, with no trip being recorded and no vehicle connected",
            lines().last(),
        )
        assertEquals(1, card.requests)
        // The next morning: asked.
        nowMs = at(12, 7)
        assertEquals(FIVE, prompts.look(ReviewOccasion.HOME))
    }

    // ---- Google Play gives no flow --------------------------------------------------------------

    @Test
    fun `a request Google Play gives no flow for counts as nothing, and is tried the next day`() =
        runTest {
            val prompts = promptsOn()
            trips.value = 5
            card.answer = { ReviewFlow.NotGiven("the Play Store app was not found on this phone") }

            prompts.askAt(ReviewOccasion.HOME)

            assertEquals(0, card.launches)
            assertEquals(MomentStanding.OPEN, stored().of(ReviewMoment.FIVE_TRIPS))
            assertEquals(nowMs, stored().lastAttemptAtMs)
            assertEquals(
                listOf(
                    "Review card: MilO holds 5 recorded trips, and asks at 5. MilO asked " +
                        "Google Play for its review card, and Google Play gave none to show: " +
                        "the Play Store app was not found on this phone. Nothing counts as " +
                        "asked. MilO tries again at the next such moment, not before tomorrow",
                ),
                lines(),
            )

            // The rest of the day: Home is opened again and again. Nothing more is asked or said.
            nowMs = at(11, 22)
            repeat(3) {
                assertEquals(emptySet<ReviewMoment>(), prompts.look(ReviewOccasion.HOME))
                prompts.askAt(ReviewOccasion.HOME)
            }
            assertEquals(1, card.requests)
            assertEquals(1, log.entries.size)

            // The next day Google Play answers.
            nowMs = at(12, 7)
            card.answer = { card.given() }
            assertEquals(FIVE, prompts.look(ReviewOccasion.HOME))
            prompts.askAt(ReviewOccasion.HOME)
            assertEquals(2, card.requests)
            assertEquals(1, card.launches)
            assertEquals(MomentStanding.ASKED, stored().of(ReviewMoment.FIVE_TRIPS))
        }

    @Test
    fun `a Google Play that does not answer is waited for ten seconds and no longer`() = runTest {
        val prompts = promptsOn()
        trips.value = 5
        card.answer = { awaitCancellation() }

        prompts.askAt(ReviewOccasion.HOME)

        assertEquals(REQUEST_LIMIT_MS, currentTime)
        assertEquals(0, card.launches)
        assertEquals(MomentStanding.OPEN, stored().of(ReviewMoment.FIVE_TRIPS))
        assertTrue(
            lines().single().contains("gave none to show: Google Play did not answer in 10 s."),
        )
    }

    @Test
    fun `whatever asking Google Play throws stays here, and counts as no flow`() = runTest {
        val prompts = promptsOn()
        trips.value = 5
        card.answer = { throw IllegalStateException("the library fell over") }

        prompts.askAt(ReviewOccasion.HOME)

        assertEquals(MomentStanding.OPEN, stored().of(ReviewMoment.FIVE_TRIPS))
        assertEquals(nowMs, stored().lastAttemptAtMs)
        val line = log.entries.single()
        assertTrue(line.message.contains("gave none to show: the request failed."))
        assertTrue(line.detail.orEmpty().contains("the library fell over"))
    }

    @Test
    fun `a flow that cannot be launched counts as no flow`() = runTest {
        val prompts = promptsOn()
        trips.value = 5
        card.answer = { ReviewFlow.Given { throw IllegalStateException("no screen to start on") } }

        prompts.askAt(ReviewOccasion.HOME)

        assertEquals(MomentStanding.OPEN, stored().of(ReviewMoment.FIVE_TRIPS))
        assertEquals(nowMs, stored().lastAttemptAtMs)
        val line = log.entries.single()
        assertTrue(line.message.contains("gave none to show: its screen could not be started."))
        assertTrue(line.detail.orEmpty().contains("no screen to start on"))
    }

    @Test
    fun `a flow whose screen has gone meanwhile is not launched, and nothing is used up`() =
        runTest {
            val prompts = promptsOn()
            trips.value = 5
            // The phone was turned while the Play Store answered: the screen it was for is gone.
            card.answer = { ReviewFlow.Given { false } }

            prompts.askAt(ReviewOccasion.HOME)

            assertEquals(MiloSettings(), settings.current())
            assertTrue(lines().single().contains("so MilO did not have it shown"))
            // On the screen that was built in its place: asked.
            card.answer = { card.given() }
            prompts.askAt(ReviewOccasion.HOME)
            assertEquals(1, card.launches)
            assertEquals(MomentStanding.ASKED, stored().of(ReviewMoment.FIVE_TRIPS))
        }

    // ---- Storage that fails ---------------------------------------------------------------------

    @Test
    fun `with a settings file that cannot be read nothing is asked, and nothing is written`() =
        runTest {
            val prompts = promptsOn(file = UnreadableSettingsFile)
            trips.value = 150

            assertEquals(emptySet<ReviewMoment>(), prompts.look(ReviewOccasion.HOME))
            prompts.askAt(ReviewOccasion.HOME)
            assertEquals(emptySet<ReviewMoment>(), prompts.look(ReviewOccasion.REPORT_SENT))

            assertEquals(0, card.requests)
            assertTrue(log.entries.isEmpty())
        }

    @Test
    fun `a launch the settings file refuses to take is remembered all the same`() = runTest {
        val prompts = promptsOn(file = ReadOnlyFile(file))
        trips.value = 5

        prompts.askAt(ReviewOccasion.HOME)

        assertEquals(1, card.launches)
        assertEquals(
            listOf("MilO could not store what it asked Google Play's review card for"),
            lines(EventCategory.ERROR),
        )
        // Home is opened again and again, that day and the next: Google Play is not asked again.
        repeat(3) {
            assertEquals(emptySet<ReviewMoment>(), prompts.look(ReviewOccasion.HOME))
            prompts.askAt(ReviewOccasion.HOME)
        }
        nowMs = at(12, 9)
        assertEquals(emptySet<ReviewMoment>(), prompts.look(ReviewOccasion.HOME))
        assertEquals(1, card.requests)
    }

    @Test
    fun `a failed request the settings file refuses to take still holds the day`() = runTest {
        val prompts = promptsOn(file = ReadOnlyFile(file))
        trips.value = 5
        card.answer = { ReviewFlow.NotGiven("Google Play reported an error of its own") }

        prompts.askAt(ReviewOccasion.HOME)
        repeat(3) { prompts.askAt(ReviewOccasion.HOME) }

        assertEquals(1, card.requests)
        nowMs = at(12, 9)
        assertEquals(FIVE, prompts.look(ReviewOccasion.HOME))
    }

    @Test
    fun `a number of trips that cannot be read is one line in the Log, and nothing more`() =
        runTest {
            val broken = flow<Int> { throw IllegalStateException("the database is locked") }
            val prompts = promptsOn(trips = broken)

            assertEquals(emptySet<ReviewMoment>(), prompts.look(ReviewOccasion.HOME))
            prompts.askAt(ReviewOccasion.HOME)

            assertEquals(0, card.requests)
            assertEquals(
                List(2) { "MilO could not look at the moments of Google Play's review card" },
                lines(EventCategory.ERROR),
            )
            assertEquals(MiloSettings(), settings.current())
        }

    @Test
    fun `if the event log is what fails, the line goes to a crash file`() = runTest {
        val fullLog =
            object : EventLogDao by log {
                override suspend fun insert(entry: EventLogEntry): Long =
                    throw IllegalStateException("the log is full")
            }
        val prompts = promptsOn(eventLog = fullLog)
        trips.value = 5

        prompts.askAt(ReviewOccasion.HOME)

        // The launch counts all the same.
        assertEquals(1, card.launches)
        assertEquals(MomentStanding.ASKED, stored().of(ReviewMoment.FIVE_TRIPS))
        val crashFiles = CrashFileStore(folder.root)
        val record = crashFiles.read(crashFiles.pendingFiles().single())
        assertTrue(record.summary.contains("MilO asked Google Play to show its review card"))
    }

    // ---- What the screens are told --------------------------------------------------------------

    @Test
    fun `the report's moment is in memory from I sent it until it is dealt with`() = runTest {
        val prompts = promptsOn()
        assertEquals(false, prompts.reportJustSent.value)

        prompts.onReportSentByAnswer()
        assertEquals(true, prompts.reportJustSent.value)

        prompts.reportDealtWith()
        assertEquals(false, prompts.reportJustSent.value)
    }
}
