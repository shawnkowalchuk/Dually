import AppIntents

/// End trip on the Live Activity (phase 4e): the same as End trip on Home, a trip ended by hand.
/// iOS runs a Live Activity's intent in the app's own process, which ends the trip there
/// (TripStopper.swift in the app); the widget extension only has to know the intent exists. Not
/// offered in the Shortcuts app: it is the Live Activity's button, nothing more.
@available(iOS 17.0, *)
struct StopTripIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "End the trip"
    static let description = IntentDescription("Ends the trip being recorded, as End trip does in Dually.")
    static let isDiscoverable = false

    func perform() async throws -> some IntentResult {
        await TripStopper.stop()
        return .result()
    }
}
