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
    /// How far the first start has got ("INTRO", "SETUP", "DONE"), or nil until it is read
    /// (2026-11-16, as on Android).
    @State private var firstStart: String?
    /// Where What's new was opened from: its way back leads there.
    @State private var whatsNewFrom = AppTab.home
    /// The greeting playing over Home, with its pictures read (2026-11-16, as on Android).
    @State private var greeting: GreetingPictures?
    /// The one-year party playing over Home (2026-11-16, as on Android).
    @State private var party: PartyShowing?
    /// The party asked for by holding the version's heading on What's new.
    @State private var partyAsked = false
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            // Android's bottom bar, not iOS's tab bar (Shawn, 2026-11-13: "the app need to be more
            // matching the andorids interface"): one screen at a time over the floating bar.
            // As on Android since 2026-10-07: Settings is the bar's third button, and Setup opens
            // from Settings' first tile (and from Home's warning), with the way back in its top line.
            Group {
                if firstStart == nil {
                    // A moment, while the first start's stage is read.
                    Palette.page.ignoresSafeArea()
                } else if firstStart == "INTRO" {
                    // The welcome page of a fresh install, before any other screen and without
                    // the bar, as on Android: its OK opens Setup with its Done button.
                    WelcomeView {
                        firstStart = "SETUP"
                        Task { try? await session.core.whatsNew.welcomeDone() }
                        openSetup(from: .home)
                    }
                } else {
                    screen
                        .overlay(alignment: .bottomLeading) { greetingOverHome }
                        .overlay { partyOverHome }
                }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                if firstStart != nil && firstStart != "INTRO" {
                    BottomBar(entries: [
                        entry(.home, "Home", "house.fill"),
                        entry(.trips, "Trips", "list.bullet"),
                        entry(.settings, "Settings", "slider.horizontal.3"),
                        entry(.service, "Service", "wrench.and.screwdriver.fill"),
                    ])
                    .background(Palette.page.ignoresSafeArea())
                }
            }
            .tint(Palette.accent)
            .preferredColorScheme(look.scheme)
            .task {
                // The welcome page on a fresh install, before anything else; What's new once
                // after an update, as on Android.
                let stage = (try? await session.core.whatsNew.stage(installed: InstalledVersion.name)) ?? "DONE"
                firstStart = stage
                if stage == "DONE",
                   (try? await session.core.whatsNew.due(installed: InstalledVersion.name))?.boolValue == true {
                    whatsNewFrom = .home
                    tab = .whatsNew
                }
                await checkParty()
                if party == nil { await greetIfDue(firstStart: stage) }
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
            // A greeting or a party is cut, not faded, when Home is left or Dually is put away.
            .onChange(of: tab) { now in
                if now != .home {
                    greeting = nil
                    party = nil
                }
                homeWatching()
                if now == .home { Task { await checkParty() } }
            }
            .onChange(of: scenePhase) { phase in
                if phase != .active {
                    greeting = nil
                    party = nil
                }
                homeWatching()
                if phase == .active { Task { await checkParty() } }
            }
            .onReceive(session.$partyDue) { due in
                guard due else { return }
                session.partyDue = false
                Task { await checkParty() }
            }
        }
    }

    /// The greeting over Home, its soles on the bottom bar's room.
    @ViewBuilder private var greetingOverHome: some View {
        if let greeting, tab == .home {
            GeometryReader { geometry in
                MascotGreetingView(player: GreetingPlayer(pictures: greeting, screenWidth: geometry.size.width) {
                    self.greeting = nil
                })
                .frame(width: geometry.size.width, height: geometry.size.height, alignment: .bottomLeading)
            }
        }
    }

    /// The party over Home, in the middle of the screen.
    @ViewBuilder private var partyOverHome: some View {
        if let party, let pictures = party.pictures, tab == .home {
            GeometryReader { geometry in
                MascotPartyView(
                    player: PartyPlayer(pictures: pictures, onPop: { partyPopped(party) }) { popped in
                        partyOver(party, popped: popped)
                    },
                    screen: geometry.size
                )
            }
        }
    }

    /// Whether Home could play the party now: the trip's start leaves its pop to it then.
    private func homeWatching() {
        session.core.party.homeWatching = tab == .home && scenePhase == .active && firstStart == "DONE"
    }

    /// The party owed by today's trips, or asked for, when Home is in front; it takes the
    /// greeting's place, as on Android. Not where the iPhone asks for less motion: a pop the
    /// party was left is then played as it would have been at its end.
    private func checkParty() async {
        homeWatching()
        guard party == nil, tab == .home, scenePhase == .active || firstStart == "DONE", firstStart == "DONE" else { return }
        let core = session.core
        var showing: PartyShowing?
        if let forMs = try? await core.party.owedNow() {
            let owesPop = (try? await core.party.shown(forMs: forMs.int64Value))?.boolValue ?? false
            showing = PartyShowing(pictures: nil, onDemand: false, owesPop: owesPop)
        } else if partyAsked {
            partyAsked = false
            showing = PartyShowing(pictures: nil, onDemand: true, owesPop: false)
        }
        guard var showing else { return }
        greeting = nil
        guard !UIAccessibility.isReduceMotionEnabled, let pictures = PartyPictures() else {
            if !UIAccessibility.isReduceMotionEnabled { try? await core.party.unreadable() }
            partyOver(showing, popped: false)
            return
        }
        showing.pictures = pictures
        party = showing
    }

    private func partyPopped(_ showing: PartyShowing) {
        party?.owesPop = false
        let core = session.core
        Task {
            let heard = (try? await core.party.popHeard())?.boolValue ?? true
            if showing.onDemand || heard { TripSoundPlayer.shared.play([core.sounds.partyPop()], core: core) }
        }
    }

    private func partyOver(_ showing: PartyShowing, popped: Bool) {
        let owed = party?.owesPop ?? showing.owesPop
        party = nil
        guard owed, !popped else { return }
        let core = session.core
        Task {
            if (try? await core.party.popHeard())?.boolValue == true {
                TripSoundPlayer.shared.play([core.sounds.partyPop()], core: core)
            }
        }
    }

    /// One greeting for a start from nothing onto Home, as on Android: not during the first
    /// start, not over What's new or a screen a notification's tap opened, and not where the
    /// iPhone asks for less motion. The wave and the moonwalk take turns; a moonwalk whose
    /// pictures cannot be read gives way to the wave.
    private func greetIfDue(firstStart stage: String) async {
        guard stage == "DONE", tab == .home, session.reportAsked == nil, !session.serviceAsked,
              !UIAccessibility.isReduceMotionEnabled,
              let kind = try? await session.core.mascot.due()
        else { return }
        var pictures = GreetingPictures(kind)
        if pictures == nil {
            try? await session.core.mascot.unreadable(kind: kind, why: "The picture could not be read from the app")
            if kind == .moonwalk { pictures = GreetingPictures(.wave) }
        }
        guard let pictures, tab == .home else { return }
        greeting = pictures
        try? await session.core.mascot.shown()
    }

    /// The screen the bar's choice opened, once the welcome page is behind.
    @ViewBuilder private var screen: some View {
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
                openLog: { tab = .log },
                openWhatsNew: {
                    whatsNewFrom = .settings
                    tab = .whatsNew
                }
            )
        case .log: LogView(core: session.core) { tab = .settings }
        case .setup:
            // During the first start, Setup has its Done button, as on Android.
            SetupView(
                model: setup,
                back: { tab = setupOpenedFrom },
                firstStart: firstStart == "SETUP",
                done: {
                    firstStart = "DONE"
                    Task { try? await session.core.whatsNew.setupDone() }
                    tab = .home
                }
            )
        case .service: ServiceView(model: service)
        case .whatsNew:
            WhatsNewView(core: session.core, back: { tab = whatsNewFrom }) {
                // Held: the party plays when Home is next in front, as on Android.
                partyAsked = true
            }
        }
    }
}

extension DuallyApp {
    private func entry(_ which: AppTab, _ label: String, _ systemImage: String) -> BottomBarEntry {
        // A screen opened from another keeps that one's button lit.
        let openedFrom: AppTab? = switch tab {
        case .setup: setupOpenedFrom
        case .report, .graph: .trips
        case .log: .settings
        case .whatsNew: whatsNewFrom
        default: nil
        }
        let lit = tab == which || openedFrom == which
        return BottomBarEntry(label: label, systemImage: systemImage, selected: lit) { tab = which }
    }

    private func openSetup(from origin: AppTab) {
        setupOpenedFrom = origin
        tab = .setup
    }
}

/// A party over Home: its pictures, whether it was asked for, and whether the pop of a trip's
/// start was left to it.
struct PartyShowing {
    var pictures: PartyPictures?
    let onDemand: Bool
    var owesPop: Bool
}

/// The screens: the bar's four, Setup (opened from Settings or Home), the report and the graph
/// (from Trips), the Log and What's new (from Settings; What's new also by itself after an
/// update).
enum AppTab: Hashable {
    case home, trips, settings, service, setup, report, log, graph, whatsNew
}
