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
            TabView(selection: $tab) {
                HomeView(session: session, setup: setup) { tab = .setup }
                    .tabItem { Label("Home", systemImage: "house.fill") }
                    .tag(AppTab.home)
                TripsView(core: session.core)
                    .tabItem { Label("Trips", systemImage: "list.bullet") }
                    .tag(AppTab.trips)
                SetupView(model: setup)
                    .tabItem { Label("Setup", systemImage: "checklist") }
                    .tag(AppTab.setup)
            }
            .tint(Palette.accent)
            .preferredColorScheme(.dark)
            .task { await session.appStarted() }
        }
    }
}

/// The tabs at the foot. Setup is one of them until the iPhone has its Settings (phase 4g),
/// where Android opens it from.
enum AppTab: Hashable {
    case home, trips, setup
}
