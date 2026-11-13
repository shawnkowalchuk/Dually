import DuallyShared
import SwiftUI

// Home on the iPhone: the top line, the trip card and the two figures Android's Home leads with,
// today's and this month's Business kilometres (phase 4a). The trip card starts a trip, shows it
// while it records, and ends it (phase 4b).
struct HomeView: View {
    @StateObject private var model: HomeModel
    @Environment(\.scenePhase) private var scenePhase

    init(core: DuallyCore) {
        _model = StateObject(wrappedValue: HomeModel(core: core))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                TopLine(screen: "Home")
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
                        note: model.figures.map { tripCount(Int($0.todayTrips)) }
                    )
                    FigureTile(
                        label: model.figures?.monthName ?? " ",
                        value: model.figures?.monthBusiness,
                        note: "Business"
                    )
                }
                if let failure = model.failure {
                    Text(failure)
                        .font(Typography.body(14))
                        .foregroundStyle(Palette.amber)
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 24)
        }
        .background(Palette.page.ignoresSafeArea())
        .task { await model.refresh() }
        .onChange(of: scenePhase) { phase in
            if phase == .active { Task { await model.refresh() } }
        }
    }

    private func tripCount(_ count: Int) -> String {
        count == 1 ? "1 trip" : "\(count) trips"
    }
}

/// The orange card: Start trip while nothing records, the running trip and End while one does.
private struct TripCard: View {
    @ObservedObject var model: HomeModel

    var body: some View {
        if let trip = model.recording {
            RecordingCard(trip: trip, distance: model.distanceText(trip)) {
                Task { await model.end() }
            }
        } else {
            Button {
                Task { await model.start() }
            } label: {
                Tile(ground: Palette.accent) {
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
                            .foregroundStyle(Palette.accent)
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
    let onEnd: () -> Void

    var body: some View {
        Tile(ground: Palette.accent) {
            Text("Recording")
                .font(Typography.body(14, weight: .semibold))
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text(distance)
                    .font(Typography.figure(40))
                Text("km")
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
                    .foregroundStyle(Palette.text)
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

/// A figure in kilometres, with its label over it and a quieter line under it.
private struct FigureTile: View {
    let label: String
    let value: String?
    let note: String?

    var body: some View {
        Tile {
            Text(label)
                .font(Typography.body(13))
                .foregroundStyle(Palette.textSecondary)
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text(value ?? "–")
                    .font(Typography.figure(28))
                    .foregroundStyle(Palette.text)
                Text("km")
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
