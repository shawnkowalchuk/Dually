import AVFoundation
import DuallyShared
import SwiftUI
import UniformTypeIdentifiers

// The Sounds tile of Settings on the iPhone (2026-11-16), as Android's: the connect sound and the
// trip-start sound, each with its switch and the sound in use; a press opens the moment's list of
// the twelve built-in sounds and the user's own, with a button to hear each and Add a sound.

/// Where Dually keeps its copies of the user's own sounds, under Application Support. The
/// settings hold the path from there, since the app's folder moves when it is updated.
enum OwnSounds {
    static let folder = "Sounds"

    static func url(_ stored: String) -> URL {
        if stored.hasPrefix("/") { return URL(fileURLWithPath: stored) }
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent(stored)
    }

    /// The largest file taken, as on Android (`MAX_OWN_SOUND_BYTES`).
    static let maxBytes = 10 * 1024 * 1024
}

@MainActor
final class SoundsModel: ObservableObject {
    @Published var moments: [SoundMomentText] = []
    @Published var page: SoundPageText?
    @Published var problem: String?
    let core: DuallyCore

    init(core: DuallyCore) { self.core = core }

    func load() async {
        moments = (try? await core.sounds.tile()) ?? []
        if let which = page?.which { page = try? await core.sounds.page(which: which) }
    }

    func open(_ which: String) async {
        problem = nil
        page = try? await core.sounds.page(which: which)
    }

    func setEnabled(_ which: String, _ on: Bool) async {
        try? await core.sounds.setEnabled(which: which, enabled: on)
        await load()
    }

    func choose(_ row: SoundRowText, own: Bool) async {
        guard let which = page?.which else { return }
        if own {
            _ = try? await core.sounds.chooseOwn(which: which, path: row.key)
        } else {
            try? await core.sounds.useBuiltIn(which: which, storedName: row.key)
        }
        await load()
    }

    func remove(_ row: SoundRowText) async {
        try? await core.sounds.removeOwn(path: row.key)
        try? FileManager.default.removeItem(at: OwnSounds.url(row.key))
        await load()
    }

    /// Copies the file the user picked into Dually's own storage, checks that it plays, and
    /// makes it this moment's sound. Android's words where it cannot.
    func add(_ picked: URL) async {
        guard let which = page?.which else { return }
        let reading = picked.startAccessingSecurityScopedResource()
        defer { if reading { picked.stopAccessingSecurityScopedResource() } }
        let size = (try? picked.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
        guard size <= OwnSounds.maxBytes else {
            problem = "That file is too large for a sound (more than 10 MB). The sound was not changed."
            return
        }
        let stamp = Int(Date().timeIntervalSince1970 * 1000)
        let stored = "\(OwnSounds.folder)/\(stamp).\(picked.pathExtension.isEmpty ? "m4a" : picked.pathExtension)"
        let target = OwnSounds.url(stored)
        do {
            try FileManager.default.createDirectory(at: target.deletingLastPathComponent(), withIntermediateDirectories: true)
            try FileManager.default.copyItem(at: picked, to: target)
        } catch {
            problem = "Dually could not copy that file. The sound was not changed."
            return
        }
        guard (try? AVAudioPlayer(contentsOf: target)) != nil else {
            try? FileManager.default.removeItem(at: target)
            problem = "That file cannot be played as a sound. The sound was not changed."
            return
        }
        problem = nil
        try? await core.sounds.addOwn(which: which, path: stored, name: picked.deletingPathExtension().lastPathComponent)
        await load()
    }
}

struct SoundsTile: View {
    @StateObject private var model: SoundsModel
    @State private var showingPage = false

    init(core: DuallyCore) {
        _model = StateObject(wrappedValue: SoundsModel(core: core))
    }

    var body: some View {
        Tile {
            Text("Sounds")
                .font(Typography.body(17, weight: .semibold))
                .foregroundStyle(Palette.text)
            ForEach(model.moments, id: \.which) { moment in
                HStack(spacing: 12) {
                    Button {
                        Task {
                            await model.open(moment.which)
                            showingPage = true
                        }
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            HStack(spacing: 4) {
                                Text(moment.title)
                                    .font(Typography.body(16, weight: .semibold))
                                    .foregroundStyle(Palette.text)
                                Image(systemName: "chevron.right")
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundStyle(Palette.textSecondary)
                            }
                            Text(moment.inUse)
                                .font(Typography.body(14))
                                .foregroundStyle(Palette.textSecondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityHint(moment.which == "CONNECT" ? "choose the connect sound" : "choose the trip-start sound")
                    Toggle("", isOn: Binding(get: { moment.enabled }, set: { on in Task { await model.setEnabled(moment.which, on) } }))
                        .labelsHidden()
                        .tint(Palette.accent)
                        .accessibilityLabel(moment.which == "CONNECT" ? "Connect sound" : "Trip-start sound")
                }
                .padding(.vertical, 4)
            }
            Text("Both play even when the iPhone is on silent or in a Focus, through CarPlay when it is connected.")
                .font(Typography.body(13))
                .foregroundStyle(Palette.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .task { await model.load() }
        .sheet(isPresented: $showingPage, onDismiss: { TripSoundPlayer.shared.stop() }) {
            SoundsPage(model: model) { showingPage = false }
        }
    }
}

/// One moment's sounds: Android's list page on the iPhone, as a sheet.
private struct SoundsPage: View {
    @ObservedObject var model: SoundsModel
    let close: () -> Void
    @State private var picking = false

    var body: some View {
        ScrollView {
            if let page = model.page {
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text(page.title)
                            .font(Typography.display(24))
                            .foregroundStyle(Palette.text)
                        Spacer()
                        Button("Done", action: close)
                            .font(Typography.body(16, weight: .semibold))
                            .foregroundStyle(Palette.accent)
                    }
                    Text(page.whenItPlays)
                        .font(Typography.body(15))
                        .foregroundStyle(Palette.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                    Text("Built in")
                        .font(Typography.body(14, weight: .semibold))
                        .foregroundStyle(Palette.textSecondary)
                        .padding(.top, 6)
                    Tile {
                        ForEach(Array(page.builtIn.enumerated()), id: \.element.key) { index, row in
                            SoundRow(row: row, own: false, model: model)
                            if index < page.builtIn.count - 1 { Divider() }
                        }
                    }
                    Text("Your sounds")
                        .font(Typography.body(14, weight: .semibold))
                        .foregroundStyle(Palette.textSecondary)
                        .padding(.top, 6)
                    Tile {
                        if page.own.isEmpty {
                            Text("None yet. A sound you add from this iPhone can be used for both sounds.")
                                .font(Typography.body(14))
                                .foregroundStyle(Palette.textSecondary)
                        }
                        ForEach(Array(page.own.enumerated()), id: \.element.key) { index, row in
                            SoundRow(row: row, own: true, model: model)
                            if index < page.own.count - 1 { Divider() }
                        }
                    }
                    if let problem = model.problem {
                        Text(problem)
                            .font(Typography.body(14))
                            .foregroundStyle(Palette.amber)
                    }
                    WideButton(title: "Add a sound", filled: false) { picking = true }
                }
                .padding(16)
            }
        }
        .background(Palette.page.ignoresSafeArea())
        .fileImporter(isPresented: $picking, allowedContentTypes: [.audio]) { result in
            if case let .success(url) = result { Task { await model.add(url) } }
        }
    }
}

/// A sound in a list: a tick on the one in use, its name (a press chooses it), the button that
/// plays it, and Remove for one of the user's own.
private struct SoundRow: View {
    let row: SoundRowText
    let own: Bool
    @ObservedObject var model: SoundsModel

    var body: some View {
        HStack(spacing: 10) {
            Button {
                Task { await model.choose(row, own: own) }
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: row.chosen ? "checkmark.circle.fill" : "circle")
                        .foregroundStyle(row.chosen ? Palette.accent : Palette.textSecondary)
                    Text(row.name)
                        .font(Typography.body(16))
                        .foregroundStyle(Palette.text)
                    Spacer()
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(row.chosen ? .isSelected : [])
            Button {
                TripSoundPlayer.shared.preview(file: row.file, own: own ? OwnSounds.url(row.key).path : nil)
            } label: {
                Image(systemName: "play.circle")
                    .font(.system(size: 22))
                    .foregroundStyle(Palette.accent)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Play \(row.name)")
            if own {
                Button("Remove") { Task { await model.remove(row) } }
                    .font(Typography.body(14, weight: .semibold))
                    .foregroundStyle(Palette.amber)
                    .accessibilityLabel("Remove \(row.name)")
            }
        }
        .padding(.vertical, 6)
    }
}
