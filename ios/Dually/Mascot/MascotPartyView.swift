import DuallyShared
import QuartzCore
import SwiftUI

// The one-year party on the iPhone (2026-11-16), as Android's (`MascotParty.kt`): the mascot
// fades in in the middle of Home in a party cap, brings out a party popper and pulls its string;
// confetti goes up and comes down round him, and he fades away with it still falling. Five and a
// half seconds, played from Android's two pictures by the shared numbers (`MascotPartyPicture`,
// `MascotPartyPlace`). Home works under him the whole time.

@MainActor
final class PartyPictures {
    let popper: MascotPlayed
    let pop: MascotPlayed

    init?() {
        guard let popper = MascotPlayed("mascot_party_popper"), let pop = MascotPlayed("mascot_party_pop") else {
            return nil
        }
        self.popper = popper
        self.pop = pop
    }
}

@MainActor
final class PartyPlayer: ObservableObject {
    @Published private(set) var image: CGImage?
    @Published private(set) var strength: Double = 0

    let pictures: PartyPictures
    private var began: CFTimeInterval = 0
    private var link: CADisplayLink?
    private var popped = false
    private var over = false
    private let onPop: () -> Void
    private let onDone: (Bool) -> Void

    init(pictures: PartyPictures, onPop: @escaping () -> Void, onDone: @escaping (Bool) -> Void) {
        self.pictures = pictures
        self.onPop = onPop
        self.onDone = onDone
        image = pictures.popper.frame(0)
    }

    func start() {
        guard link == nil else { return }
        let link = CADisplayLink(target: self, selector: #selector(tick(_:)))
        link.add(to: .main, forMode: .common)
        self.link = link
        began = CACurrentMediaTime()
    }

    /// Cut short: put away, or Home left.
    func stop() {
        link?.invalidate()
        link = nil
        if !over {
            over = true
            onDone(popped)
        }
    }

    @objc private func tick(_ link: CADisplayLink) {
        let millis = Int64((link.targetTimestamp - began) * 1000)
        let still = Int64(MascotPartyPicture.shared.STILL_MS)
        let popAt = Int64(MascotPartyPicture.shared.POP_AT_MS)
        let frameMillis = Int64(MascotPicture.shared.FRAME_MS)
        if millis < popAt {
            // He stands still while he fades in, on the popper's first frame; then it plays.
            strength = Double(MascotPartyPicture.shared.fadeIn(sinceMillis: millis))
            image = pictures.popper.frame(millis < still ? 0 : Int((millis - still) / frameMillis))
        } else {
            if !popped {
                popped = true
                onPop()
            }
            let since = millis - popAt
            strength = Double(MascotPartyPicture.shared.fadeOut(sinceMillis: since))
            image = pictures.pop.frame(Int(since / frameMillis))
            if since >= Int64(MascotPartyPicture.shared.POP_MS) { stop() }
        }
    }
}

struct MascotPartyView: View {
    @StateObject private var player: PartyPlayer
    let screen: CGSize

    init(player: @autoclosure @escaping () -> PartyPlayer, screen: CGSize) {
        _player = StateObject(wrappedValue: player())
        self.screen = screen
    }

    var body: some View {
        let place = MascotPartyPlace(screenWidth: Float(screen.width), screenHeight: Float(screen.height))
        let width = CGFloat(place.width)
        let height = CGFloat(place.height)
        let times = CGFloat(place.tall / MascotPicture.shared.TALL)
        ZStack(alignment: .topLeading) {
            Ellipse()
                .fill(RadialGradient(colors: [Color.black.opacity(0.5), .clear], center: .center, startRadius: 0, endRadius: 46 * times))
                .frame(width: 92 * times, height: 14 * times)
                .offset(x: CGFloat(place.axis) - 46 * times,
                        y: height - CGFloat(place.soles) - 3.4 * times - 7 * times)
            if let image = player.image {
                Image(decorative: image, scale: 1)
                    .resizable()
                    .interpolation(.high)
                    .frame(width: width, height: height)
            }
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .opacity(player.strength)
        .offset(x: CGFloat(place.left), y: CGFloat(place.top))
        .frame(width: screen.width, height: screen.height, alignment: .topLeading)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
        .onAppear { player.start() }
        .onDisappear { player.stop() }
    }
}
