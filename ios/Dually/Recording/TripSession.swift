import DuallyShared
import Foundation
import WidgetKit

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
    /// The driving alert (phase 4d, third part).
    let drivingWatch: DrivingWatch
    /// The trip on the Lock Screen and in the Dynamic Island (phase 4e), iOS 16.1 and later.
    private let liveTrip: AnyObject?

    /// The trip being recorded, or nil.
    @Published private(set) var recording: RecordingNow?
    @Published private(set) var failure: String?

    private init() {
        drivingWatch = DrivingWatch(core: core)
        if #available(iOS 16.1, *) {
            liveTrip = LiveTrip()
        } else {
            liveTrip = nil
        }
        location.onFix = { [weak self] fix in
            Task { await self?.add(fix) }
        }
        drivingWatch.onTap = { [weak self] in
            Task { await self?.startFromAlert() }
        }
        drivingWatch.arm()
    }

    /// The app has started: a trip or a wait left from before is picked up, and its GPS starts
    /// again; the addresses that are due are looked up.
    func appStarted() async {
        defer { Task { await self.updateWidget(force: true) } }
        await apply {
            try await self.core.appStarted()
            return try await self.core.recorder.now()
        }
    }

    /// Dually came to the front: a trip that started in the background gets its Live Activity.
    func cameToFront() async {
        if #available(iOS 16.1, *) { (liveTrip as? LiveTrip)?.cameToFront() }
        await refresh()
    }

    /// Reads the trip being recorded again, as of now.
    func refresh() async {
        await apply { try await self.core.recorder.now() }
    }

    func start() async {
        await apply { try await self.core.recorder.start() }
    }

    /// Start on the home-screen widget.
    func startFromWidget() async {
        await apply { try await self.core.recorder.startFromWidget() }
    }

    /// A tap on the driving alert: a trip started by hand, as on Android.
    func startFromAlert() async {
        await apply { try await self.core.recorder.startFromAlert() }
    }

    func end() async {
        await apply { try await self.core.recorder.end() }
    }

    /// End trip on the Live Activity.
    func endFromLiveActivity() async {
        await apply { try await self.core.recorder.endFromLiveActivity() }
    }

    /// The Shortcuts automation says the vehicle connected.
    func truckConnected() async {
        await apply { try await self.core.recorder.truckConnected() }
    }

    /// The Shortcuts automation says the vehicle disconnected.
    func truckDisconnected() async {
        await apply { try await self.core.recorder.truckDisconnected() }
    }

    private var widgetDrawnAt = Date.distantPast
    private var widgetRecording: Bool?

    /// Leaves the widget's figures where it reads them and asks for a new drawing: at once when
    /// a trip starts or ends, and while one is recorded at most every minute, since iOS limits
    /// how often a widget is redrawn and the Live Activity has the running figures.
    func updateWidget(force: Bool = false) async {
        let isRecording = recording != nil
        let changed = isRecording != widgetRecording
        guard force || changed || Date().timeIntervalSince(widgetDrawnAt) >= 60 else { return }
        widgetRecording = isRecording
        widgetDrawnAt = Date()
        let dollars = try? await core.widget.dollars(nowMs: Int64(Date().timeIntervalSince1970 * 1000))
        WidgetSnapshot(
            recording: isRecording,
            startedAt: recording.map { Date(timeIntervalSince1970: Double($0.startedAtMs) / 1000) },
            distance: recording.map { core.distanceText(metres: $0.metres) },
            unit: core.unitWord(),
            dollars: dollars
        ).save()
        WidgetCenter.shared.reloadAllTimelines()
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
        // A trip has begun, however it began: the alert's "tap to start one" is no longer true.
        if recording != nil { drivingWatch.withdraw() }
        if #available(iOS 16.1, *) {
            let distance = recording.map { core.distanceText(metres: $0.metres) } ?? ""
            (liveTrip as? LiveTrip)?.show(recording, distance: distance, unit: core.unitWord())
        }
        await updateWidget()
        // TODO(debt): the wait beside a parked truck runs the GPS as a trip does, for up to 72
        //  hours. See docs/FINDINGS_LOG.md (2026-11-13, phase 4d, first part).
        if core.recorder.wantsGps {
            location.start()
        } else {
            location.stop()
        }
    }
}
