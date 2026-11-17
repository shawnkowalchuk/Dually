import DuallyShared
import QuartzCore
import SwiftUI

// The greeting on the iPhone (2026-11-16), as Android's (`MascotGreeting.kt`, ADR-007): when
// Dually is opened from nothing onto Home, the mascot comes in along the top of the bottom bar,
// greets, and goes out at the other side. The wave and the moonwalk take turns (`IosMascot`).
// Where he stands and which frame shows at each moment is the shared course
// (`MascotGreetingKind.wayAcross`); the step and the place are one value, so they cannot come
// apart. Home works under him the whole time: he takes no touch and dims nothing.

/// A greeting's two pictures, read before anything is shown.
@MainActor
final class GreetingPictures {
    let kind: MascotGreetingKind
    let sheet: MascotSheet
    let played: MascotPlayed

    init?(_ kind: MascotGreetingKind) {
        let frames = kind.frames
        let names: (String, String) = kind == .wave
            ? ("mascot_walk_in_and_off", "mascot_stop_and_wave")
            : ("mascot_moonwalk", "mascot_stop_and_spin")
        guard let sheet = MascotSheet(names.0, columns: Int(frames.sheetColumns), rows: Int(frames.sheetRows)),
              let played = MascotPlayed(names.1)
        else { return nil }
        self.kind = kind
        self.sheet = sheet
        self.played = played
    }
}

/// Plays one greeting by the screen's own refresh, as Android's does by its frame clock.
@MainActor
final class GreetingPlayer: ObservableObject {
    @Published private(set) var image: CGImage?
    /// Where the mascot stands, in points from the screen's left edge.
    @Published private(set) var axis: CGFloat = 0

    let pictures: GreetingPictures
    private let way: MascotWay
    private var stage = Stage.coming
    private var stageBegan: CFTimeInterval = 0
    private var link: CADisplayLink?
    private let onDone: () -> Void

    private enum Stage { case coming, middle, going, over }

    init(pictures: GreetingPictures, screenWidth: CGFloat, onDone: @escaping () -> Void) {
        self.pictures = pictures
        self.way = pictures.kind.wayAcross(screenWidth: Float(screenWidth))
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

    /// Cut short: the greeting ends where it is, without a fade, as on Android.
    func stop() {
        link?.invalidate()
        link = nil
        if stage != .over {
            stage = .over
            onDone()
        }
    }

    @objc private func tick(_ link: CADisplayLink) {
        let millis = Int64((link.targetTimestamp - stageBegan) * 1000)
        switch stage {
        case .coming:
            let frame = way.coming.frameAt(atMillis: millis)
            show(way.coming.poseIn(frame: frame))
            if frame >= way.coming.frames_ { next(.middle, link) }
        case .middle:
            // The animated picture from its first frame; its last one ends the middle.
            let frame = Int(Double(millis) / 1000 / mascotFrameSeconds)
            image = pictures.played.frame(frame)
            if frame >= pictures.played.frames - 1 { next(.going, link) }
        case .going:
            let frame = way.going.frameAt(atMillis: millis)
            show(way.going.poseIn(frame: frame))
            if frame >= way.going.frames_ { stop() }
        case .over:
            break
        }
    }

    private func next(_ stage: Stage, _ link: CADisplayLink) {
        self.stage = stage
        stageBegan = link.targetTimestamp
    }

    /// A frame of the sheet, or the animated picture where the pose has none: its first frame
    /// on arriving, its last on leaving.
    private func show(_ pose: MascotPose) {
        axis = CGFloat(pose.axis)
        if let frame = pose.sheetFrame {
            image = pictures.sheet.frame(frame.intValue)
        } else {
            image = pictures.played.frame(stage == .going ? pictures.played.frames - 1 : 0)
        }
    }
}

/// The mascot over Home, his soles just above the bottom bar's tile, with his soft shadow.
struct MascotGreetingView: View {
    @StateObject private var player: GreetingPlayer

    init(player: @autoclosure @escaping () -> GreetingPlayer) {
        _player = StateObject(wrappedValue: player())
    }

    var body: some View {
        let frames = player.pictures.kind.frames
        let width = CGFloat(frames.width)
        let height = CGFloat(frames.height)
        let soles = CGFloat(frames.soles)
        ZStack(alignment: .topLeading) {
            // The shadow on the ground under him, as Android's: 92 by 14, darkest in the middle.
            Ellipse()
                .fill(RadialGradient(colors: [Color.black.opacity(0.5), .clear], center: .center, startRadius: 0, endRadius: 46))
                .frame(width: 92, height: 14)
                .offset(x: CGFloat(frames.axis) - 46, y: height - soles - 3.4 - 7)
            if let image = player.image {
                Image(decorative: image, scale: 2.5)
                    .resizable()
                    .interpolation(.high)
                    .frame(width: width, height: height)
            }
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .offset(x: player.axis - CGFloat(frames.axis), y: Self.solesBelowContent + soles)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
        .onAppear { player.start() }
        .onDisappear { player.stop() }
    }

    /// How far below the screen's content his soles stand: on the top of the bottom bar's room,
    /// a small step above its tile (the bar's tile begins 12 points down, `BottomBar`).
    static let solesBelowContent: CGFloat = 8
}
