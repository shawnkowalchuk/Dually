import DuallyShared
import SwiftUI

/// The look picked in Settings (2026-11-15 on the iPhone, as Android's Look tile since
/// 2026-11-12): Dark until Light is picked, and never the phone's own setting (Shawn's choice for
/// Android: "Light/Dark switch only"). The settings file holds it; the copy kept here draws the
/// first screen in it before the file is read at the start.
@MainActor
final class Look: ObservableObject {
    static let shared = Look()
    private static let key = "look_light"

    @Published var light: Bool {
        didSet { UserDefaults.standard.set(light, forKey: Self.key) }
    }

    var scheme: ColorScheme { light ? .light : .dark }

    private init() {
        light = UserDefaults.standard.bool(forKey: Self.key)
    }

    /// Takes the look the settings file holds, and redraws the widget when it changed.
    func follow(_ text: SettingsText) {
        guard text.light != light else { return }
        light = text.light
        Task { await TripSession.shared.updateWidget(force: true) }
    }
}
