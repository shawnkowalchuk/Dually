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

    /// Notes the truck's Bluetooth route at the automation's connect. Nothing for CarPlay, which
    /// the route names by its kind.
    static func remember() {
        guard let port = AVAudioSession.sharedInstance().currentRoute.outputs.first(where: { bluetooth.contains($0.portType) })
        else { return }
        UserDefaults.standard.set(port.portName, forKey: nameKey)
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

    private static let bluetooth: [AVAudioSession.Port] = [.bluetoothA2DP, .bluetoothHFP, .bluetoothLE]
}
