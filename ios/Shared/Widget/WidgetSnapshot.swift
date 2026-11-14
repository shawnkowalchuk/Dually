import Foundation

/// What the home-screen widget shows (phase 4i), left by the app in the App Group it shares with
/// its extension: the app works the figures out with the shared code and the widget draws them.
struct WidgetSnapshot: Codable {
    /// A trip is being recorded.
    var recording: Bool
    /// When it started: the widget counts the time up by itself.
    var startedAt: Date?
    /// Its distance so far, "12.3", and the unit's word.
    var distance: String?
    var unit: String
    /// "Nov $12 · 2026 $340 at $0.70/km", or nil when the widget is switched off in Settings.
    var dollars: String?

    static let empty = WidgetSnapshot(recording: false, startedAt: nil, distance: nil, unit: "km", dollars: nil)

    private static let group = "group.top.dually.triptracker"
    private static let key = "widget_snapshot"

    static func read() -> WidgetSnapshot {
        guard let data = UserDefaults(suiteName: group)?.data(forKey: key),
              let snapshot = try? JSONDecoder().decode(WidgetSnapshot.self, from: data) else { return .empty }
        return snapshot
    }

    func save() {
        guard let data = try? JSONEncoder().encode(self) else { return }
        UserDefaults(suiteName: Self.group)?.set(data, forKey: Self.key)
    }
}
