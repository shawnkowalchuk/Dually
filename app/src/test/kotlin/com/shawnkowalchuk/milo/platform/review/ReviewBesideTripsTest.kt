package com.shawnkowalchuk.milo.platform.review

import com.shawnkowalchuk.milo.core.review.MomentStanding
import com.shawnkowalchuk.milo.core.review.ReviewMoment
import com.shawnkowalchuk.milo.core.review.ReviewOccasion
import com.shawnkowalchuk.milo.core.review.mayBeDriving
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.trip.driveNorth
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.isRecordedTrip
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeWorld
import com.shawnkowalchuk.milo.platform.trip.StartRequest
import com.shawnkowalchuk.milo.platform.trip.TripController
import com.shawnkowalchuk.milo.platform.trip.TripTrigger
import com.shawnkowalchuk.milo.platform.trip.asFix
import com.shawnkowalchuk.milo.platform.trip.process
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** What a run of trips left behind, to hold one run against another. */
private data class Recorded(
    val trips: List<Trip>,
    val points: Int,
    val startRequests: List<StartRequest>,
    val stops: Int,
    val log: List<String>,
)

/**
 * The review card beside the real trip controller ([FakeWorld]): six trips are recorded, once
 * with nothing watching and once with [ReviewPrompts] looking at every step as Home would have
 * it look. **What is recorded is the same, row for row and line for line,** whether the copy
 * came from Google Play and asks, or did not and asks nothing; and nothing is asked of Google
 * Play while a trip is being recorded.
 */
// runCurrent() is how a test lets the controller's worker run; the API is marked experimental.
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewBesideTripsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val card = StandInCard()
    private val reviewLog = FakeEventLogDao()

    /** How often Home looked during a trip, and what it was told to ask for then. */
    private var looksDuringTrips = 0
    private val dueDuringTrips = mutableListOf<Set<ReviewMoment>>()

    private fun promptsBeside(
        world: FakeWorld,
        controller: TripController,
        fromGooglePlay: Boolean,
    ) = ReviewPrompts(
        settings = world.settings,
        recordedTrips = flow { emit(world.trips.rows.count { it.isRecordedTrip }) },
        mayBeDriving =
            controller.activity.map { now ->
                mayBeDriving(now.trip != null, now.truckConnected, now.parked != null)
            },
        fromGooglePlay = { fromGooglePlay },
        // A log of its own: the trips' log is held against the run without it, line for line.
        eventLog = EventLogRepository(reviewLog),
        crashFileStore = CrashFileStore(folder.root),
        clock = { world.nowMs },
        zone = { world.zone },
    )

    /**
     * Six trips started with the button and driven a kilometre each, ten minutes apart. With
     * [fromGooglePlay] set, the review card looks on as Home would: in the middle of each trip
     * and after each one has ended.
     */
    private suspend fun TestScope.sixTrips(fromGooglePlay: Boolean?): Recorded {
        val world = FakeWorld()
        val (controller, service) = process(world)
        service.comesUpAtOnce = true
        val prompts = fromGooglePlay?.let { promptsBeside(world, controller, it) }

        repeat(6) {
            controller.onTrigger(TripTrigger.MANUAL_START, "Start button")
            runCurrent()
            val fixes = driveNorth(11, 100.0, fromNorthMetres = it * 2_000.0, fromSecond = it * 600)
            fixes.forEach { fix -> controller.onFix(fix.asFix()) }
            world.nowMs = fixes.last().wallClockMs
            runCurrent()
            if (prompts != null) {
                val requestsBefore = card.requests
                looksDuringTrips++
                dueDuringTrips += prompts.look(ReviewOccasion.HOME)
                prompts.ask(ReviewOccasion.HOME, card) { true }
                assertEquals("asked during trip ${it + 1}", requestsBefore, card.requests)
            }
            controller.onTrigger(TripTrigger.MANUAL_END, "End button")
            runCurrent()
            if (prompts != null) {
                prompts.look(ReviewOccasion.HOME)
                prompts.ask(ReviewOccasion.HOME, card) { true }
            }
        }
        return Recorded(
            trips = world.trips.rows.toList(),
            points = world.points.rows.size,
            startRequests = service.startRequests.toList(),
            stops = service.stops,
            log = world.log.entries.map { "${it.atMs} ${it.category} ${it.message}" },
        )
    }

    @Test
    fun `trips are recorded the same with a review card that asks as with none`() = runTest {
        val alone = sixTrips(fromGooglePlay = null)

        val beside = sixTrips(fromGooglePlay = true)

        assertEquals(alone, beside)
        assertEquals(6, beside.trips.count { it.status == TripStatus.FINISHED })
        // And the card did what it is for: nothing through four trips, once after the fifth.
        assertEquals(6, looksDuringTrips)
        assertTrue(dueDuringTrips.all { it.isEmpty() })
        assertEquals(1, card.requests)
        assertEquals(1, card.launches)
        assertEquals(1, reviewLog.entries.size)
        assertTrue(reviewLog.entries.single().message.startsWith("Review card: Dually holds 5"))
    }

    @Test
    fun `and the same again in a copy Google Play did not install, which asks nothing`() = runTest {
        val alone = sixTrips(fromGooglePlay = null)

        val beside = sixTrips(fromGooglePlay = false)

        assertEquals(alone, beside)
        assertEquals(0, card.requests)
        // One line, once, after the fifth trip; none after the sixth.
        assertEquals(1, reviewLog.entries.size)
        assertTrue(reviewLog.entries.single().message.endsWith("This is written once"))
    }

    @Test
    fun `what the review card stores is its own, and no setting the trips go by`() = runTest {
        val world = FakeWorld()
        val (controller, service) = process(world)
        service.comesUpAtOnce = true
        val before = world.settings.current()
        world.trips.rows +=
            List(5) {
                Trip(
                    id = it + 1L,
                    startedAtMs = world.nowMs - (it + 1) * 3_600_000L,
                    endedAtMs = world.nowMs - (it + 1) * 3_600_000L + 600_000L,
                    status = TripStatus.FINISHED,
                    startedBy = TripStartCause.TRUCK,
                    truckSeen = true,
                )
            }
        controller.onTrigger(TripTrigger.RECONCILE, "app opened")
        runCurrent()

        val prompts = promptsBeside(world, controller, fromGooglePlay = true)
        prompts.ask(ReviewOccasion.HOME, card) { true }

        assertEquals(1, card.launches)
        val after = world.settings.current()
        assertEquals(MomentStanding.ASKED, after.reviews.of(ReviewMoment.FIVE_TRIPS))
        // Everything else in the settings file is as it was.
        assertEquals(before, after.copy(reviews = before.reviews))
        assertEquals(5, world.trips.rows.size)
        assertEquals(emptyList<StartRequest>(), service.startRequests)
    }
}
