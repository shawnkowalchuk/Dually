import DuallyShared
import SwiftUI

// Dually on the iPhone (ADR-014): SwiftUI screens over the shared Kotlin code, which Xcode has
// Gradle build as the DuallyShared framework (the "Build the shared Kotlin code" step).
@main
struct DuallyApp: App {
    /// The trip being recorded and the shared code's door, one for the whole app (phase 4d).
    private let session = TripSession.shared
    @StateObject private var setup = SetupModel(session: TripSession.shared)
    @State private var tab = AppTab.home

    var body: some Scene {
        WindowGroup {
            // Android's bottom bar, not iOS's tab bar (Shawn, 2026-11-13: "the app need to be more
            // matching the andorids interface"): one screen at a time over the floating bar.
            Group {
                switch tab {
                case .home: HomeView(session: session, setup: setup) { tab = .setup }
                case .trips: TripsView(core: session.core)
                case .setup: SetupView(model: setup)
                }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                BottomBar(entries: [
                    entry(.home, "Home", "house.fill"),
                    entry(.trips, "Trips", "list.bullet"),
                    entry(.setup, "Setup", "checklist"),
                ])
                .background(Palette.page.ignoresSafeArea())
            }
            .tint(Palette.accent)
            .preferredColorScheme(.dark)
            .task { await session.appStarted() }
        }
    }
}

extension DuallyApp {
    private func entry(_ which: AppTab, _ label: String, _ systemImage: String) -> BottomBarEntry {
        BottomBarEntry(label: label, systemImage: systemImage, selected: tab == which) { tab = which }
    }
}

/// The tabs at the foot. Setup is one of them until the iPhone has its Settings (phase 4g),
/// where Android opens it from.
enum AppTab: Hashable {
    case home, trips, setup
}
