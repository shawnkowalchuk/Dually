import CoreLocation
import Foundation

// The iPhone's GPS while a trip records (phase 4b). Core Location hands every fix to [onFix];
// the shared code decides what each one is worth (the distance rules, as on Android). Updates go
// on with the app in the background, with the blue location pill iOS shows for it, because the
// app has the "location" background mode and starts them while it is open.
@MainActor
final class LocationRecorder: NSObject, ObservableObject {
    /// Whether the user has let Dually use the location at all.
    @Published private(set) var authorization: CLAuthorizationStatus

    /// Each usable fix, as soon as Core Location has it.
    var onFix: ((Fix) -> Void)?

    private let manager = CLLocationManager()
    private var recording = false

    override init() {
        authorization = manager.authorizationStatus
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = kCLDistanceFilterNone
        manager.activityType = .automotiveNavigation
        manager.pausesLocationUpdatesAutomatically = false
    }

    /// Starts the fixes, asking for permission first if it was never asked. Calling it while
    /// they run changes nothing.
    func start() {
        if recording { return }
        recording = true
        if manager.authorizationStatus == .notDetermined {
            manager.requestWhenInUseAuthorization()
            return
        }
        beginUpdates()
    }

    func stop() {
        if !recording { return }
        recording = false
        manager.stopUpdatingLocation()
        manager.allowsBackgroundLocationUpdates = false
    }

    private func beginUpdates() {
        guard recording else { return }
        switch manager.authorizationStatus {
        case .authorizedAlways, .authorizedWhenInUse:
            manager.allowsBackgroundLocationUpdates = true
            manager.showsBackgroundLocationIndicator = true
            manager.startUpdatingLocation()
        default:
            break
        }
    }
}

extension LocationRecorder: CLLocationManagerDelegate {
    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        Task { @MainActor in
            self.authorization = status
            self.beginUpdates()
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let fixes = locations.compactMap(Fix.init)
        Task { @MainActor in
            for fix in fixes { self.onFix?(fix) }
        }
    }
}

/// One GPS fix, in the units the shared code stores (RawPoint).
struct Fix {
    let wallClockMs: Int64
    /// Time since the phone started, which never jumps when the clock is set: the distance
    /// rules judge speeds by it, as Android's elapsedRealtime.
    let elapsedMs: Int64
    let latitude: Double
    let longitude: Double
    let accuracyMetres: Float?
    let speedMetresPerSecond: Float?

    /// A fix from Core Location, or nil when it has no position (a negative accuracy).
    init?(_ location: CLLocation) {
        guard location.horizontalAccuracy >= 0 else { return nil }
        let ageMs = Date().timeIntervalSince(location.timestamp) * 1000
        wallClockMs = Int64(location.timestamp.timeIntervalSince1970 * 1000)
        elapsedMs = Int64(Double(clock_gettime_nsec_np(CLOCK_MONOTONIC)) / 1_000_000 - ageMs)
        latitude = location.coordinate.latitude
        longitude = location.coordinate.longitude
        accuracyMetres = Float(location.horizontalAccuracy)
        speedMetresPerSecond = location.speed >= 0 ? Float(location.speed) : nil
    }
}
