import AppIntents

// What a Shortcuts automation runs when the iPhone connects to the vehicle's Bluetooth or to
// CarPlay, and when it disconnects (ADR-015, the first layer: no iPhone app can watch a Bluetooth
// connection itself). The user makes the two automations in the Shortcuts app. Each action runs
// in the background without opening Dually, and tells the trip rules what Android's Bluetooth
// events tell them: a new link, or the vehicle gone.
//
// Apple refuses a build whose App Intent title or description names its devices: build 1 was
// rejected (ITMS-90626) for "iPhone" in these two descriptions. The build step "Check the
// Shortcuts actions' words" stops a build that says it again.

struct StartTripIntent: AppIntent {
    static let title: LocalizedStringResource = "Start a trip"
    static let description = IntentDescription(
        "Tells Dually your vehicle connected, for an automation that runs when your phone connects to its Bluetooth or to CarPlay. Dually starts recording a trip."
    )
    static let openAppWhenRun = false

    @MainActor
    func perform() async throws -> some IntentResult {
        await TripSession.shared.truckConnected()
        return .result()
    }
}

// From iOS 17 the two actions are Live Activity intents too: one an automation runs may start or
// end the trip's Live Activity (phase 4e) with Dually in the background.
@available(iOS 17.0, *)
extension StartTripIntent: LiveActivityIntent {}

@available(iOS 17.0, *)
extension EndTripIntent: LiveActivityIntent {}

struct EndTripIntent: AppIntent {
    static let title: LocalizedStringResource = "End a trip"
    static let description = IntentDescription(
        "Tells Dually your vehicle disconnected, for an automation that runs when your phone disconnects from its Bluetooth or from CarPlay. The trip ends after a short wait, in case the vehicle connects again."
    )
    static let openAppWhenRun = false

    @MainActor
    func perform() async throws -> some IntentResult {
        await TripSession.shared.truckDisconnected()
        return .result()
    }
}
