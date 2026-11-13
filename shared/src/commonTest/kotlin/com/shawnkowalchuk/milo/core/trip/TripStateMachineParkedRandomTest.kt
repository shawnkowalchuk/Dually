package com.shawnkowalchuk.milo.core.trip

import com.shawnkowalchuk.milo.core.trip.TripEffect.CancelGrace
import com.shawnkowalchuk.milo.core.trip.TripEffect.EndTrip
import com.shawnkowalchuk.milo.core.trip.TripEffect.EndWaiting
import com.shawnkowalchuk.milo.core.trip.TripEffect.HoldOffAutoStart
import com.shawnkowalchuk.milo.core.trip.TripEffect.MarkTruckSeen
import com.shawnkowalchuk.milo.core.trip.TripEffect.ReleaseHoldOff
import com.shawnkowalchuk.milo.core.trip.TripEffect.StartGrace
import com.shawnkowalchuk.milo.core.trip.TripEffect.StartTrip
import com.shawnkowalchuk.milo.core.trip.TripEffect.StartWaiting
import com.shawnkowalchuk.milo.core.trip.TripEvent.TruckConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val RANDOM_SEQUENCES = 3_000
private const val EVENTS_PER_SEQUENCE = 80

/**
 * [TripStateMachineRandomTest] again, with the parked rule on, which is how the app always runs:
 * thousands of random sequences of every kind of event, and after each event the state must
 * still make sense, with a truck that may now also be parked and waited for. Everything the
 * other test checks is checked here too (the stored columns of the trip, the grace period, a
 * restart at that very moment), and one thing more: the same event a second time does nothing.
 * The wait is given three hours here, so that the random steps (up to two hours) reach its
 * limit. The seed is fixed, so a failure can be replayed.
 */
class TripStateMachineParkedRandomTest {
    private val rules =
        TripRules(gracePeriodMs = GRACE, parkedLimitMs = PARKED_LIMIT, waitingLimitMs = 3 * HOUR)

    @Test
    fun `no sequence of events can leave the state inconsistent with the parked rule on`() {
        val random = SeededRandom(20261006L)
        var waits = 0
        var tripsFromParked = 0
        repeat(RANDOM_SEQUENCES) {
            var state = IDLE
            val stored = StoredSide()
            var now = T0
            repeat(EVENTS_PER_SEQUENCE) {
                now += randomTimeStep(random)
                val events = everyEventAt(now) + TripEvent.MoveTakenBack(now - HOUR, now)
                val event = events[random.nextInt(events.size)]
                val result = TripStateMachine.step(state, event, rules)
                val context = "after $event from $state"

                stored.carryOut(result.effects)
                assertConsistent(result.state, now, context)
                assertEquals(
                    stored.heldOffSinceMs,
                    result.state.autoStartHeldOffSinceMs,
                    "stored hold-off $context",
                )
                assertEquals(stored.trip, result.state.trip?.stored(), "stored trip $context")
                assertEquals(
                    stored.waitingSinceMs,
                    result.state.parked?.takeIf { it.watching }?.sinceMs,
                    "stored wait $context",
                )
                assertRestartAgrees(result.state, event, now, context)
                assertTwiceIsOnce(result.state, event, context)

                waits += result.effects.count { it is StartWaiting }
                tripsFromParked += result.effects.count { it is StartTrip && it.fromParked }
                state = result.state
            }
        }
        // The sequences really did park trucks and drive them off again.
        assertTrue(waits > 1_000, "only $waits waits began")
        assertTrue(tripsFromParked > 100, "only $tripsFromParked trips started from a parked truck")
    }

    /**
     * Being killed and restored at this very moment must do what a fresh look at the truck
     * does, and if the event itself had just read the truck, nothing is left to be done.
     *
     * One state differs on purpose, the one after MilO has stopped watching a parked truck:
     * "no longer watched" lives in memory only, so for a new process it is a restart with the
     * truck connected, which starts a trip (ADR-002, amendment 28, "Three days").
     */
    private fun assertRestartAgrees(
        state: TripState,
        event: TripEvent,
        now: Long,
        context: String,
    ) {
        // Storage holds a wait only while it is watched.
        val parked = state.parked?.takeIf { it.watching }
        if (parked != null && now - parked.sinceMs >= rules.waitingLimitMs) return
        val look = TripStateMachine.step(state, TruckConnection(state.truckConnected, now), rules)
        val restored =
            TripStateMachine.restore(
                storedTrip = state.trip,
                lastRecordedAtMs = now,
                autoStartHeldOffSinceMs = state.autoStartHeldOffSinceMs,
                truckConnected = state.truckConnected,
                androidAutoConnected = state.androidAutoConnected,
                atMs = now,
                rules = rules,
                parkedSinceMs = parked?.sinceMs,
            )
        assertEquals(look.state, restored.state, "restore $context")
        assertEquals(look.effects, restored.effects, "restore $context")

        if (event.readsTheTruck() && state.parked?.watching != false) {
            assertEquals(state, restored.state, "nothing left to do $context")
            assertEquals(emptyList<TripEffect>(), restored.effects, "nothing left to do $context")
        }
    }

    /**
     * The same event again changes nothing: the Bluetooth broadcasts arrive twice during a trip
     * and during a wait. The one state left out is again "no longer watched": the reading that
     * stops the watch starts nothing, and the reading after it is a reconcile like any other.
     */
    private fun assertTwiceIsOnce(state: TripState, event: TripEvent, context: String) {
        if (state.parked?.watching == false) return
        val again = TripStateMachine.step(state, event, rules)
        assertEquals(state, again.state, "the same event twice $context")
        assertEquals(emptyList<TripEffect>(), again.effects, "the same event twice $context")
    }

    /** What must hold for every state the rules ever produce. [now] is the last event's time. */
    private fun assertConsistent(state: TripState, now: Long, context: String) {
        val parked = state.parked
        val trip = state.trip
        assertFalse(trip != null && parked != null, "a trip open beside a wait $context")
        if (parked != null) {
            // A wait with nothing connected could never end, and Android Auto counts only
            // while MilO is watching; a wait while held off would start a trip on movement
            // that the hold-off forbids.
            val held = state.truckConnected || (parked.watching && state.androidAutoConnected)
            assertTrue(held, "parked with nothing connected $context")
            assertFalse(state.autoStartHeldOff, "parked while held off $context")
        }
        if (trip == null) {
            // The "missed trip" check: idle beside a connected truck needs a reason.
            val reason = state.autoStartHeldOff || parked != null
            assertTrue(!state.truckConnected || reason, "idle with the truck connected $context")
        } else {
            assertOpenTripConsistent(state, trip, now, context)
        }
        assertTrue(
            !state.autoStartHeldOff || state.truckConnected,
            "held off with the truck gone $context",
        )
    }

    /** The checks of [TripStateMachineRandomTest] on an open trip, unchanged by the parked rule. */
    private fun assertOpenTripConsistent(
        state: TripState,
        trip: ActiveTrip,
        now: Long,
        context: String,
    ) {
        val heldOpen = state.truckConnected || state.androidAutoConnected
        assertTrue(
            trip.confirmByMs == null || !trip.truckSeen,
            "waiting to be confirmed, yet the truck was seen $context",
        )
        // The one exception: a grace period that ran out long ago. That trip is over and
        // only waits for a reading of the truck to be closed, whatever has connected since.
        val grace = trip.grace
        val overAndWaiting = grace != null && now > grace.deadlineMs + rules.lateCheckToleranceMs
        if (!overAndWaiting) {
            assertEquals(trip.truckSeen && !heldOpen, grace != null, "grace running $context")
        }
        assertTrue(
            trip.truckSeen || !state.truckConnected,
            "truck connected but not marked seen $context",
        )
    }

    /**
     * Stands in for storage: it carries out the effects and nothing else, and refuses those
     * that make no sense, such as a second trip, a second wait, or a trip opened while the
     * wait is still stored. If it agrees with the rules after every step, the effects really
     * do describe every stored change.
     */
    private class StoredSide {
        var trip: ActiveTrip? = null
        var heldOffSinceMs: Long? = null
        var waitingSinceMs: Long? = null

        fun carryOut(effects: List<TripEffect>) {
            for (effect in effects) {
                when (effect) {
                    is StartTrip -> {
                        assertNull(trip, "a second trip was opened")
                        assertNull(waitingSinceMs, "a trip was opened beside a stored wait")
                        trip = ActiveTrip(effect.startedBy, effect.truckSeen, lastMovementAtMs = 0)
                    }

                    MarkTruckSeen -> {
                        assertEquals(false, openTrip().truckSeen, "truck marked seen twice")
                        trip = openTrip().copy(truckSeen = true)
                    }

                    is StartGrace -> {
                        assertNull(openTrip().grace, "grace started twice")
                        trip = openTrip().copy(grace = Grace(effect.startedAtMs, effect.deadlineMs))
                    }

                    CancelGrace -> {
                        assertNotNull(openTrip().grace, "grace cancelled but not running")
                        trip = openTrip().copy(grace = null)
                    }

                    is EndTrip -> {
                        openTrip()
                        trip = null
                    }

                    is StartWaiting -> {
                        assertNull(trip, "a wait began beside an open trip")
                        assertNull(heldOffSinceMs, "a wait began while held off")
                        assertNull(waitingSinceMs, "a second wait began")
                        waitingSinceMs = effect.sinceMs
                    }

                    is EndWaiting -> {
                        assertNotNull(waitingSinceMs, "a wait ended, but none is stored")
                        waitingSinceMs = null
                    }

                    is HoldOffAutoStart -> {
                        assertNull(waitingSinceMs, "held off beside a stored wait")
                        assertNotEquals(
                            heldOffSinceMs,
                            effect.sinceMs,
                            "held off again at the same time",
                        )
                        heldOffSinceMs = effect.sinceMs
                    }

                    is ReleaseHoldOff -> {
                        assertNotNull(heldOffSinceMs, "hold-off released but not set")
                        heldOffSinceMs = null
                    }
                }
            }
        }

        private fun openTrip(): ActiveTrip = checkNotNull(trip) { "an effect needed an open trip" }
    }

    /**
     * The stored columns of a trip: everything but the movement time and the confirmation
     * deadline, which are not stored.
     */
    private fun ActiveTrip.stored(): ActiveTrip = copy(lastMovementAtMs = 0, confirmByMs = null)

    /** Mostly seconds, sometimes longer than every timeout, now and then a clock set back. */
    private fun randomTimeStep(random: SeededRandom): Long = when (random.nextInt(10)) {
        0 -> -random.nextInt(5 * MINUTE.toInt()).toLong()
        1, 2 -> random.nextInt(2 * HOUR.toInt()).toLong()
        else -> random.nextInt(MINUTE.toInt()).toLong()
    }
}
