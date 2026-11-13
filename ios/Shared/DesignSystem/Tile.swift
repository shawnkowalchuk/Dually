import SwiftUI

// A card, as Android's Tile draws one: the dark ground, round corners, even padding.
struct Tile<Content: View>: View {
    var ground: Color = Palette.tile
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(18)
        .background(ground, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
    }
}

// Dually's mark: the dark D on the orange square, drawn rather than pictured so it is sharp at
// any size. The same shape as website/mark.svg.
struct DuallyMark: View {
    var size: CGFloat = 32

    var body: some View {
        RoundedRectangle(cornerRadius: size * 34 / 150, style: .continuous)
            .fill(Palette.accent)
            .overlay(MarkLetterShape().fill(Palette.markLetter, style: FillStyle(eoFill: true)))
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}

private struct MarkLetterShape: Shape {
    // website/mark.svg: on a 150 square, translate(75 75) scale(1.1), then the D's outline
    // (M-36 -38 H0, a half ellipse 36 by 38 down to 0 38, H-36) and its counter (the same
    // with -18, 18 by 20). The half ellipses are traced as short straight steps.
    func path(in rect: CGRect) -> Path {
        let scale = 1.1 * rect.width / 150
        func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
            CGPoint(x: rect.midX + x * scale, y: rect.midY + y * scale)
        }
        var path = Path()
        for (left, radiusX, radiusY) in [(CGFloat(-36), CGFloat(36), CGFloat(38)), (-18, 18, 20)] {
            path.move(to: point(left, -radiusY))
            let steps = 48
            for step in 0...steps {
                let angle = -CGFloat.pi / 2 + CGFloat.pi * CGFloat(step) / CGFloat(steps)
                path.addLine(to: point(radiusX * cos(angle), radiusY * sin(angle)))
            }
            path.addLine(to: point(left, radiusY))
            path.closeSubpath()
        }
        return path
    }
}
