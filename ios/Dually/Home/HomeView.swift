import DuallyShared
import SwiftUI

// Home on the iPhone (phase 4a): the top line, the Start trip card and the two figures Android's
// Home leads with, today's and this month's Business kilometres. Recording comes in phase 4b;
// until then the card says so and does nothing.
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
                StartTripCard()
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

/// Every screen's top line, as on Android: the mark, "Dually" and the date, then the screen.
private struct TopLine: View {
    let screen: String

    var body: some View {
        HStack(alignment: .center, spacing: 10) {
            DuallyMark(size: 34)
            VStack(alignment: .leading, spacing: 0) {
                Text("Dually")
                    .font(Typography.display(20))
                    .foregroundStyle(Palette.text)
                Text(Date.now.formatted(.dateTime.weekday(.abbreviated).month(.abbreviated).day()))
                    .font(Typography.body(13))
                    .foregroundStyle(Palette.textSecondary)
            }
            Spacer()
            Text(screen)
                .font(Typography.body(15, weight: .semibold))
                .foregroundStyle(Palette.text)
        }
        .padding(.vertical, 12)
    }
}

/// The orange card Android's Home starts with. It records nothing yet (phase 4b).
private struct StartTripCard: View {
    var body: some View {
        Tile(ground: Palette.accent) {
            Text("Start trip")
                .font(Typography.display(26))
                .foregroundStyle(Palette.markLetter)
            Text("Recording comes in the next step of the iPhone app")
                .font(Typography.body(14))
                .foregroundStyle(Palette.markLetter)
        }
        .accessibilityElement(children: .combine)
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
