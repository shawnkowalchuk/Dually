import Combine
import CoreLocation
import CoreMotion
import DuallyShared
import UIKit
import UserNotifications

// The Setup screen's state (phase 4d, second part; ADR-015). What each row says is worked out by
// the shared code (DuallyCore.setup, IosSetup.kt); this reads the facts from iOS, keeps the
// user's choice of vehicle, and carries out the buttons.
@MainActor
final class SetupModel: ObservableObject {
    @Published private(set) var screen: SetupScreen?
    /// Whether iOS shows Dually's notifications, read again at each refresh.
    private var notifications = Permission.notAsked

    private let session: TripSession
    private let defaults = UserDefaults.standard
    private var watching: Set<AnyCancellable> = []

    init(session: TripSession) {
        self.session = session
        // A change of the location permission, made in iOS's question or in Settings, shows at
        // once.
        session.location.$authorization
            .combineLatest(session.location.$precise)
            .sink { [weak self] _, _ in
                Task { @MainActor in self?.refresh() }
            }
            .store(in: &watching)
    }

    /// The vehicle the automations watch, as the user chose it: CarPlay or Bluetooth.
    var vehicle: VehicleLink? {
        switch defaults.string(forKey: Self.vehicleKey) {
        case VehicleLink.carplay.name: return .carplay
        case VehicleLink.bluetooth.name: return .bluetooth
        default: return nil
        }
    }

    /// A new vehicle link needs automations of its own: the user's word for the old ones goes.
    func choose(_ link: VehicleLink) {
        defaults.set(link.name, forKey: Self.vehicleKey)
        defaults.removeObject(forKey: Self.startMadeKey)
        defaults.removeObject(forKey: Self.endMadeKey)
        refresh()
    }

    /// Reads the facts again: when the screen shows, and when the app comes back from the
    /// Shortcuts app or Settings. The driving alert's watch follows what changed.
    func refresh() {
        compute()
        Task {
            let status = await UNUserNotificationCenter.current().notificationSettings()
                .authorizationStatus
            notifications = Self.permission(status)
            compute()
        }
    }

    private func compute() {
        let location = session.location
        screen = session.core.setup(
            location: Self.access(location.authorization),
            precise: location.precise,
            alwaysAsked: defaults.bool(forKey: Self.alwaysAskedKey),
            vehicle: vehicle,
            startMade: defaults.bool(forKey: Self.startMadeKey),
            endMade: defaults.bool(forKey: Self.endMadeKey),
            motion: Self.motionPermission(),
            notifications: notifications
        )
        session.drivingWatch.arm()
    }

    func perform(_ action: SetupAction) {
        switch action {
        case SetupAction.askLocation:
            session.location.askWhenInUse()
        case SetupAction.askAlways:
            defaults.set(true, forKey: Self.alwaysAskedKey)
            session.location.askAlways()
        case SetupAction.openSettings:
            open(UIApplication.openSettingsURLString)
        case SetupAction.openShortcuts:
            open("shortcuts://")
        case SetupAction.confirmStartMade:
            defaults.set(true, forKey: Self.startMadeKey)
            refresh()
        case SetupAction.confirmEndMade:
            defaults.set(true, forKey: Self.endMadeKey)
            refresh()
        case SetupAction.askMotion:
            // Any read of the history makes iOS ask; the answer arrives with the result.
            CMMotionActivityManager().queryActivityStarting(from: Date(), to: Date(), to: .main) { _, _ in
                Task { @MainActor in self.refresh() }
            }
        case SetupAction.askNotifications:
            Task {
                _ = try? await UNUserNotificationCenter.current()
                    .requestAuthorization(options: [.alert, .sound])
                refresh()
            }
        default:
            break
        }
    }

    private func open(_ address: String) {
        guard let url = URL(string: address) else { return }
        UIApplication.shared.open(url)
    }

    private static func access(_ status: CLAuthorizationStatus) -> LocationAccess {
        switch status {
        case .authorizedAlways: return .always
        case .authorizedWhenInUse: return .whileUsing
        case .notDetermined: return .notAsked
        default: return .off
        }
    }

    private static func motionPermission() -> Permission {
        switch CMMotionActivityManager.authorizationStatus() {
        case .authorized: return .allowed
        case .notDetermined: return .notAsked
        default: return .off
        }
    }

    private static func permission(_ status: UNAuthorizationStatus) -> Permission {
        switch status {
        case .authorized, .provisional, .ephemeral: return .allowed
        case .notDetermined: return .notAsked
        default: return .off
        }
    }

    private static let startMadeKey = "automation_start_made"
    private static let endMadeKey = "automation_end_made"

    /// Where the vehicle chosen in Setup is kept; the driving alert reads it too.
    static let vehicleKey = "vehicle_link"
    private static let alwaysAskedKey = "location_always_asked"
}
