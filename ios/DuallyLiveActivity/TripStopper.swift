/// The widget extension's half of StopTripIntent. iOS runs a Live Activity's intent in the app's
/// process (TripStopper.swift in the app ends the trip), so this one never runs; it is here only so
/// the extension, which draws the button, compiles.
enum TripStopper {
    static func stop() async {}
}
