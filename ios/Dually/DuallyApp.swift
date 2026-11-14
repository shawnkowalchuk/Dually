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
    /// Where Setup was opened from: its way back leads there, and the bar keeps it lit.
    @State private var setupOpenedFrom = AppTab.settings
    /// The month the report was opened for, from Trips.
    @State private var reportMonth = (year: 2026, month: 1)

    var body: some Scene {
        WindowGroup {
            // Android's bottom bar, not iOS's tab bar (Shawn, 2026-11-13: "the app need to be more
            // matching the andorids interface"): one screen at a time over the floating bar.
            // As on Android since 2026-10-07: Settings is the bar's third button, and Setup opens
            // from Settings' first tile (and from Home's warning), with the way back in its top line.
            Group {
                switch tab {
                case .home: HomeView(session: session, setup: setup) { openSetup(from: .home) }
                case .trips:
                    TripsView(core: session.core) { year, month in
                        reportMonth = (year, month)
                        tab = .report
                    }
                case .report:
                    ReportView(
                        core: session.core,
                        year: reportMonth.year,
                        month: reportMonth.month,
                        back: { tab = .trips },
                        openSettings: { tab = .settings }
                    )
                case .settings:
                    SettingsView(core: session.core, setup: setup) { openSetup(from: .settings) }
                case .setup: SetupView(model: setup) { tab = setupOpenedFrom }
                }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                BottomBar(entries: [
                    entry(.home, "Home", "house.fill"),
                    entry(.trips, "Trips", "list.bullet"),
                    entry(.settings, "Settings", "slider.horizontal.3"),
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
        let lit = tab == which || (tab == .setup && setupOpenedFrom == which) || (tab == .report && which == .trips)
        return BottomBarEntry(label: label, systemImage: systemImage, selected: lit) { tab = which }
    }

    private func openSetup(from origin: AppTab) {
        setupOpenedFrom = origin
        tab = .setup
    }
}

/// The screens: the bar's three, Setup (opened from Settings or Home) and the report (from Trips).
enum AppTab: Hashable {
    case home, trips, settings, setup, report
}
