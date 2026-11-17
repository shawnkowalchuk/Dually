import DuallyShared
import SwiftUI

// The Log on the iPhone (2026-11-15), as Android's Log screen: what Dually did and when, newest
// first, a tile for each day, each line with its time to the second, its coloured tag and what it
// says; a line with more to say opens on a tap. The chips narrow it to trips, the truck (what the
// Shortcuts automation reported) or errors, by Android's own grouping (`logGroup`, shared).
// "Share the whole log" hands Android's text file to the share sheet, for support.
struct LogView: View {
    let core: DuallyCore
    let back: () -> Void
    @State private var group: String?
    @State private var limit = 200
    @State private var page: LogPage?
    @State private var opened: Set<Int64> = []
    @State private var asking = false
    @State private var file: URL?
    @State private var shareFailed = false

    private let chips: [(label: String, group: String?)] = [
        ("All", nil), ("Trips", "TRIPS"), ("Truck", "BLUETOOTH"), ("Errors", "ERRORS"),
    ]

    var body: some View {
        PinnedScreen(title: "Log", onBack: back) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(chips, id: \.label) { chip in
                        Button(chip.label) {
                            group = chip.group
                            limit = 200
                        }
                        .font(Typography.body(14, weight: .semibold))
                        .padding(.horizontal, 14)
                        .frame(minHeight: 36)
                        .background(group == chip.group ? Palette.text : Palette.tile, in: Capsule())
                        .foregroundStyle(group == chip.group ? Palette.page : Palette.text)
                    }
                }
            }
            WideButton(title: "Share the whole log", filled: false) { asking = true }
            if shareFailed {
                Text("The log could not be written to a file.")
                    .font(Typography.body(14))
                    .foregroundStyle(Palette.amber)
            }
            if let page {
                if page.days.isEmpty {
                    Tile {
                        Text(page.empty)
                            .font(Typography.body(14))
                            .foregroundStyle(Palette.textSecondary)
                    }
                }
                ForEach(page.days, id: \.title) { day in
                    Text(day.title)
                        .font(Typography.body(13, weight: .semibold))
                        .foregroundStyle(Palette.textSecondary)
                        .padding(.top, 4)
                    Tile {
                        ForEach(Array(day.lines.enumerated()), id: \.element.id) { index, line in
                            if index > 0 { Divider().overlay(Palette.controlFill) }
                            LogLineRow(line: line, open: opened.contains(line.id)) {
                                if opened.contains(line.id) { opened.remove(line.id) } else { opened.insert(line.id) }
                            }
                        }
                    }
                }
                if page.more {
                    WideButton(title: "Show older entries", filled: false) { limit += 200 }
                }
            } else {
                Tile {
                    Text("Reading the log…")
                        .font(Typography.body(14))
                        .foregroundStyle(Palette.textSecondary)
                }
            }
        }
        .task(id: "\(group ?? "all")-\(limit)") {
            page = try? await core.log.page(group: group, limit: Int32(limit))
        }
        .confirmationDialog(
            "Share the whole log?",
            isPresented: $asking,
            titleVisibility: .visible
        ) {
            Button("Share") { Task { await share() } }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Makes a text file of every line, oldest first, and offers it to your apps, so that you can send it to support.")
        }
        .sheet(item: Binding(get: { file.map(SharedFile.init) }, set: { file = $0?.url })) { shared in
            ShareSheet(items: [shared.url])
        }
    }

    private func share() async {
        let now = core.timeNow()
        guard let text = try? await core.log.fileText(nowMs: now) else {
            shareFailed = true
            return
        }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(core.log.fileName(nowMs: now))
        do {
            try text.write(to: url, atomically: true, encoding: .utf8)
            shareFailed = false
            file = url
        } catch {
            shareFailed = true
        }
    }
}

/// One line: its time in its own column, then the tag and what it says, and "Tap for details"
/// or the details themselves for a line that has more.
private struct LogLineRow: View {
    let line: LogLineText
    let open: Bool
    let toggle: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Text(line.time)
                .font(Typography.figure(13))
                .foregroundStyle(Palette.textSecondary)
                .padding(.top, 2)
            VStack(alignment: .leading, spacing: 4) {
                Text(line.tag)
                    .font(Typography.figure(11))
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(tagColors.fill, in: RoundedRectangle(cornerRadius: 5, style: .continuous))
                    .foregroundStyle(tagColors.text)
                Text(line.message)
                    .font(Typography.body(14))
                    .foregroundStyle(Palette.logText)
                    .fixedSize(horizontal: false, vertical: true)
                if let detail = line.detail {
                    Text(open ? detail : "Tap for details")
                        .font(Typography.body(13))
                        .foregroundStyle(Palette.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
        .onTapGesture { if line.detail != nil { toggle() } }
        .accessibilityElement(children: .combine)
    }

    /// The colours of the chip the line is found under, as Android's tags.
    private var tagColors: (fill: Color, text: Color) {
        switch line.group {
        case "TRIPS": return (Palette.accentTile, Palette.asphalt)
        case "BLUETOOTH": return (Palette.truckTagFill, Palette.truckTagText)
        case "ERRORS": return (Palette.errorTagFill, Palette.errorTagText)
        default: return (Palette.controlFill, Palette.logText)
        }
    }
}

/// The file the share sheet is handed.
private struct SharedFile: Identifiable {
    let url: URL
    var id: String { url.path }
}

/// iOS's share sheet, for the log's file.
private struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
