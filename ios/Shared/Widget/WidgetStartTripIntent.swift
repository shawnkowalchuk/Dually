import AppIntents

/// Start on the home-screen widget (phase 4i): a trip started by hand, as on Android's widget.
/// iOS runs it in the app's process (a Live Activity intent), where TripStarter starts the trip;
/// the extension's TripStarter is a stand-in that never runs. Not offered in Shortcuts.
@available(iOS 17.0, *)
struct WidgetStartTripIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Start trip"
    static let description = IntentDescription("Starts a trip, as Start trip does in Dually.")
    static let isDiscoverable = false

    func perform() async throws -> some IntentResult {
        await TripStarter.start()
        return .result()
    }
}
