import DuallyShared
import SwiftUI

// Dually on the iPhone (ADR-014): SwiftUI screens over the shared Kotlin code, which Xcode has
// Gradle build as the DuallyShared framework (the "Build the shared Kotlin code" step).
@main
struct DuallyApp: App {
    /// The shared code's door, opened once: it opens the database.
    private let core = DuallyCore.companion.open()

    var body: some Scene {
        WindowGroup {
            HomeView(core: core)
                .preferredColorScheme(.dark)
        }
    }
}
