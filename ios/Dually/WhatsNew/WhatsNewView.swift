import DuallyShared
import SwiftUI

/// The version on this iPhone, "1.0.1", and its build number, from the app's own Info.plist.
enum InstalledVersion {
    static var name: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "?"
    }

    static var build: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "?"
    }

    /// The iPhone's list of versions and their changes, built into the app.
    static var changelogText: String {
        guard let url = Bundle.main.url(forResource: "changelog", withExtension: "json"),
              let text = try? String(contentsOf: url, encoding: .utf8) else { return "" }
        return text
    }
}

// What's new on the iPhone (2026-11-16), as Android's (2026-10-08, Shawn: "make it work same as
// gopherforms and seawingman. with including version and what has changed"): every version,
// newest first, with a tag for each change's kind, its title and a sentence under it. It opens
// from the Version tile at the end of Settings, and by itself once after an update.
struct WhatsNewView: View {
    let core: DuallyCore
    let back: () -> Void
    /// The version on this iPhone's heading was held: Android's way to the one-year party.
    var versionHeld: () -> Void = {}

    @State private var list: WhatsNewText?

    var body: some View {
        PinnedScreen(title: "What's new", onBack: back) {
            if let list {
                if list.unreadable {
                    Tile {
                        Text("The list of changes could not be read. The Log screen says why.")
                            .font(Typography.body(15))
                            .foregroundStyle(Palette.amber)
                    }
                }
                ForEach(Array(list.releases.enumerated()), id: \.offset) { _, release in
                    Text(release.heading)
                        .font(Typography.body(14, weight: .semibold))
                        .foregroundStyle(Palette.textSecondary)
                        .padding(.top, 8)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .contentShape(Rectangle())
                        .accessibilityAddTraits(.isHeader)
                        .onLongPressGesture {
                            if release.heading.hasSuffix("on this phone") { versionHeld() }
                        }
                    Tile {
                        ForEach(Array(release.changes.enumerated()), id: \.offset) { index, change in
                            ChangeRow(change: change)
                            if index < release.changes.count - 1 { Divider().padding(.vertical, 6) }
                        }
                    }
                }
            } else {
                Tile {
                    Text("Reading the list of changes…")
                        .font(Typography.body(15))
                        .foregroundStyle(Palette.textSecondary)
                }
            }
        }
        .task {
            list = try? await core.whatsNew.list(text: InstalledVersion.changelogText, installed: InstalledVersion.name)
            // Opened, by itself or from Settings: not due again until the next version.
            try? await core.whatsNew.shown(installed: InstalledVersion.name)
        }
    }
}

/// A change: its tag, its title, and what it says under it. Read as one by VoiceOver.
private struct ChangeRow: View {
    let change: ChangeText

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(change.tag)
                .font(Typography.body(11, weight: .bold))
                .padding(.horizontal, 8)
                .padding(.vertical, 3)
                .background(fill, in: Capsule())
                .foregroundStyle(ink)
            Text(change.title)
                .font(Typography.body(16, weight: .semibold))
                .foregroundStyle(Palette.text)
            if let body = change.body {
                Text(body)
                    .font(Typography.body(14))
                    .foregroundStyle(Palette.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .accessibilityElement(children: .combine)
    }

    // Android's change tags: New in the accent, Improved in the blue of the truck's tag, Fixed
    // in amber, Security in the error's red.
    private var fill: Color {
        switch change.kind {
        case "NEW": return Palette.accentTile
        case "IMPROVED": return Palette.truckTagFill
        case "FIXED": return Palette.amberTile
        default: return Palette.errorTagFill
        }
    }

    private var ink: Color {
        switch change.kind {
        case "NEW", "FIXED": return Palette.asphalt
        case "IMPROVED": return Palette.truckTagText
        default: return Palette.errorTagText
        }
    }
}

/// The last tile of Settings, as Android's: the version on this iPhone and the way into What's
/// new, the whole tile one button.
struct VersionTile: View {
    let open: () -> Void

    var body: some View {
        Button(action: open) {
            Tile {
                HStack {
                    Text("Version")
                        .font(Typography.body(17, weight: .semibold))
                        .foregroundStyle(Palette.text)
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Palette.textSecondary)
                }
                Text("Dually Trip Tracker \(InstalledVersion.name) · build \(InstalledVersion.build)")
                    .font(Typography.body(16, weight: .semibold))
                    .foregroundStyle(Palette.text)
                Text("What changed in each version")
                    .font(Typography.body(14))
                    .foregroundStyle(Palette.textSecondary)
            }
        }
        .buttonStyle(.plain)
        .accessibilityHint("see what's new")
    }
}
