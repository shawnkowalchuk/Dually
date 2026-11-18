import AVFoundation
import DuallyShared

// The connect and trip-start sounds on the iPhone (2026-11-16), at Android's moments and by its
// settings (`IosSounds`). Shawn chose "Play through silent" for the iPhone: Dually plays them
// itself, in the playback category, so the ring switch and a Focus do not silence them, as
// Android's alarm-type sounds are heard on silent and in Bedtime mode; with CarPlay connected
// iOS sends them to the truck's speakers. Other audio is lowered while one plays and comes back
// after. The app keeps running in the background while it records a trip, and the `audio`
// background mode lets it start a sound there. One sound at a time: a second waits for the first.
@MainActor
final class TripSoundPlayer: NSObject, AVAudioPlayerDelegate {
    static let shared = TripSoundPlayer()

    private var queue: [SoundToPlay] = []
    private var player: AVAudioPlayer?
    private var core: DuallyCore?

    /// Plays [sounds] in their order, after whatever is playing now.
    func play(_ sounds: [SoundToPlay], core: DuallyCore) {
        guard !sounds.isEmpty else { return }
        self.core = core
        queue.append(contentsOf: sounds)
        if player == nil { next() }
    }

    /// One sound now, for the list's play button: it does not wait for, or log, anything.
    func preview(file: String, own: String?) {
        stop()
        guard let url = own.map({ URL(fileURLWithPath: $0) }) ?? Self.builtIn(file) else { return }
        _ = start(url)
    }

    func stop() {
        player?.stop()
        player = nil
        queue.removeAll()
        deactivate()
    }

    private func next() {
        guard !queue.isEmpty else {
            player = nil
            deactivate()
            return
        }
        let sound = queue.removeFirst()
        if let own = sound.own {
            if start(OwnSounds.url(own)) {
                log("\(sound.label): playing your own sound")
                return
            }
            log("\(sound.label) could not play the chosen sound: the file cannot be played", problem: true)
        }
        if let url = Self.builtIn(sound.file), start(url) {
            log("\(sound.label): playing the bundled \(sound.builtInName.lowercased())")
        } else {
            log("\(sound.label) could not play the bundled \(sound.builtInName.lowercased())", problem: true)
            next()
        }
    }

    private func start(_ url: URL) -> Bool {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default, options: [.duckOthers])
            try session.setActive(true)
            let player = try AVAudioPlayer(contentsOf: url)
            player.delegate = self
            guard player.play() else { return false }
            self.player = player
            return true
        } catch {
            return false
        }
    }

    private func deactivate() {
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor in self.next() }
    }

    nonisolated func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: Error?) {
        Task { @MainActor in self.next() }
    }

    private func log(_ line: String, problem: Bool = false) {
        guard let core else { return }
        Task { try? await core.sounds.log(line: line, problem: problem) }
    }

    static func builtIn(_ file: String) -> URL? {
        Bundle.main.url(forResource: file, withExtension: "wav")
    }
}
