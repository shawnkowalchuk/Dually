import AVFoundation
import DuallyShared
import QuartzCore
import SwiftUI

// The run after a report is sent, on the iPhone (2026-11-16), as Android's (`MascotRun.kt`): when
// Mail says the report went, the mascot runs in along the top of the bottom bar with dust at his
// heels, spins his wheels up on the spot for two seconds with his shoes smoking, brakes with his
// brake lights coming on, swings round and slides out at the right, the smoke left lying behind.
// Wheels whirr on the spot and tyres squeal as he slides. Android's three pictures and two
// sounds, by the shared numbers (`RunPicture`, `RunWay`).

@MainActor
final class RunPictures {
    let sheet: MascotSheet
    let onSpot: MascotPlayed
    let slide: MascotPlayed

    init?() {
        let frames = RunPicture.shared.SHEET
        guard let sheet = MascotSheet("mascot_run_in", columns: Int(frames.sheetColumns), rows: Int(frames.sheetRows)),
              let onSpot = MascotPlayed("mascot_run_on_the_spot"),
              let slide = MascotPlayed("mascot_brake_and_slide")
        else { return nil }
        self.sheet = sheet
        self.onSpot = onSpot
        self.slide = slide
    }
}

/// The run's two sounds. Screen sounds, as on Android, not signals for the road: in the ambient
/// category, so the ring switch on silent silences them, and only where one of Dually's two
/// sounds is switched on. Made ready when the run begins, started on their frames.
@MainActor
final class RunSounds {
    private var whirr: AVAudioPlayer?
    private var squeal: AVAudioPlayer?

    init(heard: Bool) {
        guard heard else { return }
        try? AVAudioSession.sharedInstance().setCategory(.ambient)
        whirr = Self.ready("report_run_whirr")
        squeal = Self.ready("report_run_squeal")
    }

    func playWhirr() { whirr?.play() }
    func playSqueal() { squeal?.play() }

    func stop() {
        whirr?.stop()
        squeal?.stop()
    }

    private static func ready(_ name: String) -> AVAudioPlayer? {
        guard let url = Bundle.main.url(forResource: name, withExtension: "wav"),
              let player = try? AVAudioPlayer(contentsOf: url) else { return nil }
        player.prepareToPlay()
        return player
    }
}

@MainActor
final class RunPlayer: ObservableObject {
    @Published private(set) var sheetImage: CGImage?
    @Published private(set) var playedImage: CGImage?
    @Published private(set) var axis: CGFloat = 0

    let pictures: RunPictures
    let way: RunWay
    private let sounds: RunSounds
    private var stage = Stage.coming
    private var stageBegan: CFTimeInterval = 0
    private var link: CADisplayLink?
    private let onDone: () -> Void

    private enum Stage { case coming, onSpot, sliding, over }

    init(pictures: RunPictures, screenWidth: CGFloat, soundsHeard: Bool, onDone: @escaping () -> Void) {
        self.pictures = pictures
        self.way = RunWay(screenWidth: Float(screenWidth))
        self.sounds = RunSounds(heard: soundsHeard)
        self.onDone = onDone
        show(way.coming.poseIn(frame: 0))
    }

    func start() {
        guard link == nil else { return }
        let link = CADisplayLink(target: self, selector: #selector(tick(_:)))
        link.add(to: .main, forMode: .common)
        self.link = link
        stageBegan = CACurrentMediaTime()
    }

    /// Cut short, it is gone at once with its sound, and does not come back.
    func stop() {
        link?.invalidate()
        link = nil
        if stage != .over {
            stage = .over
            sounds.stop()
            onDone()
        }
    }

    @objc private func tick(_ link: CADisplayLink) {
        let millis = Int64((link.targetTimestamp - stageBegan) * 1000)
        let frameMillis = Double(MascotPicture.shared.FRAME_MS)
        switch stage {
        case .coming:
            let frame = way.coming.frameAt(atMillis: millis)
            show(way.coming.poseIn(frame: frame))
            if frame >= way.coming.frames_ {
                // The drawing that first shows him at the spot: the whirr starts in it.
                sheetImage = nil
                playedImage = pictures.onSpot.frame(0)
                sounds.playWhirr()
                next(.onSpot, link)
            }
        case .onSpot:
            let frame = Int(Double(millis) / frameMillis)
            playedImage = pictures.onSpot.frame(frame)
            if frame >= pictures.onSpot.frames {
                // The drawing that first shows his brake lights lit: the squeal starts in it.
                playedImage = pictures.slide.frame(0)
                sounds.playSqueal()
                next(.sliding, link)
            }
        case .sliding:
            playedImage = pictures.slide.frame(Int(Double(millis) / frameMillis))
            // Until the last of him and of his smoke has left the screen.
            if millis >= way.slideMillis {
                link.invalidate()
                self.link = nil
                stage = .over
                onDone()
            }
        case .over:
            break
        }
    }

    private func next(_ stage: Stage, _ link: CADisplayLink) {
        self.stage = stage
        stageBegan = link.targetTimestamp
    }

    private func show(_ pose: MascotPose) {
        axis = CGFloat(pose.axis)
        if let frame = pose.sheetFrame {
            sheetImage = pictures.sheet.frame(frame.intValue)
            playedImage = nil
        }
    }
}

struct MascotRunView: View {
    @StateObject private var player: RunPlayer

    init(player: @autoclosure @escaping () -> RunPlayer) {
        _player = StateObject(wrappedValue: player())
    }

    var body: some View {
        let sheet = RunPicture.shared.SHEET
        let soles = MascotGreetingView.solesBelowContent
        ZStack(alignment: .bottomLeading) {
            if let image = player.sheetImage {
                ZStack(alignment: .topLeading) {
                    Ellipse()
                        .fill(RadialGradient(colors: [Color.black.opacity(0.5), .clear], center: .center, startRadius: 0, endRadius: 46))
                        .frame(width: 92, height: 14)
                        .offset(x: CGFloat(sheet.axis) - 46, y: CGFloat(sheet.height) - CGFloat(sheet.soles) - 3.4 - 7)
                    Image(decorative: image, scale: 2.5)
                        .resizable()
                        .interpolation(.high)
                        .frame(width: CGFloat(sheet.width), height: CGFloat(sheet.height))
                }
                .frame(width: CGFloat(sheet.width), height: CGFloat(sheet.height), alignment: .topLeading)
                .offset(x: player.axis - CGFloat(sheet.axis), y: soles + CGFloat(sheet.soles))
            }
            if let image = player.playedImage {
                // The pictures that stand still: he moves in them, their shadow and smoke drawn in.
                Image(decorative: image, scale: 2.5)
                    .resizable()
                    .interpolation(.high)
                    .frame(width: CGFloat(RunPicture.shared.WIDTH), height: CGFloat(RunPicture.shared.HEIGHT))
                    .offset(x: CGFloat(player.way.playedLeft), y: soles + CGFloat(RunPicture.shared.SOLES))
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
        .onAppear { player.start() }
        .onDisappear { player.stop() }
    }
}
