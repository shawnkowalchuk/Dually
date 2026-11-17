import DuallyShared
import GoogleSignIn
import SwiftUI

// Dually on the iPhone (ADR-014): SwiftUI screens over the shared Kotlin code, which Xcode has
// Gradle build as the DuallyShared framework (the "Build the shared Kotlin code" step).
@main
struct DuallyApp: App {
    /// The trip being recorded and the shared code's door, one for the whole app (phase 4d).
    private let session = TripSession.shared
    @StateObject private var setup = SetupModel(session: TripSession.shared)
    /// The service book, for Service and for Home's tiles of what is due.
    @StateObject private var service = ServiceModel(core: TripSession.shared.core)
    /// Dark or light, as picked in Settings.
    @ObservedObject private var look = Look.shared
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
                case .home:
                    HomeView(
                        session: session,
                        setup: setup,
                        service: service,
                        openSetup: { openSetup(from: .home) },
                        openTrips: { tab = .trips },
                        openReport: { year, month in
                            reportMonth = (year, month)
                            tab = .report
                        }
                    )
                case .trips:
                    TripsView(
                        core: session.core,
                        openReport: { year, month in
                            reportMonth = (year, month)
                            tab = .report
                        },
                        openGraph: { tab = .graph }
                    )
                case .graph: TripGraphView(core: session.core) { tab = .trips }
                case .report:
                    ReportView(
                        core: session.core,
                        year: reportMonth.year,
                        month: reportMonth.month,
                        back: { tab = .trips },
                        openSettings: { tab = .settings }
                    )
                case .settings:
                    SettingsView(
                        core: session.core,
                        setup: setup,
                        openSetup: { openSetup(from: .settings) },
                        openLog: { tab = .log }
                    )
                case .log: LogView(core: session.core) { tab = .settings }
                case .setup: SetupView(model: setup) { tab = setupOpenedFrom }
                case .service: ServiceView(model: service)
                }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                BottomBar(entries: [
                    entry(.home, "Home", "house.fill"),
                    entry(.trips, "Trips", "list.bullet"),
                    entry(.settings, "Settings", "slider.horizontal.3"),
                    entry(.service, "Service", "wrench.and.screwdriver.fill"),
                ])
                .background(Palette.page.ignoresSafeArea())
            }
            .tint(Palette.accent)
            .preferredColorScheme(look.scheme)
            .task {
                // The online backup follows every change, and StoreKit's late purchases reach
                // the purchase check, in a build with Firebase's settings (phase 4h).
                if FirebaseCloud.shared.available {
                    session.core.cloud.watch()
                    BackupPurchase.listen(core: session.core)
                }
                // The look the settings file holds, in case it changed while the app was away
                // (an import brings the look of the file).
                if let text = try? await session.core.settings.read() { look.follow(text) }
                await session.appStarted()
                await Reminders.plan(core: session.core)
            }
            // Google's sign-in comes back to Dually through its web address.
            .onOpenURL { GIDSignIn.sharedInstance.handle($0) }
            // A tap on the monthly reminder opens that month's report, as on Android.
            .onReceive(session.$reportAsked) { asked in
                if let asked {
                    reportMonth = asked
                    tab = .report
                    session.reportAsked = nil
                }
            }
            // A tap on the service reminder opens Service, as on Android.
            .onReceive(session.$serviceAsked) { asked in
                if asked {
                    tab = .service
                    session.serviceAsked = false
                }
            }
        }
    }
}

extension DuallyApp {
    private func entry(_ which: AppTab, _ label: String, _ systemImage: String) -> BottomBarEntry {
        let lit = tab == which || (tab == .setup && setupOpenedFrom == which) || (tab == .report && which == .trips)
            || (tab == .graph && which == .trips)
            || (tab == .log && which == .settings)
        return BottomBarEntry(label: label, systemImage: systemImage, selected: lit) { tab = which }
    }

    private func openSetup(from origin: AppTab) {
        setupOpenedFrom = origin
        tab = .setup
    }
}

/// The screens: the bar's four, Setup (opened from Settings or Home), the report and the graph
/// (from Trips) and the Log (from Settings).
enum AppTab: Hashable {
    case home, trips, settings, service, setup, report, log, graph
}
