package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.trip.TripEffect
import com.shawnkowalchuk.milo.core.trip.TripEvent
import com.shawnkowalchuk.milo.core.trip.TripState
import com.shawnkowalchuk.milo.core.trip.TripStateMachine
import com.shawnkowalchuk.milo.core.trip.TripTransition
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.point.RawPoint
import com.shawnkowalchuk.milo.data.settings.TripSound
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

    /**
     * The trip whose drive-off has been told, so that its trip-start sound plays once (2026-11-16,
     * as Android's `TripWorker.noticeDrivingOff`).
     */
    private var drivingOffToldFor: Long? = null

    /** The sounds due since Swift last asked ([takeSounds]), in the order they are to play. */
    private val soundsDue = mutableListOf<TripSound>()

    /** Whether [soundsDue] holds the trip's first sound (see [TripSounds.tripStarts]). */
    private var tripStartsDue = false
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
    suspend fun start(): RecordingNow? = startByHand("Start pressed in the app")

    /** A tap on the driving alert (phase 4d, third part): a trip started by hand, as on Android. */
    suspend fun startFromAlert(): RecordingNow? = startByHand("tap on the driving alert")

    /** Start on the home-screen widget (phase 4i): a trip started by hand, as Android's widget. */
    suspend fun startFromWidget(): RecordingNow? = startByHand("Start on the home-screen widget")

    /** Start trip on the CarPlay screen (phase 4f), as Android Auto's Start button. */
    suspend fun startFromCar(): RecordingNow? = startByHand("Start trip on the CarPlay screen")

    private suspend fun startByHand(source: String): RecordingNow? = lock.withLock {
        val atMs = clock()
        advance(source, TripEvent.ManualStart(truck.connected, atMs))
        snapshot()
    }

    /**
     * What the driving alert reads of the recording, taken under the same lock as everything
     * else: whether a trip is open, what the last automation said about the truck, and whether
     * the rules are watching a parked truck for its drive-off.
     */
    internal suspend fun drivingFacts(): DrivingFacts = lock.withLock {
        pickUp()
        checkIfDue()
        snapshot()
        val parked = known?.parked
        DrivingFacts(
            tripOpen = ledger.open != null,
            truckBelievedConnected = truck.connected,
            watchingParkedTruck = known?.waitingToMove == true,
            parkedNoLongerWatched = parked != null && !parked.watching && known?.trip == null,
        )
    }

    /**
     * Home's truck tile on the iPhone (2026-11-15), in the words of Android's (`TruckState`):
     * what the last Shortcuts automation said, since the iPhone cannot read the connection, and
     * what the rules do about it. Null when no automation has ever run: Setup's warning stands
     * on Home in its place.
     */
    suspend fun truckTile(): TruckTileText? = lock.withLock {
        pickUp()
        checkIfDue()
        val heardAtMs =
            (if (truck.connected) truck.connectHeardAtMs else truck.disconnectHeardAtMs)
                ?: return@withLock null
        val state = known
        val parked = state?.parked
        when {
            !truck.connected && state?.trip != null ->
                TruckTileText(
                    false,
                    "Not connected",
                    "Truck disconnected. Waiting for it to reconnect",
                    heardAtMs,
                )

            !truck.connected ->
                TruckTileText(
                    connected = false,
                    title = "Not connected",
                    text = "A trip starts by itself when the truck connects",
                    heardAtMs = heardAtMs,
                )

            state?.trip != null ->
                TruckTileText(true, "Connected", "Trip recording started", heardAtMs)

            state?.waitingToMove == true ->
                TruckTileText(
                    true,
                    "Truck connected and parked",
                    "A trip starts by itself when the truck moves",
                    heardAtMs,
                )

            parked != null && !parked.watching ->
                TruckTileText(
                    true,
                    "Truck connected. Dually has stopped watching it",
                    "It has not moved for a long time. Press Start trip before you drive off.",
                    heardAtMs,
                )

            state?.autoStartHeldOff == true ->
                TruckTileText(
                    true,
                    "Connected",
                    "You ended the trip. The next one starts by itself after the truck reconnects.",
                    heardAtMs,
                )

            else ->
                TruckTileText(
                    true,
                    "Connected",
                    "No trip is being recorded. Press Start trip.",
                    heardAtMs,
                )
        }
    }

    /**
     * End pressed: closes the open trip, which is then measured and sorted. With the truck
     * believed connected, automatic start is then held off until it connects again, as on
     * Android.
     */
    suspend fun end(): RecordingNow? = endByHand("End pressed in the app")

    /**
     * End trip on the trip's Live Activity (phase 4e): the same as End in the app, told apart in
     * the event log, as Android tells its phone's and its car's buttons apart.
     */
    suspend fun endFromLiveActivity(): RecordingNow? = endByHand("End trip on the Live Activity")

    /** End trip on the CarPlay screen (phase 4f), as Android Auto's End button. */
    suspend fun endFromCar(): RecordingNow? = endByHand("End trip on the CarPlay screen")

    private suspend fun endByHand(source: String): RecordingNow? = lock.withLock {
        val atMs = clock()
        advance(source, TripEvent.ManualEnd(truck.connected, atMs))
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
        truck.connectedByReading = false
        truck.connectHeardAtMs = clock()
        advance(AUTOMATION_CONNECTED, TripEvent.TruckLinkConnected(clock()))
        snapshot()
    }

    /**
     * A wake for a drive found the truck connected (2026-11-16, ADR-015's second layer): the
     * iPhone's audio goes to CarPlay, or to the truck's Bluetooth by the name noted when the
     * automation last ran, while Core Motion says the iPhone is driving and no trip is open. It
     * is Android's reading of the truck at a process start, `TruckConnection(true)`: a trip
     * starts unless the rules hold automatic start off; a wait beside the parked truck carries
     * on as the rules have it. The connect the automation missed is recovered, a few hundred
     * metres into the drive, where iOS woke Dually.
     */
    suspend fun truckReadConnected(): RecordingNow? = lock.withLock {
        pickUp()
        truck.connected = true
        truck.connectedByReading = true
        advance(WAKE_READ_CONNECTED, TripEvent.TruckConnection(true, clock()))
        snapshot()
    }

    /**
     * Whether the truck is believed connected because a wake read it in the audio route, not
     * because the automation said so. While it is, Swift reads the route again with every fix,
     * as Android reads the truck through a trip, and its going ends the belief
     * ([truckReadGone]): the disconnect automation, which is likely the one that failed, is
     * not waited for.
     */
    val connectedByReading: Boolean get() = truck.connected && truck.connectedByReading

    /**
     * The audio route no longer goes to a truck that a wake read as connected: Android's reading
     * of a disconnect, `TruckConnection(false)`. An open trip's grace period starts; a wait
     * beside the parked truck ends.
     */
    suspend fun truckReadGone(): RecordingNow? = lock.withLock {
        pickUp()
        if (!(truck.connected && truck.connectedByReading)) return@withLock snapshot()
        truck.connected = false
        truck.connectedByReading = false
        advance(READ_GONE, TripEvent.TruckConnection(false, clock()))
        snapshot()
    }

    /**
     * Notes in the Log where the iPhone's audio went when the automation said the truck
     * connected: the name a wake will look for, or none, when a wake cannot find this truck by
     * its audio (a hands-free-only car kit, or more than one Bluetooth output).
     */
    suspend fun noteTruckRoute(name: String?) {
        val line =
            if (name == null) {
                "At the automation's connect the iPhone's audio went to no single Bluetooth " +
                    "device: a wake for a drive cannot find this truck by its audio"
            } else {
                "At the automation's connect the iPhone's audio went to \"$name\": a wake for a " +
                    "drive that finds it there finds the truck"
            }
        eventLog.add(clock(), EventCategory.TRIGGER, line)
    }

    /**
     * The user's Shortcuts automation says the truck disconnected. An open trip's grace period
     * starts: it closes unless the truck connects again first.
     */
    suspend fun truckDisconnected(): RecordingNow? = lock.withLock {
        pickUp()
        truck.connected = false
        truck.connectedByReading = false
        truck.disconnectHeardAtMs = clock()
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
     * Whether the trip rules wait beside the parked truck for it to move, as Android's
     * "Truck parked. A trip starts when it moves" on the car screen says (CarPlay, 2026-11-14).
     */
    suspend fun parkedWaiting(): Boolean = lock.withLock {
        pickUp()
        known?.waitingToMove == true
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
        fun restoreWith(truckConnected: Boolean) = TripStateMachine.restore(
            storedTrip = stored?.trip,
            lastRecordedAtMs = stored?.lastRecordedAtMs,
            autoStartHeldOffSinceMs = heldOffSinceMs,
            truckConnected = truckConnected,
            androidAutoConnected = false,
            atMs = atMs,
            rules = rules,
            parkedSinceMs = parkedSinceMs,
        )
        // The belief is what the last automation said, not a reading of the truck: it carries
        // on what was stored, but never starts a trip here. Where the rules would start one (a
        // stored trip or wait that had run out, closed, then the truck "connected"), they are
        // given the truck as not connected instead (2026-11-16, after the independent check:
        // every wake for a move now picks up).
        val transition =
            restoreWith(connected).let { first ->
                if (connected && first.effects.any { it is TripEffect.StartTrip }) {
                    restoreWith(false)
                } else {
                    first
                }
            }
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
        // A trip picked up that had driven off already plays no second trip-start sound.
        ledger.open?.takeIf { it.progress.drivenAtMs != null }?.let { drivingOffToldFor = it.id }
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
        // The connect sound, once per trip, when it has really begun: not at a pick-up after a
        // restart, and not for a trip a parked truck's moving started (that one has the
        // trip-start sound at once), as on Android.
        if (transition.tripReallyBegan(before = before)) {
            soundsDue += TripSound.CONNECT
            tripStartsDue = true
        }
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

    /**
     * The sounds due since the last time, cleared as they are handed over: the connect sound
     * when a trip has really begun, then the trip-start sound the first time the trip is seen
     * driving (Android's moments, `TripRecorder.record`). Swift plays them by the settings
     * (`IosSounds.toPlay`). Null when none is due.
     */
    suspend fun takeSounds(): TripSounds? = lock.withLock {
        if (soundsDue.isEmpty()) return@withLock null
        val due = TripSounds(soundsDue.map { it.name }, tripStartsDue)
        soundsDue.clear()
        tripStartsDue = false
        due
    }

    /**
     * The open trip has been seen driving for the first time (`TripProgress.drivenAtMs`): the
     * moment for the trip-start sound, once per trip. A trip a parked truck's moving started
     * shows it from its first points, and its sound is then the trip's first.
     */
    private fun noticeDrivingOff() {
        val open = ledger.open ?: return
        val unconfirmed = known?.trip?.confirmByMs != null
        if (open.id == drivingOffToldFor || unconfirmed || open.progress.drivenAtMs == null) return
        drivingOffToldFor = open.id
        soundsDue += TripSound.DRIVING_OFF
        if (open.fromParked) tripStartsDue = true
    }

    private fun snapshot(): RecordingNow? {
        noticeDrivingOff()
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
            waitingForTruck = known?.trip?.grace != null,
            truckSeen = known?.trip?.truckSeen == true,
        )
    }
}

/**
 * Home's truck tile (see [IosTripRecorder.truckTile]): Android's title and sentence for the state,
 * and when the automation said so.
 */
data class TruckTileText(
    val connected: Boolean,
    val title: String,
    val text: String,
    val heardAtMs: Long,
    /** "Your Shortcuts automation said so today at 3:45 PM.", filled in by [DuallyCore.truckTile]. */
    val heard: String = "",
)

/**
 * Sounds due at one moment, in their order: "CONNECT" and "DRIVING_OFF" ([TripSound]).
 * [tripStarts] is whether they hold the trip's first sound, which on the day of the one-year
 * party gives way to its pop.
 */
data class TripSounds(val sounds: List<String>, val tripStarts: Boolean)

/** See [IosTripRecorder.drivingFacts]. */
internal data class DrivingFacts(
    val tripOpen: Boolean,
    val truckBelievedConnected: Boolean,
    val watchingParkedTruck: Boolean,
    val parkedNoLongerWatched: Boolean,
)

/**
 * What the last Shortcuts automation said about the truck (ADR-015): the iPhone cannot read a
 * Bluetooth connection itself. Kept across launches, because the automation runs only when the
 * connection changes.
 */
internal interface TruckBelief {
    var connected: Boolean

    /** Whether [connected] came from a wake's reading of the audio route (see IosTripRecorder). */
    var connectedByReading: Boolean

    /** When the automation last ran Start a trip, or null if it never has: Setup's check. */
    var connectHeardAtMs: Long?

    /** When the automation last ran End a trip, or null if it never has. */
    var disconnectHeardAtMs: Long?
}

/** [TruckBelief] in the app's own defaults (NSUserDefaults). */
internal class StoredTruckBelief : TruckBelief {
    private val defaults = NSUserDefaults.standardUserDefaults

    override var connected: Boolean
        get() = defaults.boolForKey(KEY)
        set(value) = defaults.setBool(value, KEY)

    override var connectedByReading: Boolean
        get() = defaults.boolForKey(BY_READING_KEY)
        set(value) = defaults.setBool(value, BY_READING_KEY)

    override var connectHeardAtMs: Long?
        get() = timeAt(CONNECT_HEARD_KEY)
        set(value) = defaults.setDouble(value?.toDouble() ?: 0.0, CONNECT_HEARD_KEY)

    override var disconnectHeardAtMs: Long?
        get() = timeAt(DISCONNECT_HEARD_KEY)
        set(value) = defaults.setDouble(value?.toDouble() ?: 0.0, DISCONNECT_HEARD_KEY)

    /** A stored time; 0, what the defaults give for a key never set, is none. */
    private fun timeAt(key: String): Long? = defaults.doubleForKey(key).toLong().takeIf { it > 0 }

    private companion object {
        const val KEY = "truck_connected_by_automation"
        const val BY_READING_KEY = "truck_connected_by_reading"
        const val CONNECT_HEARD_KEY = "automation_connect_heard_at_ms"
        const val DISCONNECT_HEARD_KEY = "automation_disconnect_heard_at_ms"
    }
}

private const val AUTOMATION_CONNECTED = "The Shortcuts automation said the truck connected"

/** The Log's line for a wake that read the truck as connected. */
private const val READ_GONE =
    "The iPhone's audio no longer goes to the truck a wake found connected: it is taken as " +
        "disconnected"

private val WAKE_READ_CONNECTED =
    "A wake for a drive found the truck connected (the iPhone's audio goes to it); the " +
        "automation's connect was missed"
private const val AUTOMATION_DISCONNECTED = "The Shortcuts automation said the truck disconnected"

/**
 * The trip being recorded: its id, when it started, how far it has gone so far in metres (with
 * the user's distance adjustment, as Android's Home shows it), and how many fixes it has.
 * [waitingForTruck] is true while the grace period runs: the truck has gone and the trip ends
 * unless it comes back, which Android's trip notification says ("Waiting for the truck to
 * reconnect") and so does the iPhone's Live Activity (phase 4e). [truckSeen] is whether the truck
 * has been seen in it: until then it moves no odometer, as on Android (`TripSoFar`; Home's
 * odometer, 2026-11-14).
 */
data class RecordingNow(
    val tripId: Long,
    val startedAtMs: Long,
    val metres: Double,
    val fixCount: Int,
    val waitingForTruck: Boolean,
    val truckSeen: Boolean,
)
