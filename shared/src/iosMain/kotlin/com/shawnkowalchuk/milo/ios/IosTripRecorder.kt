package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.trip.TripEffect
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
import kotlin.concurrent.Volatile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSUserDefaults

/**
 * Records a trip on the iPhone: Start and End pressed in the app (phase 4b), the truck connecting
 * and disconnecting as the user's Shortcuts automation reports it (phase 4d, ADR-015), and the
 * GPS fixes the app hands in. It does what Android's trip worker does for the same things
 * (TripWorker in the app), with the same shared parts: the trip rules ([TripStateMachine])
 * decide, and the trip ledger ([TripLedger]) writes what they decided into the databases. So a
 * trip is opened, measured, closed and sorted into Business or Personal by the very code Android
 * uses, and a truck that stands still is waited beside as on Android.
 *
 * **The truck, as the iPhone knows it.** No iPhone app can read a Bluetooth connection, so the
 * rules are told what the last automation said ([TruckBelief]): connecting is a new link, as
 * Android's Bluetooth connect, and disconnecting starts the grace period. An automation that did
 * not run leaves the belief wrong; the parked rule then closes a trip whose truck has gone, as it
 * does on Android for a Bluetooth reading that is stuck.
 *
 * One thing at a time: Start, End, the truck, a fix and a look all take the same lock, as
 * Android's worker takes its work from one queue.
 */
class IosTripRecorder internal constructor(
    private val ledger: TripLedger,
    private val eventLog: EventLogRepository,
    private val ruleSettings: TripRuleSettings,
    private val truck: TruckBelief,
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

    /**
     * Whether the GPS should run: while a trip is open, and while the rules wait beside a parked
     * truck for it to move. Set after each call.
     */
    @Volatile
    var wantsGps: Boolean = false
        private set

    /** Start pressed: opens a trip, unless one is open already. */
    suspend fun start(): RecordingNow? = lock.withLock {
        val atMs = clock()
        advance("Start pressed in the app", TripEvent.ManualStart(truck.connected, atMs))
        snapshot()
    }

    /**
     * End pressed: closes the open trip, which is then measured and sorted. With the truck
     * believed connected, automatic start is then held off until it connects again, as on
     * Android.
     */
    suspend fun end(): RecordingNow? = lock.withLock {
        val atMs = clock()
        advance("End pressed in the app", TripEvent.ManualEnd(truck.connected, atMs))
        snapshot()
    }

    /**
     * The user's Shortcuts automation says the truck connected (its Bluetooth or CarPlay): a new
     * link, as Android's Bluetooth connect. A trip starts unless one is open or the rules hold
     * automatic start off.
     */
    suspend fun truckConnected(): RecordingNow? = lock.withLock {
        // What was stored is picked up as it was, before the news: the trip is the connect's.
        pickUp()
        truck.connected = true
        advance(AUTOMATION_CONNECTED, TripEvent.TruckLinkConnected(clock()))
        snapshot()
    }

    /**
     * The user's Shortcuts automation says the truck disconnected. An open trip's grace period
     * starts: it closes unless the truck connects again first.
     */
    suspend fun truckDisconnected(): RecordingNow? = lock.withLock {
        pickUp()
        truck.connected = false
        advance(AUTOMATION_DISCONNECTED, TripEvent.TruckConnection(false, clock()))
        snapshot()
    }

    /**
     * A GPS fix from the phone. It is stored with the open trip and counted into its distance.
     * Beside a parked truck it is looked at first, for the truck driving off, which starts the
     * next trip at the parked place. Otherwise, with no trip open, it is dropped, as on Android.
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
        val movedAtMs = if (known?.waitingToMove == true) ledger.watchFix(fix) else null
        if (movedAtMs != null) {
            advance("GPS", TripEvent.Moved(movedAtMs), quiet = true)
            // The trip's first points, this fix among them, were stored when it was opened. It
            // last moved at the newest of them that counted, not at the first.
            ledger.lastMovementAtMs?.let { advance("GPS", TripEvent.Moved(it), quiet = true) }
        } else if (known?.trip != null && ledger.open != null) {
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

    /**
     * Reads what storage holds into the rules once, the first time anything is asked (ADR-002,
     * "State survives the process"): the open trip, the hold-off, and a wait beside the parked
     * truck, which then watches from the stored place again.
     *
     * The truck is what the last automation said, but only to carry on what was stored. It
     * never starts a trip here: a disconnect the automation missed (the phone was off) leaves it
     * saying "connected", and a trip would then start each time the app is opened. A trip
     * starts on the automation's next connect instead.
     */
    private suspend fun pickUp() {
        if (known != null) return
        val settingsNow = ruleSettings.read()
        val rules = ruleSettings.rules
        val atMs = clock()
        val stored = ledger.load(rules, settingsNow.drivenOffTripId)
        val parkedSinceMs = settingsNow.parkedTruck?.sinceMs
        val heldOffSinceMs = settingsNow.autoStartHeldOffSinceMs
        val somethingStored = stored != null || parkedSinceMs != null || heldOffSinceMs != null
        val connected = truck.connected && somethingStored
        val transition =
            TripStateMachine.restore(
                storedTrip = stored?.trip,
                lastRecordedAtMs = stored?.lastRecordedAtMs,
                autoStartHeldOffSinceMs = heldOffSinceMs,
                truckConnected = connected,
                androidAutoConnected = false,
                atMs = atMs,
                rules = rules,
                parkedSinceMs = parkedSinceMs,
            )
        val found = if (stored == null) "no trip open" else "trip ${ledger.open?.id} open"
        val belief =
            when {
                connected -> "connected"
                truck.connected -> "connected, which starts nothing by itself"
                else -> "not connected"
            }
        commit(
            "The iPhone app picked up the stored state: $found; the last automation said the " +
                "truck was $belief",
            transition,
        )
        // A wait that carries on watches from the place stored with it. One that began in this
        // very step (a stored trip had stood too long) has its place already.
        val carriesOn = transition.effects.none { it is TripEffect.StartWaiting }
        if (carriesOn && transition.state.waitingToMove) {
            ledger.resumeWaiting(settingsNow.parkedTruck)
        }
    }

    /**
     * Gives the rules the time when one of their deadlines has passed (the parked rule's, a
     * grace period's, the wait's), as Android's timer does, with the truck as the last
     * automation said.
     */
    private suspend fun checkIfDue() {
        val state = known ?: return
        val due = TripStateMachine.nextCheckAtMs(state, ruleSettings.rules) ?: return
        val atMs = clock()
        if (atMs < due) return
        advance(
            "a deadline of the trip rules",
            TripEvent.TruckConnection(truck.connected, atMs),
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
        wantsGps = ledger.open != null || known?.waitingToMove == true
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
 * What the last Shortcuts automation said about the truck (ADR-015): the iPhone cannot read a
 * Bluetooth connection itself. Kept across launches, because the automation runs only when the
 * connection changes.
 */
internal interface TruckBelief {
    var connected: Boolean
}

/** [TruckBelief] in the app's own defaults (NSUserDefaults). */
internal class StoredTruckBelief : TruckBelief {
    private val defaults = NSUserDefaults.standardUserDefaults

    override var connected: Boolean
        get() = defaults.boolForKey(KEY)
        set(value) = defaults.setBool(value, KEY)

    private companion object {
        const val KEY = "truck_connected_by_automation"
    }
}

private const val AUTOMATION_CONNECTED = "The Shortcuts automation said the truck connected"
private const val AUTOMATION_DISCONNECTED = "The Shortcuts automation said the truck disconnected"

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
