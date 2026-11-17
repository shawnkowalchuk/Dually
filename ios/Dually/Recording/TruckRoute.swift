import AVFoundation
import DuallyShared
import Foundation

// The iPhone's one reading of the truck (2026-11-16, ADR-015's second layer): where its audio goes.
// No app can ask whether a classic Bluetooth device is connected, but the current audio route
// names CarPlay, and a Bluetooth car kit by its name. When the user's automation says the truck
// connected, Dually notes the Bluetooth route's name it finds then; at a wake for a drive, the
// same name in the route (or CarPlay, for a CarPlay truck) is the truck, connected. With several
// vehicles (ADR-018) a name is noted for each vehicle its action names, and a wake finds which
// one it is; CarPlay, which the route does not name, is the vehicle that last connected by it.
@MainActor
enum TruckRoute {
    /// The name noted at a connect that named no vehicle.
    private static let nameKey = "truck_route_name"
    /// The names noted for each vehicle: [vehicle id: Bluetooth name].
    private static let namesKey = "truck_route_names"
    /// The vehicle that last connected by CarPlay, or none.
    private static let carPlayVehicleKey = "truck_route_carplay_vehicle"

    /// Notes the truck's Bluetooth route at the automation's connect, for a Bluetooth truck:
    /// the one Bluetooth output there is then. None, or more than one (earbuds beside the car
    /// kit), notes nothing, since it cannot be told which is the truck. Returns what was noted
    /// when it differs from before, to be said in the Log; nil when nothing changed, and always
    /// for a CarPlay truck, which the route names by its kind.
    ///
    /// - Parameter vehicle: the vehicle the action names, or nil for none.
    static func remember(vehicle: String?) -> NotedRoute? {
        let defaults = UserDefaults.standard
        switch defaults.string(forKey: SetupModel.vehicleKey) {
        case VehicleLink.carplay.name:
            defaults.set(vehicle, forKey: carPlayVehicleKey)
            return nil
        case VehicleLink.bluetooth.name:
            break
        default:
            return nil
        }
        let ports = AVAudioSession.sharedInstance().currentRoute.outputs.filter { bluetooth.contains($0.portType) }
        let name = ports.count == 1 ? ports[0].portName : nil
        var names = defaults.dictionary(forKey: namesKey) as? [String: String] ?? [:]
        let before = vehicle.map { names[$0] } ?? defaults.string(forKey: nameKey)
        if let name {
            if let vehicle {
                names[vehicle] = name
                defaults.set(names, forKey: namesKey)
            } else {
                defaults.set(name, forKey: nameKey)
            }
        }
        if name == before || (name == nil && before != nil) { return nil }
        return NotedRoute(name: name)
    }

    /// Whether the iPhone's audio goes to a vehicle now (see [vehicleNow]).
    static func connectedNow() -> Bool {
        vehicleNow() != nil
    }

    /// The vehicle the iPhone's audio goes to now, or nil for none: CarPlay for a CarPlay
    /// truck, a noted Bluetooth name for a Bluetooth one. Nil with no vehicle chosen in Setup.
    static func vehicleNow() -> FoundVehicle? {
        let defaults = UserDefaults.standard
        let outputs = AVAudioSession.sharedInstance().currentRoute.outputs
        switch defaults.string(forKey: SetupModel.vehicleKey) {
        case VehicleLink.carplay.name:
            guard outputs.contains(where: { $0.portType == .carAudio }) else { return nil }
            return FoundVehicle(vehicle: defaults.string(forKey: carPlayVehicleKey))
        case VehicleLink.bluetooth.name:
            let inRoute = Set(outputs.filter { bluetooth.contains($0.portType) }.map(\.portName))
            let names = defaults.dictionary(forKey: namesKey) as? [String: String] ?? [:]
            if let named = names.sorted(by: { $0.key < $1.key }).first(where: { inRoute.contains($0.value) }) {
                return FoundVehicle(vehicle: named.key)
            }
            guard let name = defaults.string(forKey: nameKey), inRoute.contains(name) else { return nil }
            return FoundVehicle(vehicle: nil)
        default:
            return nil
        }
    }

    /// What [remember] noted: a Bluetooth name, or none to be found.
    struct NotedRoute {
        let name: String?
    }

    /// What [vehicleNow] found: the vehicle, or nil for the one an action that names none is in.
    struct FoundVehicle {
        let vehicle: String?
    }

    private static let bluetooth: [AVAudioSession.Port] = [.bluetoothA2DP, .bluetoothHFP, .bluetoothLE]
}
