import DuallyShared
import SwiftUI

// The graph of a year's months on the iPhone (2026-11-16), as Android's Trips graph (2026-10-16;
// Shawn: "show on the graph the buseinss and personal mileage on seperate lines"): the year with
// its two steps, the chart, what the picked month adds up to, and every month's figures under it,
// which are the chart's table for whoever does not read the picture.
struct TripGraphView: View {
    let core: DuallyCore
    /// Leads back to Trips.
    let back: () -> Void

    @State private var year = Calendar.current.component(.year, from: Date())
    @State private var graph: YearGraphText?
    @State private var selected: Int?

    var body: some View {
        PinnedScreen(title: "Graph", onBack: back) {
            HStack {
                Text(String(year))
                    .font(Typography.display(26))
                    .foregroundStyle(Palette.text)
                Spacer()
                StepButton(systemName: "chevron.left", label: "Previous year") { step(-1) }
                StepButton(systemName: "chevron.right", label: "Next year") { step(1) }
                    .disabled(!(graph?.canStepForward ?? false))
                    .opacity(graph?.canStepForward ?? false ? 1 : 0.35)
            }
            if let graph {
                Tile {
                    YearChart(graph: graph, selected: $selected)
                    if let index = selected, graph.months.indices.contains(index),
                       graph.months[index].begun {
                        Text(graph.months[index].line)
                            .font(Typography.body(16))
                            .foregroundStyle(Palette.text)
                            .padding(.top, 6)
                    }
                }
                Tile {
                    let begun = graph.months.filter(\.begun)
                    ForEach(Array(begun.enumerated()), id: \.offset) { index, month in
                        Text(month.line)
                            .font(Typography.body(15))
                            .foregroundStyle(Palette.text)
                            .padding(.vertical, 4)
                        if index < begun.count - 1 { Divider() }
                    }
                }
            } else {
                Tile {
                    Text("Reading the trips…")
                        .font(Typography.body(15))
                        .foregroundStyle(Palette.textSecondary)
                }
            }
        }
        .task(id: year) {
            graph = try? await core.trips.yearGraph(year: Int32(year))
            // The running month is picked when its year opens, as on Android.
            selected = graph.flatMap { $0.currentIndex >= 0 ? Int($0.currentIndex) : nil }
        }
    }

    private func step(_ by: Int) {
        if by > 0, !(graph?.canStepForward ?? false) { return }
        selected = nil
        year += by
    }
}

private struct StepButton: View {
    let systemName: String
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 16, weight: .bold))
                .frame(width: 40, height: 40)
                .background(Palette.controlFill, in: Circle())
                .foregroundStyle(Palette.text)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

/// Android's LineChart on the iPhone: a legend over it, the two lines in their fixed colours,
/// each named at its end as well, so that no line is told apart by its colour alone; a quiet
/// grid on round figures from zero; and under each month its letter. A press picks the nearest
/// month: a guide line stands over it and its points grow.
private struct YearChart: View {
    let graph: YearGraphText
    @Binding var selected: Int?

    private let height: CGFloat = 220
    private let startRoom: CGFloat = 44
    private let endRoom: CGFloat = 64
    private let labelRoom: CGFloat = 22
    private let labelSize: CGFloat = 13

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 16) {
                Swatch(name: "Business", color: Palette.chartBusiness)
                Swatch(name: "Personal", color: Palette.chartPersonal)
            }
            GeometryReader { geometry in
                let plot = Plot(
                    left: startRoom,
                    right: geometry.size.width - endRoom,
                    top: labelSize,
                    bottom: height - labelRoom,
                    points: graph.months.count,
                    scaleTop: max(graph.scaleTop, 1)
                )
                Canvas { context, _ in
                    draw(plot, in: &context)
                }
                .contentShape(Rectangle())
                .gesture(SpatialTapGesture().onEnded { tap in
                    let index = plot.nearest(tap.location.x)
                    // A month that has not begun has no point to pick.
                    guard graph.months[index].begun else { return }
                    selected = selected == index ? nil : index
                })
            }
            .frame(height: height)
            .accessibilityElement()
            .accessibilityLabel("Graph of \(String(graph.year)): Business and Personal distance for each month. Each month's figures are listed under it.")
        }
    }

    private func draw(_ plot: Plot, in context: inout GraphicsContext) {
        let steps = Int(graph.gridSteps)
        for step in 0...steps {
            let value = plot.scaleTop * Double(step) / Double(steps)
            let y = plot.y(value)
            var line = Path()
            line.move(to: CGPoint(x: plot.left, y: y))
            line.addLine(to: CGPoint(x: plot.right, y: y))
            context.stroke(line, with: .color(Palette.controlFill), lineWidth: 1)
            context.draw(
                label(Int(value.rounded()).formatted()),
                at: CGPoint(x: 0, y: y),
                anchor: .leading
            )
        }
        let letters = Calendar.current.veryShortMonthSymbols
        for index in 0..<plot.points {
            context.draw(
                label(letters[index % letters.count]),
                at: CGPoint(x: plot.x(index), y: plot.bottom + 4),
                anchor: .top
            )
        }
        if let selected {
            var guide = Path()
            guide.move(to: CGPoint(x: plot.x(selected), y: plot.top))
            guide.addLine(to: CGPoint(x: plot.x(selected), y: plot.bottom))
            context.stroke(guide, with: .color(Palette.chartGuide), lineWidth: 1)
        }
        let business = graph.months.map { $0.begun ? $0.business : nil }
        let personal = graph.months.map { $0.begun ? $0.personal : nil }
        let names = nameCentres(plot, [business, personal])
        series(business, "Business", Palette.chartBusiness, names[0], plot, &context)
        series(personal, "Personal", Palette.chartPersonal, names[1], plot, &context)
    }

    /// One line: its path where it has values, its points ringed in the tile's colour, and its
    /// name after its last point.
    private func series(
        _ values: [Double?], _ name: String, _ color: Color, _ nameY: CGFloat?,
        _ plot: Plot, _ context: inout GraphicsContext
    ) {
        let points = values.enumerated().compactMap { index, value in
            value.map { (index, CGPoint(x: plot.x(index), y: plot.y($0))) }
        }
        guard let last = points.last else { return }
        var path = Path()
        for (at, point) in points.enumerated() {
            if at == 0 { path.move(to: point.1) } else { path.addLine(to: point.1) }
        }
        context.stroke(path, with: .color(color), lineWidth: 2)
        for (index, point) in points {
            let radius: CGFloat = index == selected ? 6 : 4
            context.fill(circle(point, radius + 2), with: .color(Palette.tile))
            context.fill(circle(point, radius), with: .color(color))
        }
        context.draw(label(name), at: CGPoint(x: last.1.x + 8, y: nameY ?? last.1.y), anchor: .leading)
    }

    /// Where each line's name stands: beside its last point, pushed apart where two would cover
    /// each other, upwards only, as on Android.
    private func nameCentres(_ plot: Plot, _ lines: [[Double?]]) -> [CGFloat?] {
        let lineHeight: CGFloat = labelSize + 4
        let ends = lines.enumerated().compactMap { index, values in
            values.last(where: { $0 != nil }).flatMap { $0 }.map { (index, plot.y($0)) }
        }.sorted { $0.1 > $1.1 }
        var placed: [Int: CGFloat] = [:]
        var below = plot.bottom
        for (index, wanted) in ends {
            let centre = min(wanted, below)
            placed[index] = centre
            below = centre - lineHeight
        }
        return lines.indices.map { placed[$0] }
    }

    private func label(_ text: String) -> Text {
        Text(text).font(Typography.body(labelSize)).foregroundColor(Palette.textSecondary)
    }

    private func circle(_ centre: CGPoint, _ radius: CGFloat) -> Path {
        Path(ellipseIn: CGRect(x: centre.x - radius, y: centre.y - radius, width: radius * 2, height: radius * 2))
    }
}

private struct Swatch: View {
    let name: String
    let color: Color

    var body: some View {
        HStack(spacing: 6) {
            Circle().fill(color).frame(width: 10, height: 10)
            Text(name)
                .font(Typography.body(13))
                .foregroundStyle(Palette.textSecondary)
        }
    }
}

/// Where the plot stands, and its scale: zero at [bottom], [scaleTop] at [top].
private struct Plot {
    let left: CGFloat
    let right: CGFloat
    let top: CGFloat
    let bottom: CGFloat
    let points: Int
    let scaleTop: Double

    func x(_ index: Int) -> CGFloat {
        left + (right - left) * CGFloat(index) / CGFloat(max(points - 1, 1))
    }

    func y(_ value: Double) -> CGFloat {
        bottom - (bottom - top) * CGFloat(value / scaleTop)
    }

    func nearest(_ x: CGFloat) -> Int {
        let step = (right - left) / CGFloat(max(points - 1, 1))
        return min(max(Int(((x - left) / step).rounded()), 0), points - 1)
    }
}
