import SwiftUI

// Dually's typefaces on the iPhone, bundled in Fonts/ with their licences: Archivo for words,
// IBM Plex Mono for figures that stand by themselves, as on Android (core/designsystem/theme).

enum Typography {
    /// The wordmark and headlines: Archivo ExtraBold.
    static func display(_ size: CGFloat) -> Font {
        .custom("Archivo", size: size).weight(.heavy)
    }

    /// Running words in Archivo.
    static func body(_ size: CGFloat, weight: Font.Weight = .regular) -> Font {
        .custom("Archivo", size: size).weight(weight)
    }

    /// A figure: IBM Plex Mono SemiBold.
    static func figure(_ size: CGFloat) -> Font {
        .custom("IBMPlexMono-SemiBold", size: size)
    }
}
