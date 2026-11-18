import ActivityKit
import SwiftUI
import WidgetKit

// The trip being recorded on the Lock Screen and in the Dynamic Island (phase 4e; ADR-015,
// Shawn's choice of a Live Activity at launch): the iPhone's form of Android's trip notification,
// with its words ("Trip in progress", "Trip in progress. Waiting for the truck to reconnect"),
// the distance so far, the time since the start, and End trip.

@main
struct DuallyLiveActivityBundle: WidgetBundle {
    var body: some Widget {
        TripLiveActivity()
        TripWidget()
    }
}

struct TripLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: TripActivityAttributes.self) { context in
            LockScreenTrip(context: context)
                .activityBackgroundTint(Palette.page)
                .activitySystemActionForegroundColor(Palette.text)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    HStack(spacing: 8) {
                        DuallyMark(size: 26)
                        Text(statusWords(context.state, short: true))
                            .font(Typography.body(14, weight: .semibold))
                            .foregroundStyle(Palette.text)
                            .lineLimit(1)
                    }
                }
                DynamicIslandExpandedRegion(.trailing) {
                    DistanceText(state: context.state, size: 26)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    HStack {
                        Elapsed(since: context.attributes.startedAt, size: 16)
                        Spacer()
                        EndTripButton()
                    }
                }
            } compactLeading: {
                DuallyMark(size: 20)
            } compactTrailing: {
                Text(context.state.distance)
                    .font(Typography.figure(15))
                    .foregroundStyle(Palette.accent)
            } minimal: {
                DuallyMark(size: 20)
            }
            .keylineTint(Palette.accent)
        }
    }
}

/// The Lock Screen card: the mark and what is happening, then the distance and the time, then
/// End trip.
private struct LockScreenTrip: View {
    let context: ActivityViewContext<TripActivityAttributes>

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                DuallyMark(size: 28)
                Text(statusWords(context.state, short: false))
                    .font(Typography.body(15, weight: .semibold))
                    .foregroundStyle(context.state.waitingForTruck ? Palette.amber : Palette.text)
                    .lineLimit(2)
                Spacer(minLength: 0)
            }
            HStack(alignment: .firstTextBaseline) {
                DistanceText(state: context.state, size: 34)
                Spacer()
                Elapsed(since: context.attributes.startedAt, size: 18)
            }
            EndTripButton()
        }
        .padding(16)
    }
}

/// "12.3 km", the figure in IBM Plex Mono as on Home.
private struct DistanceText: View {
    let state: TripActivityAttributes.ContentState
    let size: CGFloat

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 4) {
            Text(state.distance)
                .font(Typography.figure(size))
                .foregroundStyle(Palette.text)
            Text(state.unit)
                .font(Typography.body(size * 0.5))
                .foregroundStyle(Palette.textSecondary)
        }
    }
}

/// The time since the start, counted by iOS itself, so the activity is not updated every second.
private struct Elapsed: View {
    let since: Date
    let size: CGFloat

    var body: some View {
        Text(since, style: .timer)
            .font(Typography.figure(size))
            .foregroundStyle(Palette.textSecondary)
            .monospacedDigit()
            .multilineTextAlignment(.trailing)
    }
}

/// End trip: a button from iOS 17, where a Live Activity can run an action; before that, a
/// tap on the activity opens Dually, where End trip is.
private struct EndTripButton: View {
    var body: some View {
        if #available(iOS 17.0, *) {
            Button(intent: StopTripIntent()) {
                Text("End trip")
                    .font(Typography.body(15, weight: .bold))
                    .frame(maxWidth: .infinity, minHeight: 38)
                    .background(Palette.accent, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .foregroundStyle(Palette.markLetter)
            }
            .buttonStyle(.plain)
        } else {
            Text("Open Dually to end the trip")
                .font(Typography.body(13))
                .foregroundStyle(Palette.textSecondary)
        }
    }
}

/// Android's trip notification's words.
private func statusWords(_ state: TripActivityAttributes.ContentState, short: Bool) -> String {
    switch (state.waitingForTruck, short) {
    case (true, true): return "Truck gone"
    case (true, false): return "Trip in progress. Waiting for the truck to reconnect"
    // The Dynamic Island's corner has room for one word: Home's card says "Recording" too.
    case (false, true): return "Recording"
    case (false, false): return "Trip in progress"
    }
}
