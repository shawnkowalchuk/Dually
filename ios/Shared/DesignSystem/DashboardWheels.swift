import SwiftUI

// A figure as a row of wheels, like a truck's own odometer: Android's DashboardWheels
// (app/.../core/designsystem/component/DashboardWheels.kt, the owner's choice of 2026-10-10,
// "Dashboard wheels") on the iPhone since 2026-11-14, for Home's odometer. Each digit stands in
// a dark cell of its own, with the unit small after the last. While a trip is counted the last
// cell is the accent and its wheel turns slowly on to the next digit; passing from 9 to 0 it
// takes the wheel before it along, as on a dashboard. There is no cell for the tenth.

/// The digit's size, as Android's headlineSmall: the cells are sized from it, as there.
private let digitSize: CGFloat = 26

struct DashboardWheels: View {
    /// The figure in whole kilometres or miles, a digit to a wheel.
    let whole: Int64
    /// While counting: how far the last wheel has turned on from the last digit of [whole] to
    /// the next, from 0 up to 1. Nil for a row that stands.
    let turn: Double?
    /// "km" or "mi".
    let unit: String
    /// What VoiceOver says for the whole row: "123,461 kilometres".
    let spoken: String

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Where the row has rolled to, which follows the figure softly.
    @State private var rolled: Double?

    var body: some View {
        let target = Double(whole) + (turn ?? 0)
        let moving = turn != nil && !reduceMotion
        WheelRow(position: moving ? (rolled ?? target) : Double(whole), counting: turn != nil, unit: unit)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(spoken)
            .onAppear { rolled = target }
            .onChange(of: target) { next in
                // Forwards only, and no more than a few digits at once, as on Android: a trip
                // the truck joined late is set to its figure, not spun there.
                guard moving, let from = rolled, next >= from, next - from <= mostStepsRolled else {
                    rolled = next
                    return
                }
                withAnimation(.spring(response: rollResponse, dampingFraction: 1)) { rolled = next }
            }
    }
}

/// A figure that has moved on by more steps than this at once is shown where it now stands.
private let mostStepsRolled = 5.0

/// Android's soft spring without bounce (stiffness 0.64): the steps the figure arrives in become
/// one movement, some two and a half seconds behind it.
private let rollResponse = 2 * Double.pi / 0.8

/// The cells and the unit, at one position of the row: SwiftUI animates [position], and the
/// digits and the turn of the wheels are worked out from it at each frame.
private struct WheelRow: View, Animatable {
    var position: Double
    let counting: Bool
    let unit: String

    var animatableData: Double {
        get { position }
        set { position = newValue }
    }

    var body: some View {
        let reached = max(0, position.rounded(.down))
        let part = counting ? position - reached : 0
        let wheels = wheelsOf(Int64(reached), rolling: counting)
        HStack(alignment: .bottom, spacing: 8) {
            HStack(spacing: 4) {
                ForEach(wheels.indices, id: \.self) { index in
                    WheelCell(wheel: wheels[index], part: part, accent: counting && index == wheels.count - 1)
                }
            }
            Text(unit)
                .font(Typography.figure(13))
                .foregroundStyle(Palette.textSecondary)
        }
    }
}

/// One wheel in its window: turning, the digit that leaves goes up and the next comes from below.
private struct WheelCell: View {
    let wheel: Wheel
    let part: Double
    let accent: Bool

    var body: some View {
        let height = digitSize * 1.5
        ZStack {
            if wheel.turning {
                digit(wheel.digit).offset(y: -part * height)
                digit(wheel.next).offset(y: (1 - part) * height)
            } else {
                digit(wheel.digit)
            }
        }
        .frame(width: digitSize * 1.12, height: height)
        .background(accent ? Palette.accentTile : Palette.asphalt)
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
    }

    private func digit(_ value: Int) -> some View {
        Text(String(value))
            .font(Typography.figure(digitSize))
            .foregroundStyle(accent ? Palette.asphalt : Palette.bone)
    }
}

/// One wheel at one moment, as Android's WheelPositions.kt: the digit in its window, and whether
/// it turns on to the next with the last wheel (it does while every wheel after it shows 9).
private struct Wheel {
    let digit: Int
    let turning: Bool
    var next: Int { (digit + 1) % 10 }
}

/// The wheels of [steps], the highest first, as Android's `wheelsOf`: one wheel more, a 0 in
/// front, while a row of 9s rolls on.
private func wheelsOf(_ steps: Int64, rolling: Bool) -> [Wheel] {
    let reached = rolling ? steps + 1 : steps
    let count = max(String(steps).count, String(reached).count)
    var place: Int64 = 1
    var lowestFirst: [Wheel] = []
    for _ in 0..<count {
        lowestFirst.append(Wheel(digit: Int((steps / place) % 10), turning: rolling && steps % place == place - 1))
        place *= 10
    }
    return lowestFirst.reversed()
}
