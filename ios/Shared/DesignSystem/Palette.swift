import SwiftUI

// Dually's colours on the iPhone (ADR-014): the same values as Android's
// app/src/main/kotlin/.../core/designsystem/theme/Color.kt, under the same names. A value
// changed there is changed here in the same pull request. Dark only for now; the light look
// comes with the iPhone's Settings.

enum Palette {
    /// Hi-Vis Orange: the accent. Words on it are dark, never white.
    static let accent = Color(hex: 0xFF5A12)
    /// Amber: what is waiting for the user.
    static let amber = Color(hex: 0xFFB648)
    /// Asphalt: the page.
    static let page = Color(hex: 0x15120F)
    /// Bone: the words on the page.
    static let text = Color(hex: 0xFFF4E8)
    static let textSecondary = Color(hex: 0xCDBFAF)
    /// The dark of the letter on the mark, and the words on the accent.
    static let markLetter = Color(hex: 0x1E1A17)
    /// A card.
    static let tile = Color(hex: 0x221D18)
    /// A control or a line on a card.
    static let controlFill = Color(hex: 0x2E2721)
}

extension Color {
    /// A colour from its 0xRRGGBB value, as Android's design system writes them.
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }
}
