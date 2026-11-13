import DuallyShared
import SwiftUI

// Dually on the iPhone (ADR-014): SwiftUI screens over the shared Kotlin code, which Xcode has
// Gradle build as the DuallyShared framework (the "Build the shared Kotlin code" step).
@main
struct DuallyApp: App {
    /// The shared code's door, opened once: it opens the database.
    private let core = DuallyCore.companion.open(placeFinder: AppleGeocoder())

    var body: some Scene {
        WindowGroup {
            TabView {
                HomeView(core: core)
                    .tabItem { Label("Home", systemImage: "house.fill") }
                TripsView(core: core)
                    .tabItem { Label("Trips", systemImage: "list.bullet") }
            }
            .tint(Palette.accent)
            .preferredColorScheme(.dark)
            .task { try? await core.appStarted() }
        }
    }
}
