package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.time.ZoneId
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.bluetooth.TruckReading
import com.shawnkowalchuk.milo.platform.driving.DrivingAlertStep
import com.shawnkowalchuk.milo.platform.driving.DrivingMoment
import com.shawnkowalchuk.milo.platform.driving.VehicleReport
import com.shawnkowalchuk.milo.platform.driving.judgeDriving
import com.shawnkowalchuk.milo.platform.driving.judgedText
import com.shawnkowalchuk.milo.platform.driving.momentText
import kotlin.coroutines.cancellation.CancellationException
import platform.Foundation.NSUserDefaults

/**
 * The driving alert on the iPhone (phase 4d, third part; ADR-015, the second layer): "You seem
 * to be driving. No trip is being recorded. Tap to start one." It is Android's alert: the same
 * rules decide ([judgeDriving]: work hours, no trip open, not just after a trip, once in 30
 * minutes) and the same lines go to the event log. It never starts a trip; a tap does.
 *
 * **Where the reports come from.** Android is told when the phone enters or leaves a vehicle.
 * The iPhone's Core Motion keeps a history of what the phone was doing instead ("in a vehicle
 * since 15:51"), which Swift reads each time iOS wakes Dually because the iPhone has moved some
 * way (a significant change of its position). [motion] turns the history since the last look
 * into what Android is told: a report each time "in a vehicle" turns on or off. So one drive is
 * one "entered", as on Android, however often iOS wakes Dually during it.
 *
 * A report's age on the iPhone is the time since Dually learned of it, which is now: the
 * history is read at the wake, and its last state is the present one. Android's limit on old
 * reports is for reports delivered late; here a drive iOS woke Dually for only after some
 * minutes is still being driven, and still worth the alert.
 */
class IosDrivingAlert internal constructor(
    private val recorder: IosTripRecorder,
    private val trips: TripRepository,
    private val settings: SettingsStore,
    private val eventLog: EventLogRepository,
    private val memory: MotionMemory,
    private val zone: () -> ZoneId,
    private val clock: () -> Long,
) {
    /**
     * Where the next read of Core Motion's history starts: the last look, or a quarter of an
     * hour back the first time. Core Motion keeps a week; a look further back than that starts
     * a week back.
     */
    fun lookFromMs(): Long {
        val nowMs = clock()
        val last = memory.lastLookAtMs ?: return nowMs - FIRST_LOOK_MS
        return last.coerceIn(nowMs - HISTORY_MS, nowMs)
    }

    /**
     * Core Motion's history since [lookFromMs], and what to do with the notification.
     *
     * @param records what the phone was doing, in any order.
     * @param vehicleChosen whether Setup has the vehicle: the iPhone's "a truck is paired".
     * Without it every ride in any vehicle during the work hours would alert.
     * @param notificationsAllowed whether iOS shows Dually's notifications, for the log line.
     * @return [DrivingAlertStep.LEAVE] when the history holds no change, without a log line:
     * iOS wakes Dually often while it drives. A failure is written to the event log and also
     * leaves the notification as it is, as on Android: the alert never takes anything else down.
     */
    suspend fun motion(
        records: List<MotionRecord>,
        vehicleChosen: Boolean,
        notificationsAllowed: Boolean,
    ): DrivingAlertStep = try {
        motionOrFail(records, vehicleChosen, notificationsAllowed)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        reportFailure(failure)
        DrivingAlertStep.LEAVE
    }

    private suspend fun motionOrFail(
        records: List<MotionRecord>,
        vehicleChosen: Boolean,
        notificationsAllowed: Boolean,
    ): DrivingAlertStep {
        val nowMs = clock()
        var inVehicle = memory.reportedInVehicle
        val reports = mutableListOf<VehicleReport>()
        for (record in records.sortedBy { it.startedAtMs }) {
            val says = record.inVehicle ?: continue
            if (says != inVehicle) {
                reports += VehicleReport(entered = says, ageMs = 0)
                inVehicle = says
            }
        }
        memory.reportedInVehicle = inVehicle
        memory.lastLookAtMs = nowMs
        if (reports.isEmpty()) return DrivingAlertStep.LEAVE
        return judge(reports, vehicleChosen, notificationsAllowed, nowMs)
    }

    private suspend fun judge(
        reports: List<VehicleReport>,
        vehicleChosen: Boolean,
        notificationsAllowed: Boolean,
        nowMs: Long,
    ): DrivingAlertStep {
        val stored = stored()
        val facts = recorder.drivingFacts()
        val (truck, evidence) = truckFor(facts)
        val moment =
            DrivingMoment(
                reports = reports,
                alertEnabled = stored.settings.drivingAlertEnabled,
                tripInProgress = facts.tripOpen,
                truckPaired = vehicleChosen,
                truck = truck,
                lastTripEndedAtMs = trips.findNewestTripEndMs(),
                lastAlertAtMs = stored.settings.lastDrivingAlertAtMs,
                nowMs = nowMs,
                schedule = stored.settings.schedule,
                zone = zone(),
                truckNoLongerWatched = facts.parkedNoLongerWatched,
            )
        val verdict = judgeDriving(moment)
        val notRemembered =
            if (verdict.step == DrivingAlertStep.SHOW) rememberAlert(nowMs) else null
        eventLog.add(
            nowMs,
            EventCategory.DRIVING,
            judgedText(reports, verdict, seen = notificationsAllowed),
            momentText(moment, evidence, listOfNotNull(stored.problem, notRemembered)),
        )
        return verdict.step
    }

    /**
     * The truck as the alert reads it on the iPhone, which cannot read a Bluetooth connection:
     * - the last automation said "disconnected": not connected;
     * - it said "connected" and the rules are watching the parked truck: connected, and its
     *   drive-off starts the trip, so no alert;
     * - it said "connected", and nothing is watching: unknown. A missed disconnect leaves that
     *   word behind, and if the truck is there, no trip starts by itself either way. The alert
     *   is shown, as on Android for a truck that cannot be read.
     */
    private fun truckFor(facts: DrivingFacts): Pair<TruckReading.Answer, String> = when {
        !facts.truckBelievedConnected ->
            TruckReading.Answer.NOT_CONNECTED to "the last automation said disconnected"

        facts.watchingParkedTruck || facts.parkedNoLongerWatched ->
            TruckReading.Answer.CONNECTED to "the last automation said connected"

        else ->
            TruckReading.Answer.UNKNOWN to
                "the last automation said connected, but no trip and no wait beside the " +
                "parked truck goes with it"
    }

    /**
     * Stores when the alert was posted, which keeps a second one away for 30 minutes. As on
     * Android, a failure is said in the log line rather than silencing the alert.
     */
    private suspend fun rememberAlert(atMs: Long): String? = try {
        settings.setLastDrivingAlertAtMs(atMs)
        null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (notStored: Exception) {
        "The time of this alert could not be stored ($notStored), so the next report of " +
            "driving may bring it up again."
    }

    // TODO(debt): the iPhone has no crash file, so a failure that the event log cannot take
    //  either is lost. See docs/FINDINGS_LOG.md (2026-11-13, addresses on the iPhone).
    private suspend fun reportFailure(failure: Exception) {
        try {
            eventLog.add(
                clock(),
                EventCategory.ERROR,
                "The driving alert failed while dealing with a report of driving",
                failure.stackTraceToString(),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (lost: Exception) {
            // Nowhere else to put it on the iPhone yet: see the TODO above.
        }
    }

    /** The settings, or the defaults if the file cannot be read, as on Android. */
    private suspend fun stored(): Stored = try {
        Stored(settings.current(), problem = null)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (unreadable: Exception) {
        Stored(
            MiloSettings(),
            problem =
                "The settings cannot be read ($unreadable). The defaults were used: the alert " +
                    "switched on, the work hours Dually starts out with, and no earlier alert.",
        )
    }

    private class Stored(val settings: MiloSettings, val problem: String?)

    private companion object {
        const val FIRST_LOOK_MS = 15 * 60_000L

        /** Core Motion keeps a week of history. */
        const val HISTORY_MS = 7 * 24 * 60 * 60_000L
    }
}

/**
 * One entry of Core Motion's history: from [startedAtMs], the phone was in a vehicle (true),
 * was not (walking, running, cycling, or still and not in a vehicle: false), or Core Motion
 * could not say with some confidence (null, which is passed over).
 */
data class MotionRecord(val startedAtMs: Long, val inVehicle: Boolean?)

/** What the driving alert remembers between wakes. */
internal interface MotionMemory {
    /** Whether the last report handed to the rules was "in a vehicle". */
    var reportedInVehicle: Boolean

    /** When Core Motion's history was last read, or null if never. */
    var lastLookAtMs: Long?
}

/** [MotionMemory] in the app's defaults (NSUserDefaults). */
internal class StoredMotionMemory : MotionMemory {
    private val defaults = NSUserDefaults.standardUserDefaults

    override var reportedInVehicle: Boolean
        get() = defaults.boolForKey(IN_VEHICLE_KEY)
        set(value) = defaults.setBool(value, IN_VEHICLE_KEY)

    override var lastLookAtMs: Long?
        get() = defaults.doubleForKey(LAST_LOOK_KEY).toLong().takeIf { it > 0 }
        set(value) = defaults.setDouble(value?.toDouble() ?: 0.0, LAST_LOOK_KEY)

    private companion object {
        const val IN_VEHICLE_KEY = "driving_reported_in_vehicle"
        const val LAST_LOOK_KEY = "driving_last_motion_look_at_ms"
    }
}
