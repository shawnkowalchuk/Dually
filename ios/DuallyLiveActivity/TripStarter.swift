/// The extension's half of WidgetStartTripIntent, which iOS runs in the app's process; this one
/// never runs and is here so the widget, which draws the button, compiles.
enum TripStarter {
    static func start() async {}
}
