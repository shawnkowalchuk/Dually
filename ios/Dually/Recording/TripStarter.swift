/// What the widget's Start button does in the app (WidgetStartTripIntent): Start trip, logged as
/// coming from the widget.
enum TripStarter {
    @MainActor
    static func start() async {
        await TripSession.shared.startFromWidget()
    }
}
