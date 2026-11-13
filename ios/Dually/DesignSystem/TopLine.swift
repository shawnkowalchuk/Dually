import SwiftUI

/// Every screen's top line, as on Android: the mark, "Dually" and the date, then the screen.
struct TopLine: View {
    let screen: String

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
            Text(screen)
                .font(Typography.body(15, weight: .semibold))
                .foregroundStyle(Palette.text)
        }
        .padding(.vertical, 12)
    }
}
