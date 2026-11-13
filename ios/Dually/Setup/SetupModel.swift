import Combine
import CoreLocation
import DuallyShared
import UIKit

// The Setup screen's state (phase 4d, second part; ADR-015). What each row says is worked out by
// the shared code (DuallyCore.setup, IosSetup.kt); this reads the facts from iOS, keeps the
// user's choice of vehicle, and carries out the buttons.
@MainActor
final class SetupModel: ObservableObject {
    @Published private(set) var screen: SetupScreen?

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

    func choose(_ link: VehicleLink) {
        defaults.set(link.name, forKey: Self.vehicleKey)
        refresh()
    }

    /// Reads the facts again: when the screen shows, and when the app comes back from the
    /// Shortcuts app or Settings.
    func refresh() {
        let location = session.location
        screen = session.core.setup(
            location: Self.access(location.authorization),
            precise: location.precise,
            alwaysAsked: defaults.bool(forKey: Self.alwaysAskedKey),
            vehicle: vehicle
        )
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

    private static let vehicleKey = "vehicle_link"
    private static let alwaysAskedKey = "location_always_asked"
}
