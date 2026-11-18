import SwiftUI

/// One button of the [BottomBar].
struct BottomBarEntry: Identifiable {
    /// The screen's name: the bar draws no words, so this is what VoiceOver says.
    let label: String
    let systemImage: String
    let selected: Bool
    let action: () -> Void

    var id: String { label }
}

/// The bar at the foot of the app, as Android's MiloNavigationBar: a rounded tile floating
/// clear of the edges, one icon for each top-level screen and no words, the showing screen's
/// icon in the accent and the others in the grey of quieter words.
struct BottomBar: View {
    let entries: [BottomBarEntry]

    var body: some View {
        HStack(spacing: 0) {
            ForEach(entries) { entry in
                Button(action: entry.action) {
                    Image(systemName: entry.systemImage)
                        .font(.system(size: 21, weight: .semibold))
                        .foregroundStyle(entry.selected ? Palette.accent : Palette.textSecondary)
                        .frame(maxWidth: .infinity, minHeight: 64)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(entry.label)
                .accessibilityAddTraits(entry.selected ? [.isButton, .isSelected] : .isButton)
            }
        }
        .padding(.horizontal, 6)
        .background(Palette.tile, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .padding(.horizontal, 18)
        .padding(.top, 12)
        .padding(.bottom, 8)
    }
}
