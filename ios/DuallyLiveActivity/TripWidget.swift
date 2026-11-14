import SwiftUI
import WidgetKit

// The home-screen widget on the iPhone (phase 4i), as Android's: the mark and what is happening,
// the trip being recorded with its distance and running time, this month's and this year's
// Business dollars at the rate in Settings, and one button, Start trip or End trip (iOS 17 on).
// The app leaves the figures in the App Group (WidgetSnapshot) and asks for a new drawing when
// they change.

struct TripWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "TripWidget", provider: SnapshotProvider()) { entry in
            TripWidgetView(snapshot: entry.snapshot)
                .widgetGround()
        }
        .configurationDisplayName("Dually trip")
        .description("The trip being recorded, this month's Business dollars, and Start or End.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct SnapshotEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot
}

struct SnapshotProvider: TimelineProvider {
    func placeholder(in context: Context) -> SnapshotEntry { SnapshotEntry(date: Date(), snapshot: .empty) }

    func getSnapshot(in context: Context, completion: @escaping (SnapshotEntry) -> Void) {
        completion(SnapshotEntry(date: Date(), snapshot: .read()))
    }

    /// One entry: the app asks for the next drawing itself when something changes. The month is
    /// looked at again within the hour, as on Android.
    func getTimeline(in context: Context, completion: @escaping (Timeline<SnapshotEntry>) -> Void) {
        let entry = SnapshotEntry(date: Date(), snapshot: .read())
        completion(Timeline(entries: [entry], policy: .after(Date().addingTimeInterval(3600))))
    }
}

struct TripWidgetView: View {
    let snapshot: WidgetSnapshot
    @Environment(\.widgetFamily) private var family

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                DuallyMark(size: 20)
                Text(snapshot.recording ? "Recording" : "Not recording")
                    .font(Typography.body(13, weight: .semibold))
                    .foregroundStyle(Palette.text)
                    .lineLimit(1)
            }
            if snapshot.recording, let distance = snapshot.distance {
                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    Text(distance).font(Typography.figure(26)).foregroundStyle(Palette.text)
                    Text(snapshot.unit).font(Typography.body(12)).foregroundStyle(Palette.textSecondary)
                    if let started = snapshot.startedAt, family != .systemSmall {
                        Spacer()
                        Text(started, style: .timer)
                            .font(Typography.figure(14))
                            .foregroundStyle(Palette.textSecondary)
                            .multilineTextAlignment(.trailing)
                    }
                }
            }
            Spacer(minLength: 0)
            if let dollars = snapshot.dollars {
                Text(dollars)
                    .font(Typography.body(11))
                    .foregroundStyle(Palette.textSecondary)
                    .lineLimit(2)
            } else {
                Text("Dually's widget is switched off. Turn it on in Dually, Settings.")
                    .font(Typography.body(11))
                    .foregroundStyle(Palette.textSecondary)
                    .lineLimit(2)
            }
            button
        }
    }

    @ViewBuilder
    private var button: some View {
        if #available(iOS 17.0, *), snapshot.dollars != nil {
            if snapshot.recording {
                Button(intent: StopTripIntent()) { ButtonLabel(title: "End trip", filled: false) }
                    .buttonStyle(.plain)
            } else {
                Button(intent: WidgetStartTripIntent()) { ButtonLabel(title: "Start trip", filled: true) }
                    .buttonStyle(.plain)
            }
        }
    }
}

private struct ButtonLabel: View {
    let title: String
    let filled: Bool

    var body: some View {
        Text(title)
            .font(Typography.body(14, weight: .bold))
            .frame(maxWidth: .infinity, minHeight: 32)
            .background(filled ? Palette.accent : Palette.controlFill,
                        in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            .foregroundStyle(filled ? Palette.markLetter : Palette.text)
    }
}

private extension View {
    /// The widget's dark ground: iOS 17's container background, or a plain one before it.
    @ViewBuilder
    func widgetGround() -> some View {
        if #available(iOS 17.0, *) {
            containerBackground(Palette.page, for: .widget)
        } else {
            padding().background(Palette.page)
        }
    }
}
