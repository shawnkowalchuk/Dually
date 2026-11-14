import SwiftUI

/// A full-width button: orange for the main choice, quiet for the others. The trip sheet's
/// buttons (phase 4c) and Setup's (phase 4d).
struct WideButton: View {
    let title: String
    let filled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(Typography.body(16, weight: .bold))
                .frame(maxWidth: .infinity, minHeight: 46)
                .background(filled ? Palette.accent : Palette.controlFill,
                            in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                .foregroundStyle(filled ? Palette.markLetter : Palette.text)
        }
        .buttonStyle(.plain)
    }
}
