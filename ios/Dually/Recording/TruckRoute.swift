import AVFoundation
import DuallyShared
import Foundation

// The iPhone's one reading of the truck (2026-11-16, ADR-015's second layer): where its audio goes.
// No app can ask whether a classic Bluetooth device is connected, but the current audio route
// names CarPlay, and a Bluetooth car kit by its name. When the user's automation says the truck
// connected, Dually notes the Bluetooth route's name it finds then; at a wake for a drive, the
// same name in the route (or CarPlay, for a CarPlay truck) is the truck, connected.
@MainActor
enum TruckRoute {
    private static let nameKey = "truck_route_name"

    /// Notes the truck's Bluetooth route at the automation's connect, for a Bluetooth truck:
    /// the one Bluetooth output there is then. None, or more than one (earbuds beside the car
    /// kit), notes nothing, since it cannot be told which is the truck. Returns what was noted
    /// when it differs from before, to be said in the Log; nil when nothing changed, and always
    /// for a CarPlay truck, which the route names by its kind.
    static func remember() -> NotedRoute? {
        guard UserDefaults.standard.string(forKey: SetupModel.vehicleKey) == VehicleLink.bluetooth.name
        else { return nil }
        let ports = AVAudioSession.sharedInstance().currentRoute.outputs.filter { bluetooth.contains($0.portType) }
        let name = ports.count == 1 ? ports[0].portName : nil
        let before = UserDefaults.standard.string(forKey: nameKey)
        if let name { UserDefaults.standard.set(name, forKey: nameKey) }
        if name == before || (name == nil && before != nil) { return nil }
        return NotedRoute(name: name)
    }

    /// Whether the iPhone's audio goes to the truck now: CarPlay for a CarPlay truck, the noted
    /// Bluetooth name for a Bluetooth one. False with no vehicle chosen in Setup.
    static func connectedNow() -> Bool {
        let outputs = AVAudioSession.sharedInstance().currentRoute.outputs
        switch UserDefaults.standard.string(forKey: SetupModel.vehicleKey) {
        case VehicleLink.carplay.name:
            return outputs.contains { $0.portType == .carAudio }
        case VehicleLink.bluetooth.name:
            guard let name = UserDefaults.standard.string(forKey: nameKey) else { return false }
            return outputs.contains { bluetooth.contains($0.portType) && $0.portName == name }
        default:
            return false
        }
    }

    /// What [remember] noted: a Bluetooth name, or none to be found.
    struct NotedRoute {
        let name: String?
    }

    private static let bluetooth: [AVAudioSession.Port] = [.bluetoothA2DP, .bluetoothHFP, .bluetoothLE]
}
