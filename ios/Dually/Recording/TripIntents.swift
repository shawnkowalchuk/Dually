import AppIntents

// What a Shortcuts automation runs when the iPhone connects to the vehicle's Bluetooth or to
// CarPlay, and when it disconnects (ADR-015, the first layer: no iPhone app can watch a Bluetooth
// connection itself). The user makes the two automations in the Shortcuts app. Each action runs
// in the background without opening Dually, and tells the trip rules what Android's Bluetooth
// events tell them: a new link, or the vehicle gone.

struct StartTripIntent: AppIntent {
    static let title: LocalizedStringResource = "Start a trip"
    static let description = IntentDescription(
        "Tells Dually your vehicle connected, for an automation that runs when your iPhone connects to its Bluetooth or to CarPlay. Dually starts recording a trip."
    )
    static let openAppWhenRun = false

    @MainActor
    func perform() async throws -> some IntentResult {
        await TripSession.shared.truckConnected()
        return .result()
    }
}

struct EndTripIntent: AppIntent {
    static let title: LocalizedStringResource = "End a trip"
    static let description = IntentDescription(
        "Tells Dually your vehicle disconnected, for an automation that runs when your iPhone disconnects from its Bluetooth or from CarPlay. The trip ends after a short wait, in case the vehicle connects again."
    )
    static let openAppWhenRun = false

    @MainActor
    func perform() async throws -> some IntentResult {
        await TripSession.shared.truckDisconnected()
        return .result()
    }
}
