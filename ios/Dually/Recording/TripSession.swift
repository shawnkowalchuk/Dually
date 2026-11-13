import DuallyShared
import Foundation

// The trip being recorded, for all of the iPhone app (phase 4d). Home's Start and End, the
// Shortcuts automation's "Start a trip" and "End a trip" (TripIntents.swift) and the GPS all go
// through this one object, so they share one recorder and one GPS. iOS can launch the app in the
// background to run an automation, with no screen at all: this is made on first use either way.
@MainActor
final class TripSession: ObservableObject {
    static let shared = TripSession()

    /// The shared code's door, opened once: it opens the database.
    let core = DuallyCore.companion.open(placeFinder: AppleGeocoder())
    let location = LocationRecorder()

    /// The trip being recorded, or nil.
    @Published private(set) var recording: RecordingNow?
    @Published private(set) var failure: String?

    private init() {
        location.onFix = { [weak self] fix in
            Task { await self?.add(fix) }
        }
    }

    /// The app has started: a trip or a wait left from before is picked up, and its GPS starts
    /// again; the addresses that are due are looked up.
    func appStarted() async {
        await apply {
            try await self.core.appStarted()
            return try await self.core.recorder.now()
        }
    }

    /// Reads the trip being recorded again, as of now.
    func refresh() async {
        await apply { try await self.core.recorder.now() }
    }

    func start() async {
        await apply { try await self.core.recorder.start() }
    }

    func end() async {
        await apply { try await self.core.recorder.end() }
    }

    /// The Shortcuts automation says the vehicle connected.
    func truckConnected() async {
        await apply { try await self.core.recorder.truckConnected() }
    }

    /// The Shortcuts automation says the vehicle disconnected.
    func truckDisconnected() async {
        await apply { try await self.core.recorder.truckDisconnected() }
    }

    private func add(_ fix: Fix) async {
        await apply {
            try await self.core.recorder.addFix(
                wallClockMs: fix.wallClockMs,
                elapsedMs: fix.elapsedMs,
                latitude: fix.latitude,
                longitude: fix.longitude,
                accuracyMetres: fix.accuracyMetres.map { KotlinFloat(value: $0) },
                speedMetresPerSecond: fix.speedMetresPerSecond.map { KotlinFloat(value: $0) }
            )
        }
    }

    /// One step of the recorder, then the GPS on or off as the trip rules now want it: on while a
    /// trip is open, and while they wait beside a parked truck for it to move.
    private func apply(_ step: @escaping () async throws -> RecordingNow?) async {
        do {
            recording = try await step()
            failure = nil
        } catch {
            failure = error.localizedDescription
        }
        // TODO(debt): the wait beside a parked truck runs the GPS as a trip does, for up to 72
        //  hours. See docs/FINDINGS_LOG.md (2026-11-13, phase 4d, first part).
        if core.recorder.wantsGps {
            location.start()
        } else {
            location.stop()
        }
    }
}
