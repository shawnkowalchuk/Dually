import CoreGraphics
import Foundation
import ImageIO

// The mascot's pictures on the iPhone (2026-11-16), Android's own files (ADR-007) read by iOS's
// image decoder, which reads WebP since iOS 14: nothing is added to the app for them. Every frame
// of a greeting's two pictures is the same size and from the same camera, so one can take
// another's place without the mascot moving.

/// The frame time of every picture: 24 frames a second, as Android's `MascotPicture.FRAME_MS`.
let mascotFrameSeconds = 0.042

/// A picture with its frames side by side, as Android's sheets: the app draws the frame it wants,
/// because the mascot's place has to change with his step.
final class MascotSheet {
    private let image: CGImage
    private let columns: Int
    private let rows: Int

    /// Reads `name`.webp from the app. Nil if it cannot be read.
    init?(_ name: String, columns: Int, rows: Int) {
        guard let url = Bundle.main.url(forResource: name, withExtension: "webp"),
              let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let image = CGImageSourceCreateImageAtIndex(source, 0, [kCGImageSourceShouldCacheImmediately: true] as CFDictionary)
        else { return nil }
        self.image = image
        self.columns = columns
        self.rows = rows
    }

    /// Frame `index`, counted along each row from the top left.
    func frame(_ index: Int) -> CGImage? {
        let width = image.width / columns
        let height = image.height / rows
        let column = index % columns
        let row = index / columns
        return image.cropping(to: CGRect(x: column * width, y: row * height, width: width, height: height))
    }
}

/// An animated picture, whose frames the app shows one by one by its own clock, so that it knows
/// which is on the screen (Android lets its decoder play them; the iPhone steps through them).
/// Each frame is decoded when it is due, and only the last one is kept.
final class MascotPlayed {
    private let source: CGImageSource
    let frames: Int
    private var cachedIndex = -1
    private var cached: CGImage?

    init?(_ name: String) {
        guard let url = Bundle.main.url(forResource: name, withExtension: "webp"),
              let source = CGImageSourceCreateWithURL(url as CFURL, nil)
        else { return nil }
        let frames = CGImageSourceGetCount(source)
        guard frames > 1 else { return nil }
        self.source = source
        self.frames = frames
        // The first frame drawn now, so that it is there the moment it is due.
        _ = frame(0)
    }

    func frame(_ index: Int) -> CGImage? {
        let wanted = min(max(index, 0), frames - 1)
        if wanted == cachedIndex { return cached }
        cached = CGImageSourceCreateImageAtIndex(source, wanted, nil)
        cachedIndex = wanted
        return cached
    }

    var seconds: Double { Double(frames) * mascotFrameSeconds }
}
