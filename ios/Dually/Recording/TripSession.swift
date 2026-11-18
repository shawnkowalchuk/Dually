import DuallyShared
import Foundation
import UIKit
import UserNotifications
import WidgetKit

// The trip being recorded, for all of the iPhone app (phase 4d). Home's Start and End, the
// Shortcuts automation's "Start a trip" and "End a trip" (TripIntents.swift) and the GPS all go
// through this one object, so they share one recorder and one GPS. iOS can launch the app in the
// background to run an automation, with no screen at all: this is made on first use either way.
@MainActor
final class TripSession: ObservableObject {
    static let shared = TripSession()

    /// The shared code's door, opened once: it opens the database.
    let core = DuallyCore.companion.open(placeFinder: AppleGeocoder(), cloud: FirebaseCloud.shared)
    let location = LocationRecorder()
    /// The driving alert (phase 4d, third part).
    let drivingWatch: DrivingWatch
    /// The trip on the Lock Screen and in the Dynamic Island (phase 4e), iOS 16.1 and later.
    private let liveTrip: AnyObject?

    /// The trip being recorded, or nil.
    @Published private(set) var recording: RecordingNow?
    @Published private(set) var failure: String?
    /// A tap on the service reminder asked for Service; the app opens it and sets this back.
    @Published var serviceAsked = false
    /// A tap on the monthly reminder asked for that month's report; the app opens it.
    @Published var reportAsked: (year: Int, month: Int)?
    /// A trip has started on the one-year party's day with Home in front: the party plays now.
    @Published var partyDue = false

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
        drivingWatch.onServiceTap = { [weak self] in self?.serviceAsked = true }
        drivingWatch.onReminderTap = { [weak self] year, month in self?.reportAsked = (year, month) }
        drivingWatch.onWake = { [weak self] in await self?.wokenByMove() }
        drivingWatch.onDrivingWithoutTrip = { [weak self] in await self?.drivingWithoutTrip() ?? false }
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

    /// Dually came to the front: a trip that started in the background gets its Live Activity,
    /// and the online backup sends what an earlier upload could not.
    func cameToFront() async {
        if #available(iOS 16.1, *) { (liveTrip as? LiveTrip)?.cameToFront() }
        await refresh()
        uploadBackup("the app came to the front")
        await Reminders.plan(core: core)
    }

    /// One upload of the online backup (phase 4h), with the time iOS gives an app that is going
    /// to sleep to finish it. Nothing at all where nobody is signed in to the backup.
    func uploadBackup(_ reason: String) {
        guard FirebaseCloud.shared.available, core.cloud.account() != nil else { return }
        var task = UIBackgroundTaskIdentifier.invalid
        task = UIApplication.shared.beginBackgroundTask(withName: "Online backup") {
            UIApplication.shared.endBackgroundTask(task)
            task = .invalid
        }
        Task {
            _ = try? await core.cloud.upload(reason: reason)
            if task != .invalid { UIApplication.shared.endBackgroundTask(task) }
        }
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

    /// Start trip on the CarPlay screen (phase 4f).
    func startFromCar() async {
        await apply { try await self.core.recorder.startFromCar() }
    }

    /// End trip on the CarPlay screen (phase 4f).
    func endFromCar() async {
        await apply { try await self.core.recorder.endFromCar() }
    }

    /// End trip on the Live Activity.
    func endFromLiveActivity() async {
        await apply { try await self.core.recorder.endFromLiveActivity() }
    }

    /// The Shortcuts automation says the vehicle connected: the one its action names, or nil.
    func truckConnected(vehicle: String?) async {
        // Where the truck's audio goes, noted for a wake that has to find it (TruckRoute), and
        // said in the Log when it changes.
        if let noted = TruckRoute.remember(vehicle: vehicle) {
            try? await core.recorder.noteTruckRoute(name: noted.name)
        }
        await apply { try await self.core.recorder.truckConnected(vehicle: vehicle) }
    }

    /// A wake for a move: a trip or a wait stored before carries on, its GPS on again.
    func wokenByMove() async {
        await apply { try await self.core.recorder.now() }
        await checkReadTruck()
    }

    /// When was the truck a wake read as connected last missing from the audio route, or nil.
    private var routeMissingSince: Date?

    /// While the truck is believed connected because a wake read it (not because the
    /// automation said so), its route is read again with every fix, as Android reads the truck
    /// through a trip: gone for a minute, it is taken as disconnected, and the rules' grace
    /// period or the end of the wait follows.
    private func checkReadTruck() async {
        guard core.recorder.connectedByReading else {
            routeMissingSince = nil
            return
        }
        if TruckRoute.connectedNow() {
            routeMissingSince = nil
            return
        }
        let since = routeMissingSince ?? Date()
        routeMissingSince = since
        guard Date().timeIntervalSince(since) >= 60 else { return }
        routeMissingSince = nil
        await apply { try await self.core.recorder.truckReadGone() }
    }

    /// Driving with no trip open, at a wake: if the iPhone's audio goes to the truck, the
    /// connect the automation missed is recovered and a trip starts. True if it did.
    func drivingWithoutTrip() async -> Bool {
        guard recording == nil, let found = TruckRoute.vehicleNow() else { return false }
        await apply { try await self.core.recorder.truckReadConnected(vehicle: found.vehicle) }
        return recording != nil
    }

    /// The Shortcuts automation says the vehicle disconnected: the one its action names, or nil.
    func truckDisconnected(vehicle: String?) async {
        await apply { try await self.core.recorder.truckDisconnected(vehicle: vehicle) }
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
        let dollars = try? await core.widget.dollars(nowMs: core.timeNow())
        WidgetSnapshot(
            recording: isRecording,
            startedAt: recording.map { Date(timeIntervalSince1970: Double($0.startedAtMs) / 1000) },
            distance: recording.map { core.distanceText(metres: $0.metres) },
            unit: core.unitWord(),
            dollars: dollars,
            light: Look.shared.light
        ).save()
        WidgetCenter.shared.reloadAllTimelines()
    }

    /// The service reminder, as Android's: the items soon or due that were not told yet.
    private func remindOfService() async {
        guard let reminder = try? await core.service.reminder() else { return }
        let content = UNMutableNotificationContent()
        content.title = reminder.title
        content.body = reminder.text
        content.sound = .default
        let request = UNNotificationRequest(identifier: DrivingWatch.serviceId, content: content, trigger: nil)
        try? await UNUserNotificationCenter.current().add(request)
    }

    private func add(_ fix: Fix) async {
        await checkReadTruck()
        await apply {
            // Dated by Dually's own clock at the fix's moment, not the phone's (ADR-006), so a
            // fix taken while the date is set ahead is dated when it was taken.
            try await self.core.recorder.addFix(
                wallClockMs: self.core.timeAtElapsed(elapsedMs: fix.elapsedMs),
                elapsedMs: fix.elapsedMs,
                latitude: fix.latitude,
                longitude: fix.longitude,
                accuracyMetres: fix.accuracyMetres.map { KotlinFloat(value: $0) },
                speedMetresPerSecond: fix.speedMetresPerSecond.map { KotlinFloat(value: $0) }
            )
        }
    }

    /// The connect and trip-start sounds the last step made due, played by the settings
    /// (2026-11-16, as on Android).
    private func playDueSounds() async {
        guard let due = try? await core.recorder.takeSounds(),
              let plan = try? await core.sounds.toPlay(due: due)
        else { return }
        TripSoundPlayer.shared.play(plan.sounds, core: core)
        // On the one-year party's day, with Home in front, the trip's first sound is the pop of
        // the party that plays there now; if no party takes it within two seconds, the trip's
        // start plays it itself (Android's POP_HANDOVER_MS).
        guard plan.popLeftToAnimation else { return }
        partyDue = true
        try? await Task.sleep(nanoseconds: 2_000_000_000)
        if core.party.takePop(),
           (try? await core.party.popHeard())?.boolValue == true {
            TripSoundPlayer.shared.play([core.sounds.partyPop()], core: core)
        }
    }

    /// One step of the recorder, then the GPS on or off as the trip rules now want it: on while a
    /// trip is open, and while they wait beside a parked truck for it to move.
    private func apply(_ step: @escaping () async throws -> RecordingNow?) async {
        let wasRecording = recording != nil
        defer {
            // A trip has ended: the service items that came due are told now, never during one,
            // and the online backup uploads it before iOS puts the app to sleep.
            if wasRecording && recording == nil {
                Task { await self.remindOfService() }
                uploadBackup("a trip ended")
            }
            // A trip today takes today's daily check away; the plan follows every start and end.
            if wasRecording != (recording != nil) {
                Task { await Reminders.plan(core: self.core) }
            }
        }
        do {
            recording = try await step()
            failure = nil
        } catch {
            failure = error.localizedDescription
        }
        await playDueSounds()
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
