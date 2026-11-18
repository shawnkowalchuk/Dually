/// What the Live Activity's End trip button does in the app (StopTripIntent): End trip, as on
/// Home, logged as coming from the Live Activity.
enum TripStopper {
    @MainActor
    static func stop() async {
        await TripSession.shared.endFromLiveActivity()
    }
}
