import DuallyShared
import SwiftUI

// Home on the iPhone: the top line, the trip card and the two figures Android's Home leads with,
// today's and this month's Business kilometres (phase 4a). The trip card starts a trip, shows it
// while it records, and ends it (phase 4b).
struct HomeView: View {
    @StateObject private var model: HomeModel
    @ObservedObject var setup: SetupModel
    @ObservedObject var service: ServiceModel
    let openSetup: () -> Void
    let openTrips: () -> Void
    let openReport: (Int, Int) -> Void
    @Environment(\.scenePhase) private var scenePhase
    @State private var finishing: AlertText?

    init(
        session: TripSession,
        setup: SetupModel,
        service: ServiceModel,
        openSetup: @escaping () -> Void,
        openTrips: @escaping () -> Void,
        openReport: @escaping (Int, Int) -> Void
    ) {
        _model = StateObject(wrappedValue: HomeModel(session: session))
        self.setup = setup
        self.service = service
        self.openSetup = openSetup
        self.openTrips = openTrips
        self.openReport = openReport
    }

    var body: some View {
        PinnedScreen(title: nil) {
            if let screen = setup.screen, !screen.complete {
                SetupWarning(screen: screen, open: openSetup)
            }
            TripCard(model: model)
            if model.location.authorization == .denied || model.location.authorization == .restricted {
                Text("Dually may not use the location. Allow it in the iPhone's Settings, Dually, Location, to record a trip.")
                    .font(Typography.body(14))
                    .foregroundStyle(Palette.amber)
            }
            HStack(spacing: 12) {
                FigureTile(
                    label: "Today · Business",
                    value: model.figures?.todayBusiness,
                    note: model.figures.map { tripCount(Int($0.todayTrips)) },
                    unit: model.unitWord
                )
                FigureTile(
                    label: model.figures?.monthName ?? " ",
                    value: model.figures?.monthBusiness,
                    note: "Business",
                    unit: model.unitWord
                )
            }
            // Last month's report while the monthly reminder says it waits, as Android's amber
            // tile, with Send.
            if let waiting = model.reportWaiting, model.recording == nil {
                ReportWaitingTile(title: waiting.homeTitle) {
                    openReport(Int(waiting.year), Int(waiting.month))
                }
            }
            // The truck, as Android's tile under the figures: what the last automation said.
            if let truck = model.truck {
                TruckTile(truck: truck)
            }
            if let odometer = model.odometer {
                OdometerTile(odometer: odometer)
            }
            // What service is soon or due, as Android's Home tiles: not while a trip records,
            // when there is nothing to read while driving.
            if model.recording == nil {
                ForEach(service.text?.alerts ?? [], id: \.id) { alert in
                    ServiceAlertTile(alert: alert) { finishing = alert }
                }
            }
            TodayTile(today: model.today, recording: model.recording != nil, openTrips: openTrips)
            if let failure = model.failure ?? model.recordingFailure {
                Text(failure)
                    .font(Typography.body(14))
                    .foregroundStyle(Palette.amber)
            }
        }
        .task {
            setup.refresh()
            await model.refresh()
            await service.load()
        }
        .onChange(of: scenePhase) { phase in
            if phase == .active {
                setup.refresh()
                Task {
                    await model.cameToFront()
                    await service.load()
                }
            }
        }
        .onChange(of: model.recording == nil) { _ in Task { await service.load() } }
        .confirmationDialog(
            finishing?.doneTitle ?? "",
            isPresented: Binding(get: { finishing != nil }, set: { if !$0 { finishing = nil } }),
            titleVisibility: .visible,
            presenting: finishing
        ) { alert in
            Button(serviceWords("home_service_done")) {
                Task { await service.change { try await service.core.service.doneNow(id: alert.id) } }
            }
            Button(serviceWords("action_cancel"), role: .cancel) {}
        } message: { _ in
            Text(serviceWords("home_service_done_text"))
        }
    }

    private func tripCount(_ count: Int) -> String {
        count == 1 ? "1 trip" : "\(count) trips"
    }
}

/// One service item that is soon or due: its name, its line, and Done, as Android's tile.
private struct ServiceAlertTile: View {
    let alert: AlertText
    let done: () -> Void

    var body: some View {
        Tile {
            HStack(alignment: .top, spacing: 8) {
                Image(systemName: "wrench.and.screwdriver.fill")
                    .foregroundStyle(Palette.amber)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 4) {
                    Text(alert.name)
                        .font(Typography.body(17, weight: .semibold))
                        .foregroundStyle(Palette.text)
                    Text(alert.line)
                        .font(Typography.body(15))
                        .foregroundStyle(Palette.amber)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            WideButton(title: serviceWords("home_service_done"), filled: true, action: done)
        }
    }
}

/// The orange card: Start trip while nothing records, the running trip and End while one does.
private struct TripCard: View {
    @ObservedObject var model: HomeModel

    var body: some View {
        if let trip = model.recording {
            RecordingCard(trip: trip, distance: model.distanceText(trip), unit: model.unitWord) {
                Task { await model.end() }
            }
        } else {
            Button {
                Task { await model.start() }
            } label: {
                Tile(ground: Palette.accentTile) {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Start trip")
                                .font(Typography.display(26))
                            Text("Recorded with GPS, even with the app in the background")
                                .font(Typography.body(14))
                        }
                        Spacer()
                        Image(systemName: "play.fill")
                            .font(.system(size: 20, weight: .bold))
                            .frame(width: 48, height: 48)
                            .background(Palette.markLetter, in: Circle())
                            .foregroundStyle(Palette.accentTile)
                    }
                    .foregroundStyle(Palette.markLetter)
                }
            }
            .buttonStyle(.plain)
        }
    }
}

/// The trip as it records: how far, how long, and End.
private struct RecordingCard: View {
    let trip: RecordingNow
    let distance: String
    let unit: String
    let onEnd: () -> Void

    var body: some View {
        Tile(ground: Palette.accentTile) {
            Text("Recording")
                .font(Typography.body(14, weight: .semibold))
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text(distance)
                    .font(Typography.figure(40))
                Text(unit)
                    .font(Typography.body(16))
            }
            TimelineView(.periodic(from: .now, by: 1)) { context in
                Text(elapsed(until: context.date))
                    .font(Typography.figure(16))
            }
            Button(action: onEnd) {
                Text("End trip")
                    .font(Typography.body(17, weight: .bold))
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .background(Palette.markLetter, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .foregroundStyle(Palette.bone)
            }
            .buttonStyle(.plain)
            .padding(.top, 6)
        }
        .foregroundStyle(Palette.markLetter)
    }

    /// The time since the trip started, "0:12:05".
    private func elapsed(until now: Date) -> String {
        let seconds = max(0, Int(now.timeIntervalSince1970) - Int(trip.startedAtMs / 1000))
        return String(format: "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    }
}

/// Android's amber tile for last month's report: "October report", "Not sent to the accountant
/// yet", and Send, which opens that report.
private struct ReportWaitingTile: View {
    let title: String
    let send: () -> Void

    var body: some View {
        Tile {
            HStack(alignment: .center, spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(Typography.body(17, weight: .semibold))
                        .foregroundStyle(Palette.amber)
                    Text("Not sent to the accountant yet")
                        .font(Typography.body(14))
                        .foregroundStyle(Palette.text)
                }
                Spacer()
                Button("Send", action: send)
                    .font(Typography.body(16, weight: .bold))
                    .padding(.horizontal, 18)
                    .frame(minHeight: 40)
                    .background(Palette.accent, in: Capsule())
                    .foregroundStyle(Palette.onAccent)
            }
        }
    }
}

/// The truck's tile, as Android's under Home's figures: "Truck", the state in Android's words
/// ("Connected", "Not connected", "Truck connected and parked"), its sentence, and when the
/// Shortcuts automation said so, since the iPhone cannot read the connection itself.
private struct TruckTile: View {
    let truck: TruckTileText

    var body: some View {
        Tile {
            Text("Truck")
                .font(Typography.body(13))
                .foregroundStyle(Palette.textSecondary)
            HStack(spacing: 8) {
                Circle()
                    .fill(truck.connected ? Palette.accent : Color.clear)
                    .overlay(Circle().stroke(truck.connected ? Palette.accent : Palette.textSecondary, lineWidth: 2))
                    .frame(width: 10, height: 10)
                Text(truck.title)
                    .font(Typography.body(18, weight: .semibold))
                    .foregroundStyle(Palette.text)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Text(truck.text)
                .font(Typography.body(14))
                .foregroundStyle(Palette.text)
                .fixedSize(horizontal: false, vertical: true)
            Text(truck.heard)
                .font(Typography.body(13))
                .foregroundStyle(Palette.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .accessibilityElement(children: .combine)
    }
}

/// Today's finished trips, newest first, as Android's tile at the foot of Home: "Today" or
/// "Today · 3 trips · 1 h 5 min driving", Trips in its corner, and a row for each trip with its
/// times, what it is saved as, where it went and its distance. A trip that is not Business has
/// its figure greyed, as on Android: the figures in full colour add up to the Business above.
private struct TodayTile: View {
    let today: TodayText?
    let recording: Bool
    let openTrips: () -> Void

    var body: some View {
        Tile {
            HStack(alignment: .firstTextBaseline) {
                Text(today?.title ?? "Today")
                    .font(Typography.body(13))
                    .foregroundStyle(Palette.textSecondary)
                Spacer()
                Button("Trips", action: openTrips)
                    .font(Typography.body(14, weight: .semibold))
                    .foregroundStyle(Palette.accent)
            }
            if let today, today.trips.isEmpty {
                Text("No finished trips yet today.")
                    .font(Typography.body(14))
                    .foregroundStyle(Palette.text)
            }
            ForEach(Array((today?.trips ?? []).enumerated()), id: \.element.tripId) { index, trip in
                if index > 0 { Divider().overlay(Palette.controlFill) }
                TodayRow(trip: trip)
            }
            if recording {
                Text("The trip in progress is added when it ends.")
                    .font(Typography.body(13))
                    .foregroundStyle(Palette.textSecondary)
                    .padding(.top, 4)
            }
        }
    }
}

private struct TodayRow: View {
    let trip: TripText

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text([trip.time, trip.category].compactMap { $0 }.joined(separator: " · "))
                    .font(Typography.body(14, weight: .semibold))
                    .foregroundStyle(Palette.text)
                if let route = trip.route {
                    Text(route)
                        .font(Typography.body(13))
                        .foregroundStyle(Palette.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                if let label = trip.label {
                    Text(label)
                        .font(Typography.body(13))
                        .foregroundStyle(Palette.textSecondary)
                }
            }
            Spacer(minLength: 0)
            Text(trip.distance ?? "–")
                .font(Typography.figure(20))
                .foregroundStyle(trip.category == "Business" ? Palette.text : Palette.textSecondary)
        }
        .padding(.vertical, 6)
        .accessibilityElement(children: .combine)
    }
}

/// A figure in kilometres, with its label over it and a quieter line under it.
private struct FigureTile: View {
    let label: String
    let value: String?
    let note: String?
    let unit: String

    var body: some View {
        Tile {
            Text(label)
                .font(Typography.body(13))
                .foregroundStyle(Palette.textSecondary)
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text(value ?? "–")
                    .font(Typography.figure(28))
                    .foregroundStyle(Palette.text)
                Text(unit)
                    .font(Typography.body(14))
                    .foregroundStyle(Palette.textSecondary)
            }
            Text(note ?? " ")
                .font(Typography.body(13))
                .foregroundStyle(Palette.textSecondary)
        }
        .accessibilityElement(children: .combine)
    }
}

/// The odometer, as Android's Home tile: the wheels standing on the figure Settings shows, and
/// while a trip with the truck in it is driven, a dot that pulses and the last wheel turning.
/// Nothing to press: a reading is typed in Settings, and the tile says so before the first.
private struct OdometerTile: View {
    let odometer: HomeOdometerText

    var body: some View {
        Tile {
            HStack(spacing: 8) {
                if odometer.counting { LiveDot() }
                Text(odometer.title)
                    .font(Typography.body(13))
                    .foregroundStyle(Palette.textSecondary)
            }
            if odometer.hasReading {
                DashboardWheels(
                    whole: odometer.whole,
                    turn: odometer.counting ? odometer.turn : nil,
                    unit: odometer.unit,
                    spoken: odometer.spoken
                )
                .padding(.vertical, 4)
            }
            Text(odometer.caption)
                .font(Typography.body(13))
                .foregroundStyle(Palette.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

/// A dot in the accent that pulses while something is live, as Android's LiveDot.
private struct LiveDot: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var dim = false

    var body: some View {
        Circle()
            .fill(Palette.accent)
            .frame(width: 8, height: 8)
            .opacity(dim ? 0.35 : 1)
            .accessibilityHidden(true)
            .onAppear {
                guard !reduceMotion else { return }
                withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) { dim = true }
            }
    }
}

/// Home's warning while Setup is not complete, as Android's: trips will not start by themselves.
private struct SetupWarning: View {
    let screen: SetupScreen
    let open: () -> Void

    var body: some View {
        Tile {
            Text("Setup: \(screen.ready) of \(screen.total) ready")
                .font(Typography.body(17, weight: .semibold))
                .foregroundStyle(Palette.amber)
            Text("Until it is finished, trips don't start by themselves: only with Start trip.")
                .font(Typography.body(15))
                .foregroundStyle(Palette.textSecondary)
            WideButton(title: "Open Setup", filled: false, action: open)
                .padding(.top, 6)
        }
    }
}
