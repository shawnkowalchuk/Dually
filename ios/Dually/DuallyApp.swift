import DuallyShared
import SwiftUI

// Dually on the iPhone (ADR-014): SwiftUI screens over the shared Kotlin code, which Xcode has
// Gradle build as the DuallyShared framework (the "Build the shared Kotlin code" step).
@main
struct DuallyApp: App {
    /// The trip being recorded and the shared code's door, one for the whole app (phase 4d).
    private let session = TripSession.shared

    var body: some Scene {
        WindowGroup {
            TabView {
                HomeView(session: session)
                    .tabItem { Label("Home", systemImage: "house.fill") }
                TripsView(core: session.core)
                    .tabItem { Label("Trips", systemImage: "list.bullet") }
            }
            .tint(Palette.accent)
            .preferredColorScheme(.dark)
            .task { await session.appStarted() }
        }
    }
}
