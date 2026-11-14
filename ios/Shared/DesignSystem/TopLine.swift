import SwiftUI

/// Every screen's top line, as on Android: the mark, "Dually" and the date, then the screen's
/// name, which Home leaves out (Shawn, 2026-10-09: "remove the home top right corner"): the
/// bottom bar's lit Home button already says which screen it is.
///
/// On a screen opened from another one (Setup from Settings), [onBack] puts an arrowhead before
/// the name, and the two are one button that goes back, as on Android.
struct TopLine: View {
    let screen: String?
    var onBack: (() -> Void)?

    var body: some View {
        HStack(alignment: .center, spacing: 10) {
            DuallyMark(size: 34)
            VStack(alignment: .leading, spacing: 0) {
                Text("Dually")
                    .font(Typography.display(20))
                    .foregroundStyle(Palette.text)
                Text(Date.now.formatted(.dateTime.weekday(.abbreviated).month(.abbreviated).day()))
                    .font(Typography.body(13))
                    .foregroundStyle(Palette.textSecondary)
            }
            Spacer()
            if let screen, let onBack {
                Button(action: onBack) {
                    HStack(spacing: 4) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 14, weight: .bold))
                        Text(screen)
                            .font(Typography.body(15, weight: .semibold))
                    }
                    .foregroundStyle(Palette.text)
                    .frame(minHeight: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Back from \(screen)")
            } else if let screen {
                Text(screen)
                    .font(Typography.body(15, weight: .semibold))
                    .foregroundStyle(Palette.text)
                    .accessibilityAddTraits(.isHeader)
            }
        }
        .padding(.vertical, 12)
    }
}
