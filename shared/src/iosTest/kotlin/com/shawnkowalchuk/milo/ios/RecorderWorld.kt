package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.time.systemZoneId
import com.shawnkowalchuk.milo.core.trip.TRACK_START_WALL_CLOCK_MS
import com.shawnkowalchuk.milo.core.trip.TrackPoint
import com.shawnkowalchuk.milo.data.buildMiloDatabase
import com.shawnkowalchuk.milo.data.buildPointsDatabase
import com.shawnkowalchuk.milo.data.eventlog.EventLogEntry
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.point.RawPointRepository
import com.shawnkowalchuk.milo.data.settings.buildSettingsStore
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.trip.TripLedger
import com.shawnkowalchuk.milo.platform.trip.TripRuleSettings
import kotlin.random.Random
import okio.FileSystem

/**
 * The iPhone's recording over real databases and a real settings file, in a folder of their own
 * that [close] removes, with a clock the test sets ([nowMs]) and the truck as a test says the
 * last automation left it.
 */
internal class RecorderWorld {
    private val folder =
        (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "recorder-${Random.nextLong()}").also {
            FileSystem.SYSTEM.createDirectories(it)
        }
    private val database = buildMiloDatabase(folder.toString())
    private val points = buildPointsDatabase(folder.toString())
    val settings = buildSettingsStore(folder.toString())
    val log = EventLogRepository(database.eventLogDao())
    val trips = TripRepository(database.tripDao())
    var nowMs = TRACK_START_WALL_CLOCK_MS
    private val truck =
        object : TruckBelief {
            override var connected = false
            override var connectHeardAtMs: Long? = null
            override var disconnectHeardAtMs: Long? = null
        }

    fun truckBelief(connected: Boolean) {
        truck.connected = connected
    }

    fun recorder(): IosTripRecorder = IosTripRecorder(
        TripLedger(
            trips,
            RawPointRepository(points.rawPointDao()),
            settings,
            zone = ::systemZoneId,
        ),
        log,
        TripRuleSettings(settings, log) { nowMs },
        truck,
    ) { nowMs }

    suspend fun drive(recorder: IosTripRecorder, track: List<TrackPoint>): RecordingNow? {
        var last: RecordingNow? = null
        for (fix in track) {
            nowMs = fix.wallClockMs
            last =
                recorder.addFix(
                    fix.wallClockMs,
                    fix.elapsedRealtimeMs,
                    fix.latitude,
                    fix.longitude,
                    fix.accuracyMetres,
                    fix.speedMetresPerSecond,
                )
        }
        return last
    }

    /** Every line of the event log, oldest first. */
    suspend fun logLines(): List<EventLogEntry> {
        val lines = mutableListOf<EventLogEntry>()
        log.readAll { lines += it }
        return lines.sortedBy { it.atMs }
    }

    fun sentReportDao() = database.sentReportDao()

    fun close() {
        database.close()
        points.close()
        FileSystem.SYSTEM.deleteRecursively(folder)
    }
}
