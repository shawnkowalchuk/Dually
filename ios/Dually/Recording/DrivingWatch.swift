import CoreLocation
import CoreMotion
import DuallyShared
import UIKit
import UserNotifications

// The driving alert's eyes on the iPhone (phase 4d, third part; ADR-015, the second layer). iOS
// wakes Dually, even when it is closed, each time the iPhone has moved some way (a significant
// change of its position, which needs Always location). Dually then reads Core Motion's history
// of what the phone was doing, and the shared code (IosDrivingAlert: Android's own rules) says
// whether to show "You seem to be driving", take it away, or leave it. It never starts a trip;
// a tap on it does.
@MainActor
final class DrivingWatch: NSObject {
    nonisolated static let alertId = "driving-alert"

    /// What a tap on the alert does: start a trip.
    var onTap: (() -> Void)?

    private let manager = CLLocationManager()
    private let motion = CMMotionActivityManager()
    private let core: DuallyCore
    private var watching = false

    init(core: DuallyCore) {
        self.core = core
        super.init()
        manager.delegate = self
        UNUserNotificationCenter.current().delegate = self
    }

    /// Watches the iPhone's moves while the alert can be of use: Always location, motion
    /// allowed, and a vehicle chosen in Setup. Called at every start of the app, which iOS
    /// requires after it relaunches Dually for a move, and whenever Setup changes.
    func arm() {
        let useful = manager.authorizationStatus == .authorizedAlways
            && CMMotionActivityManager.authorizationStatus() == .authorized
            && vehicleChosen
        if useful && !watching {
            manager.startMonitoringSignificantLocationChanges()
            watching = true
        } else if !useful && watching {
            manager.stopMonitoringSignificantLocationChanges()
            watching = false
        }
    }

    /// Takes the alert away: a trip has started.
    func withdraw() {
        UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: [Self.alertId])
    }

    private var vehicleChosen: Bool {
        UserDefaults.standard.string(forKey: SetupModel.vehicleKey) != nil
    }

    /// One wake: Core Motion's history since the last look, judged by the shared rules.
    private func look() async {
        // iOS gives a wake for a move a few seconds; this asks for the time the look needs.
        let task = UIApplication.shared.beginBackgroundTask(withName: "Driving alert")
        defer { UIApplication.shared.endBackgroundTask(task) }
        let from = Date(timeIntervalSince1970: Double(core.drivingAlert.lookFromMs()) / 1000)
        let records = await history(from: from)
        let allowed = await UNUserNotificationCenter.current().notificationSettings()
            .authorizationStatus == .authorized
        guard let step = try? await core.drivingAlert.motion(
            records: records,
            vehicleChosen: vehicleChosen,
            notificationsAllowed: allowed
        ) else { return }
        switch step {
        case DrivingAlertStep.show: await post()
        case DrivingAlertStep.withdraw: withdraw()
        default: break
        }
    }

    /// Core Motion's history from [from] to now, as the shared code reads it.
    private func history(from: Date) async -> [MotionRecord] {
        await withCheckedContinuation { continuation in
            motion.queryActivityStarting(from: from, to: Date(), to: .main) { activities, _ in
                let records = (activities ?? []).map { activity in
                    MotionRecord(
                        startedAtMs: Int64(activity.startDate.timeIntervalSince1970 * 1000),
                        inVehicle: Self.inVehicle(activity).map { KotlinBoolean(value: $0) }
                    )
                }
                continuation.resume(returning: records)
            }
        }
    }

    /// In a vehicle, not, or unsure (low confidence, or nothing Core Motion could name).
    private nonisolated static func inVehicle(_ activity: CMMotionActivity) -> Bool? {
        if activity.confidence == .low { return nil }
        if activity.automotive { return true }
        if activity.walking || activity.running || activity.cycling || activity.stationary {
            return false
        }
        return nil
    }

    /// Posts the alert, with Android's words. Posted again while it shows, it makes no sound.
    private func post() async {
        let center = UNUserNotificationCenter.current()
        let showing = await center.deliveredNotifications().contains {
            $0.request.identifier == Self.alertId
        }
        let content = UNMutableNotificationContent()
        content.title = "You seem to be driving"
        content.body = "No trip is being recorded. Tap to start one."
        content.sound = showing ? nil : .default
        try? await center.add(UNNotificationRequest(identifier: Self.alertId, content: content, trigger: nil))
    }
}

extension DrivingWatch: CLLocationManagerDelegate {
    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        Task { @MainActor in await self.look() }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        Task { @MainActor in self.arm() }
    }
}

extension DrivingWatch: UNUserNotificationCenterDelegate {
    /// The alert shows even with Dually open.
    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }

    /// A tap on the alert starts a trip, as on Android.
    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let tapped = response.notification.request.identifier == Self.alertId
        Task { @MainActor in
            if tapped { self.onTap?() }
            completionHandler()
        }
    }
}
