import SwiftUI
import UIKit

// Dually's colours on the iPhone (ADR-014): the same values as Android's
// app/src/main/kotlin/.../core/designsystem/theme/Color.kt, under the same names. A value
// changed there is changed here in the same pull request.
//
// Two looks since 2026-11-15, as Android's since 2026-11-12: Dark, and the brand's light page
// (Bone, white cards, Asphalt words). Each colour below holds both values, and the screens take
// the one of the look in force: the app sets it from the Look tile in Settings
// (`.preferredColorScheme`), never from the phone's own setting, and the widget from the look the
// app leaves it. What is drawn the same in both looks, as on Android, uses the fixed values at the
// end: the orange trip card and what is on it, the mark, and the odometer's dark windows.

enum Palette {
    /// The accent of buttons, links, switches and the bar. Hi-Vis Orange on dark; on the light
    /// page a deeper orange, as Android's LightAction, since Hi-Vis Orange is too faint as words
    /// there.
    static let accent = Color(dark: 0xFF5A12, light: 0xB53A08)
    /// Words on [accent]: dark on Hi-Vis Orange, white on the light look's deeper orange.
    static let onAccent = Color(dark: 0x1E1A17, light: 0xFFFFFF)
    /// Amber: what is waiting for the user. On light, Android's browner LightAttentionText.
    static let amber = Color(dark: 0xFFB648, light: 0x6A5200)
    /// The page: Asphalt, or Bone on light.
    static let page = Color(dark: 0x15120F, light: 0xFFF4E8)
    /// The words on the page: Bone, or Asphalt on light.
    static let text = Color(dark: 0xFFF4E8, light: 0x15120F)
    /// Quieter words; on light the brand's quieter brown.
    static let textSecondary = Color(dark: 0xCDBFAF, light: 0x6B5A4C)
    /// A card: dark, or white on light.
    static let tile = Color(dark: 0x221D18, light: 0xFFFFFF)
    /// A control or a line on a card.
    static let controlFill = Color(dark: 0x2E2721, light: 0xF1E4D5)

    // ---- The Log's tags, as Android's logTags ----

    /// The words of a line on the Log.
    static let logText = Color(dark: 0xE3D3C0, light: 0x3B322B)
    /// The tag of a line about the truck's connection (Android's Bluetooth tag).
    static let truckTagFill = Color(dark: 0x2A3350, light: 0xDFE7FF)
    static let truckTagText = Color(dark: 0xC9D6FF, light: 0x24396B)
    /// The tag of an error or a crash.
    static let errorTagFill = Color(dark: 0x3A1C1F, light: 0xFADBDC)
    static let errorTagText = Color(dark: 0xFFB3B3, light: 0x8E1F27)

    // ---- The graph's lines, as Android's chartColors ----

    /// The graph's Business line, and its Personal line.
    static let chartBusiness = Color(dark: 0xE8692E, light: 0xD9561A)
    static let chartPersonal = Color(dark: 0x4C9ED0, light: 0x2F7FB5)
    /// The line over a picked month (Android's Ring).
    static let chartGuide = Color(dark: 0x7A6858, light: 0x8E7C6C)

    // ---- The same in both looks ----

    /// Hi-Vis Orange as a ground: the trip card, the mark, the odometer's last window.
    static let accentTile = Color(hex: 0xFF5A12)
    /// The dark of the letter on the mark, and the words on [accentTile].
    static let markLetter = Color(hex: 0x1E1A17)
    /// Asphalt and Bone, for what stays dark in both looks: the odometer's windows and digits,
    /// and the label of the dark button on the trip card.
    static let asphalt = Color(hex: 0x15120F)
    static let bone = Color(hex: 0xFFF4E8)
}

extension Color {
    /// A colour from its 0xRRGGBB value, as Android's design system writes them.
    init(hex: UInt32) {
        self.init(uiColor: UIColor(hex: hex))
    }

    /// A colour with a value for each look, taken by the look of the view it is drawn in.
    init(dark: UInt32, light: UInt32) {
        self.init(uiColor: UIColor { traits in
            UIColor(hex: traits.userInterfaceStyle == .light ? light : dark)
        })
    }
}

private extension UIColor {
    convenience init(hex: UInt32) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1
        )
    }
}
