import SwiftUI

/// A setting chosen in steps, as Android's StepperRow: its name at the left, and at the right its
/// value between a minus and a plus. A button is greyed out at its end of the range. The value
/// has a fixed width, so the buttons stay where they are while it changes and a thumb can press
/// again.
struct StepperRow: View {
    let name: String
    let value: String
    let canLower: Bool
    let canRaise: Bool
    let lower: () -> Void
    let raise: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Text(name)
                .font(Typography.body(15))
                .foregroundStyle(Palette.text)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 8)
            StepButton(systemName: "minus", label: "Less", enabled: canLower, action: lower)
            Text(value)
                .font(Typography.figure(15))
                .foregroundStyle(Palette.text)
                .frame(width: 74)
                .multilineTextAlignment(.center)
            StepButton(systemName: "plus", label: "More", enabled: canRaise, action: raise)
        }
        .padding(.vertical, 4)
    }
}

private struct StepButton: View {
    let systemName: String
    let label: String
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 15, weight: .bold))
                .frame(width: 38, height: 38)
                .background(Palette.controlFill, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                .foregroundStyle(Palette.text)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.35)
        .accessibilityLabel(label)
    }
}
