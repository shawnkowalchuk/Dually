package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.trip.TripEvent
import com.shawnkowalchuk.milo.core.trip.TripState
import com.shawnkowalchuk.milo.core.trip.TripStateMachine
import com.shawnkowalchuk.milo.core.trip.TripTransition
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.point.RawPoint
import com.shawnkowalchuk.milo.platform.address.TripUnderWay
import com.shawnkowalchuk.milo.platform.trip.TripLedger
import com.shawnkowalchuk.milo.platform.trip.TripRuleSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Records a trip on the iPhone (phase 4b): Start and End pressed in the app, and the GPS fixes
 * the app hands in while a trip is open. It does what Android's trip worker does for the same
 * three things (TripWorker in the app), with the same shared parts: the trip rules
 * ([TripStateMachine]) decide, and the trip ledger ([TripLedger]) writes what they decided into
 * the databases. So a trip is opened, measured, closed and sorted into Business or Personal by
 * the very code Android uses.
 *
 * Not here yet: the truck. The iPhone's automatic start comes in phase 4d (ADR-015); until then
 * the rules are told the truck is never connected, so every trip is one started by hand.
 *
 * One thing at a time: Start, End, a fix and a look all take the same lock, as Android's worker
 * takes its work from one queue.
 */
class IosTripRecorder internal constructor(
    private val ledger: TripLedger,
    private val eventLog: EventLogRepository,
    private val ruleSettings: TripRuleSettings,
    private val clock: () -> Long,
) {
    private val lock = Mutex()
    private var known: TripState? = null
    private val mutableUnderWay = MutableStateFlow<TripUnderWay?>(null)

    /**
     * The trip being recorded and where it started, for the address lookup, as Android's trip
     * controller publishes it: the lookup finds where an open trip started, and both ends of a
     * trip once it is over.
     */
    internal val underWay: StateFlow<TripUnderWay?> = mutableUnderWay.asStateFlow()

    /** Start pressed: opens a trip, unless one is open already. */
    suspend fun start(): RecordingNow? = lock.withLock {
        val atMs = clock()
        advance("Start pressed in the app", TripEvent.ManualStart(truckConnected = false, atMs))
        snapshot()
    }

    /** End pressed: closes the open trip, which is then measured and sorted. */
    suspend fun end(): RecordingNow? = lock.withLock {
        val atMs = clock()
        advance("End pressed in the app", TripEvent.ManualEnd(truckConnected = false, atMs))
        snapshot()
    }

    /**
     * A GPS fix from the phone. It is stored with the open trip and counted into its distance;
     * with no trip open it is dropped, as on Android.
     *
     * @param elapsedMs the time since the phone started, which only ever goes forward: the
     *   distance rules judge speeds by it, never by the clock the user can set.
     */
    suspend fun addFix(
        wallClockMs: Long,
        elapsedMs: Long,
        latitude: Double,
        longitude: Double,
        accuracyMetres: Float?,
        speedMetresPerSecond: Float?,
    ): RecordingNow? = lock.withLock {
        pickUp()
        checkIfDue()
        if (known?.trip != null && ledger.open != null) {
            val fix =
                RawPoint(
                    tripId = 0,
                    wallClockMs = wallClockMs,
                    elapsedRealtimeMs = elapsedMs,
                    latitude = latitude,
                    longitude = longitude,
                    accuracyMetres = accuracyMetres,
                    speedMetresPerSecond = speedMetresPerSecond,
                )
            val movement = ledger.addFix(fix)
            if (movement != null) advance("GPS", movement, quiet = true)
        }
        snapshot()
    }

    /**
     * The trip being recorded now, or null when none is. The first call after the app starts
     * picks up a trip that was open when it last stopped, as Android does after a restart.
     */
    suspend fun now(): RecordingNow? = lock.withLock {
        pickUp()
        checkIfDue()
        snapshot()
    }

    /** Reads the stored trip into the rules once, the first time anything is asked. */
    private suspend fun pickUp() {
        if (known != null) return
        val settingsNow = ruleSettings.read()
        val rules = ruleSettings.rules
        val atMs = clock()
        val stored = ledger.load(rules, settingsNow.drivenOffTripId)
        val transition =
            TripStateMachine.restore(
                storedTrip = stored?.trip,
                lastRecordedAtMs = stored?.lastRecordedAtMs,
                autoStartHeldOffSinceMs = settingsNow.autoStartHeldOffSinceMs,
                truckConnected = false,
                androidAutoConnected = false,
                atMs = atMs,
                rules = rules,
                parkedSinceMs = null,
            )
        val found = if (stored == null) "no trip open" else "trip ${ledger.open?.id} open"
        commit("The iPhone app picked up the stored state: $found", transition)
    }

    /**
     * Gives the rules the time when one of their deadlines has passed (the parked rule's, a
     * grace period's), as Android's timer does: for a trip started by hand, the truck is not
     * connected.
     */
    private suspend fun checkIfDue() {
        val state = known ?: return
        val due = TripStateMachine.nextCheckAtMs(state, ruleSettings.rules) ?: return
        val atMs = clock()
        if (atMs < due) return
        advance(
            "a deadline of the trip rules",
            TripEvent.TruckConnection(false, atMs),
            quiet = true,
        )
    }

    private suspend fun advance(source: String, event: TripEvent, quiet: Boolean = false) {
        pickUp()
        ruleSettings.read()
        val before = checkNotNull(known) { "The stored state was picked up a moment ago" }
        val transition = TripStateMachine.step(before, event, ruleSettings.rules)
        commit(source, transition, quiet)
    }

    private suspend fun commit(what: String, transition: TripTransition, quiet: Boolean = false) {
        if (!quiet || transition.effects.isNotEmpty()) {
            eventLog.add(clock(), EventCategory.TRIGGER, what)
        }
        for (effect in transition.effects) {
            val line = ledger.carryOut(effect, ruleSettings)
            eventLog.add(clock(), line.category, line.message)
        }
        known = transition.state
    }

    private fun snapshot(): RecordingNow? {
        mutableUnderWay.value =
            ledger.open?.let { open ->
                val start = open.progress.distance.firstAccepted
                TripUnderWay(open.id, start?.latitude, start?.longitude)
            }
        val open = ledger.open ?: return null
        val metres = open.progress.distance.metres * ruleSettings.distanceFactor
        return RecordingNow(
            tripId = open.id,
            startedAtMs = open.startedAtMs,
            metres = metres,
            fixCount = open.progress.fixCount,
        )
    }
}

/**
 * The trip being recorded: its id, when it started, how far it has gone so far in metres (with
 * the user's distance adjustment, as Android's Home shows it), and how many fixes it has.
 */
data class RecordingNow(
    val tripId: Long,
    val startedAtMs: Long,
    val metres: Double,
    val fixCount: Int,
)
