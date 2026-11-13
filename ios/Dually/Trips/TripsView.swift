import DuallyShared
import SwiftUI

// The Trips screen on the iPhone (phase 4c): one month at a time, its Business and Personal
// totals, and each day's trips, newest first, as Android's Trips screen lists them. A trip opens
// a sheet to mark it Business or Personal, label it, or delete it.
struct TripsView: View {
    @StateObject private var model: TripsModel
    @State private var chosen: TripText?
    @Environment(\.scenePhase) private var scenePhase

    init(core: DuallyCore) {
        _model = StateObject(wrappedValue: TripsModel(core: core))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                MonthHeader(model: model)
                if let month = model.month {
                    MonthTotals(month: month)
                    if let live = month.inProgress {
                        TripRow(trip: live, recording: true)
                    }
                    ForEach(month.days, id: \.title) { day in
                        DaySection(day: day) { chosen = $0 }
                    }
                    if month.days.isEmpty && month.inProgress == nil {
                        Text("No trips this month.")
                            .font(Typography.body(15))
                            .foregroundStyle(Palette.textSecondary)
                            .padding(.top, 8)
                    }
                    Toggle(isOn: $model.showLeftOut) {
                        Text(month.hiddenLeftOut > 0 && !model.showLeftOut
                             ? "Show deleted and discarded (\(month.hiddenLeftOut))"
                             : "Show deleted and discarded")
                            .font(Typography.body(15))
                            .foregroundStyle(Palette.text)
                    }
                    .tint(Palette.accent)
                    .padding(.top, 4)
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
        .task {
            model.lookUpAddresses()
            // Again every few seconds while the screen is up: an address found, a trip ended
            // and the distance of the trip being recorded all show without a tap.
            while !Task.isCancelled {
                await model.load()
                try? await Task.sleep(nanoseconds: 5_000_000_000)
            }
        }
        .onChange(of: scenePhase) { phase in
            if phase == .active {
                model.lookUpAddresses()
                Task { await model.load() }
            }
        }
        .sheet(item: Binding(get: { chosen.map(ChosenTrip.init) }, set: { chosen = $0?.trip })) { item in
            TripSheet(trip: item.trip, model: model) { chosen = nil }
        }
    }
}

/// A trip chosen for the sheet, identified by its id.
private struct ChosenTrip: Identifiable {
    let trip: TripText
    var id: Int64 { trip.tripId }
}

/// The top line and the month, with the arrows to the month before and after.
private struct MonthHeader: View {
    @ObservedObject var model: TripsModel

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            TopLine(screen: "Trips")
            HStack {
                Text(model.month?.title ?? " ")
                    .font(Typography.display(26))
                    .foregroundStyle(Palette.text)
                Spacer()
                ArrowButton(systemName: "chevron.left", label: "Month before") {
                    Task { await model.step(by: -1) }
                }
                ArrowButton(systemName: "chevron.right", label: "Month after") {
                    Task { await model.step(by: 1) }
                }
                .disabled(!model.canGoForward)
                .opacity(model.canGoForward ? 1 : 0.35)
            }
        }
    }
}

private struct ArrowButton: View {
    let systemName: String
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 16, weight: .bold))
                .frame(width: 40, height: 40)
                .background(Palette.controlFill, in: Circle())
                .foregroundStyle(Palette.text)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

/// The month's Business kilometres on the orange card, Personal and the count beside it.
private struct MonthTotals: View {
    let month: MonthText

    var body: some View {
        HStack(spacing: 12) {
            Tile(ground: Palette.accent) {
                Text("Business")
                    .font(Typography.body(13, weight: .semibold))
                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    Text(month.business).font(Typography.figure(28))
                    Text("km").font(Typography.body(14))
                }
                Text(tripCount(month.businessCount))
                    .font(Typography.body(13))
            }
            .foregroundStyle(Palette.markLetter)
            Tile {
                Text("Personal")
                    .font(Typography.body(13))
                    .foregroundStyle(Palette.textSecondary)
                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    Text(month.personal).font(Typography.figure(28)).foregroundStyle(Palette.text)
                    Text("km").font(Typography.body(14)).foregroundStyle(Palette.textSecondary)
                }
                Text(tripCount(month.personalCount))
                    .font(Typography.body(13))
                    .foregroundStyle(Palette.textSecondary)
            }
        }
    }

    private func tripCount(_ count: Int32) -> String {
        count == 1 ? "1 trip" : "\(count) trips"
    }
}

/// One day: its date and Business kilometres, then its trips.
private struct DaySection: View {
    let day: DayText
    let onChoose: (TripText) -> Void

    var body: some View {
        Tile {
            HStack(alignment: .firstTextBaseline) {
                Text(day.title)
                    .font(Typography.body(16, weight: .bold))
                    .foregroundStyle(Palette.text)
                Spacer()
                Text("\(day.business) km Business")
                    .font(Typography.body(13))
                    .foregroundStyle(Palette.textSecondary)
            }
            ForEach(day.trips, id: \.tripId) { trip in
                Button { onChoose(trip) } label: { TripRow(trip: trip, recording: false) }
                    .buttonStyle(.plain)
            }
        }
    }
}

/// One trip: its times, places, kilometres, and what it is saved as.
private struct TripRow: View {
    let trip: TripText
    let recording: Bool

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 3) {
                Text(recording ? "Recording since \(trip.time)" : trip.time)
                    .font(Typography.body(14, weight: .semibold))
                    .foregroundStyle(Palette.text)
                if let route = trip.route {
                    Text(route)
                        .font(Typography.body(13))
                        .foregroundStyle(Palette.textSecondary)
                        .lineLimit(2)
                }
                let quiet = [trip.label, trip.category, trip.note].compactMap { $0 }
                if !quiet.isEmpty {
                    Text(quiet.joined(separator: " · "))
                        .font(Typography.body(13))
                        .foregroundStyle(Palette.textSecondary)
                }
            }
            Spacer()
            if let distance = trip.distance {
                Text(distance)
                    .font(Typography.figure(17))
                    .foregroundStyle(Palette.text)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.controlFill, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .contentShape(Rectangle())
    }
}
