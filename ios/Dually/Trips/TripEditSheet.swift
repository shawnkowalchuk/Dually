import DuallyShared
import SwiftUI

// The edit sheet on the iPhone (2026-11-13), as Android's edit screen: a finished trip's day,
// times, addresses, distance and Business or Personal changed by hand, what Dually recorded put
// back, or a trip Dually missed typed in. Every check, save and word is the shared code's
// (TripEditor); this holds what the pickers and fields show and hands it over on each change.
@MainActor
final class TripEditModel: ObservableObject {
    @Published private(set) var text: EditFormText?
    @Published private(set) var notEditable = false
    @Published var date = Date() { didSet { refresh() } }
    @Published var start: Date? { didSet { refresh() } }
    @Published var end: Date? { didSet { refresh() } }
    @Published var nextDay = false { didSet { refresh() } }
    @Published var from = "" { didSet { refresh() } }
    @Published var to = "" { didSet { refresh() } }
    @Published var distance = "" { didSet { refresh() } }
    @Published var chosen: String? { didSet { refresh() } }
    @Published private(set) var kind: KindText?
    @Published private(set) var problems: ProblemsText?
    @Published private(set) var endsOn: String?
    @Published private(set) var failed: String?

    private var editor: TripEditor?
    private let load: () async -> TripEditor?

    init(load: @escaping () async -> TripEditor?) {
        self.load = load
    }

    /// The Gregorian calendar in the phone's zone: what the shared code reads a day in, whatever
    /// calendar the phone shows.
    static let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .current
        return calendar
    }()

    func open() async {
        guard let editor = await load() else {
            notEditable = true
            return
        }
        let opened = editor.text
        self.editor = editor
        let calendar = Self.calendar
        date = calendar.date(from: DateComponents(
            year: Int(opened.year), month: Int(opened.month), day: Int(opened.day), hour: 12
        )) ?? Date()
        start = Self.time(opened.startMinute, on: date)
        end = Self.time(opened.endMinute, on: date)
        nextDay = opened.nextDay
        from = opened.from
        to = opened.to
        distance = opened.distance
        text = opened
        refresh()
    }

    /// Today, the last day the calendar offers.
    var latestDay: Date {
        guard let text else { return Date() }
        return Self.calendar.date(from: DateComponents(
            year: Int(text.latestYear), month: Int(text.latestMonth), day: Int(text.latestDay), hour: 23, minute: 59
        )) ?? Date()
    }

    /// A time not chosen yet starts at the time of day the sheet opened, or just after the start.
    func choose(start isStart: Bool) {
        let now = Date()
        if isStart {
            start = now
        } else {
            end = start ?? now
        }
    }

    var unsaved: Bool { editor.map { $0.unsaved(fields: fields()) } ?? false }

    /// Saves the form. True once it is stored.
    func save() async -> Bool {
        guard let editor else { return false }
        guard let outcome = try? await editor.save(fields: fields()) else {
            failed = EnglishFormWordsKt.englishFormText(name: "trip_edit_save_failed")
            return false
        }
        problems = outcome.problems
        failed = outcome.failed
        return outcome.stored
    }

    /// Puts back what Dually recorded. True once it is done.
    func putBack() async -> Bool {
        guard let editor else { return false }
        let done = (try? await editor.putBack())?.boolValue ?? false
        if !done { failed = EnglishFormWordsKt.englishFormText(name: "trip_edit_save_failed") }
        return done
    }

    private func refresh() {
        guard let editor else { return }
        let now = fields()
        kind = editor.kind(fields: now)
        problems = editor.problems(fields: now)
        endsOn = editor.endsOn(fields: now)
    }

    private func fields() -> EditFields {
        let day = Self.calendar.dateComponents([.year, .month, .day], from: date)
        return EditFields(
            year: Int32(day.year ?? 2026),
            month: Int32(day.month ?? 1),
            day: Int32(day.day ?? 1),
            startMinute: Self.minute(of: start),
            endMinute: Self.minute(of: end),
            nextDay: nextDay,
            from: from,
            to: to,
            distance: distance,
            chosenCategory: chosen
        )
    }

    private static func minute(of time: Date?) -> Int32 {
        guard let time else { return IosTripEditKt.NO_TIME }
        let parts = calendar.dateComponents([.hour, .minute], from: time)
        return Int32((parts.hour ?? 0) * 60 + (parts.minute ?? 0))
    }

    private static func time(_ minute: Int32, on day: Date) -> Date? {
        guard minute != IosTripEditKt.NO_TIME else { return nil }
        return calendar.date(bySettingHour: Int(minute) / 60, minute: Int(minute) % 60, second: 0, of: day)
    }
}

/// The shared form's words, as Android's strings.xml has them.
private func words(_ name: String) -> String {
    EnglishFormWordsKt.englishFormText(name: name)
}

struct TripEditSheet: View {
    @StateObject private var model: TripEditModel
    /// Closes the sheet: true once something was stored.
    let onDone: (Bool) -> Void
    @State private var askingToLeave = false
    @State private var askingToRestore = false
    @State private var saving = false

    init(load: @escaping () async -> TripEditor?, onDone: @escaping (Bool) -> Void) {
        _model = StateObject(wrappedValue: TripEditModel(load: load))
        self.onDone = onDone
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    if model.notEditable {
                        Text(words("trip_edit_not_editable"))
                            .font(Typography.body(15))
                            .foregroundStyle(Palette.amber)
                    } else if let text = model.text {
                        form(text)
                    }
                }
                .padding(20)
            }
            .background(Palette.page.ignoresSafeArea())
            .navigationTitle(model.text?.title ?? "")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        if model.unsaved { askingToLeave = true } else { onDone(false) }
                    }
                    .foregroundStyle(Palette.text)
                }
            }
            .confirmationDialog(words("leave_unsaved_title"), isPresented: $askingToLeave, titleVisibility: .visible) {
                Button(words("leave_unsaved_discard"), role: .destructive) { onDone(false) }
                Button(words("leave_unsaved_keep"), role: .cancel) {}
            } message: {
                Text(words("leave_unsaved_text"))
            }
            .confirmationDialog(words("trip_edit_restore_title"), isPresented: $askingToRestore, titleVisibility: .visible) {
                Button(words("trip_edit_restore_confirm")) {
                    Task { if await model.putBack() { onDone(true) } }
                }
            } message: {
                Text(model.text?.restoreQuestion ?? "")
            }
        }
        .interactiveDismissDisabled(model.unsaved)
        .task { await model.open() }
    }

    @ViewBuilder
    private func form(_ text: EditFormText) -> some View {
        if let intro = text.intro {
            Text(intro)
                .font(Typography.body(14))
                .foregroundStyle(Palette.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        if let recorded = text.recorded {
            Tile {
                Text(words("trip_edit_recorded_title"))
                    .font(Typography.body(13, weight: .semibold))
                    .foregroundStyle(Palette.textSecondary)
                Text(recorded).font(Typography.body(15)).foregroundStyle(Palette.text)
                WideButton(title: words("trip_edit_restore_confirm"), filled: false) { askingToRestore = true }
            }
        }
        whenTile
        whereTile
        distanceTile(text)
        kindTile
        if let failed = model.failed {
            Text(failed).font(Typography.body(14)).foregroundStyle(Palette.amber)
        }
        WideButton(title: text.saveWords, filled: true) {
            guard !saving else { return }
            saving = true
            Task {
                let stored = await model.save()
                saving = false
                if stored { onDone(true) }
            }
        }
    }

    private var whenTile: some View {
        Tile {
            TileTitle(words("trip_edit_when_title"))
            DatePicker("Day", selection: $model.date, in: ...model.latestDay, displayedComponents: .date)
                .font(Typography.body(16))
                .foregroundStyle(Palette.text)
            timeRow(words("trip_edit_start_label"), time: $model.start, isStart: true)
            timeRow(words("trip_edit_end_label"), time: $model.end, isStart: false)
            Toggle(isOn: $model.nextDay) {
                Text(words("trip_edit_ends_next_day")).font(Typography.body(16)).foregroundStyle(Palette.text)
            }
            .tint(Palette.accent)
            if let endsOn = model.endsOn {
                Text(endsOn).font(Typography.body(13)).foregroundStyle(Palette.textSecondary)
            }
            ForEach(model.problems?.times ?? [], id: \.self) { problem in
                Text(problem)
                    .font(Typography.body(14))
                    .foregroundStyle(Palette.amber)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    @ViewBuilder
    private func timeRow(_ label: String, time: Binding<Date?>, isStart: Bool) -> some View {
        HStack {
            Text(label).font(Typography.body(16)).foregroundStyle(Palette.text)
            Spacer()
            if let chosen = time.wrappedValue {
                DatePicker(
                    label,
                    selection: Binding(get: { chosen }, set: { time.wrappedValue = $0 }),
                    displayedComponents: .hourAndMinute
                )
                .labelsHidden()
            } else {
                Button(words("trip_edit_time_not_set")) { model.choose(start: isStart) }
                    .font(Typography.body(16, weight: .semibold))
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(Palette.controlFill, in: Capsule())
                    .foregroundStyle(Palette.text)
            }
        }
    }

    private var whereTile: some View {
        Tile {
            TileTitle(words("trip_edit_where_title"))
            EditField(label: words("trip_edit_from"), text: $model.from)
            EditField(label: words("trip_edit_to"), text: $model.to)
        }
    }

    private func distanceTile(_ text: EditFormText) -> some View {
        Tile {
            TileTitle(text.distanceTitle)
            EditField(label: text.distanceTitle, text: $model.distance)
                .keyboardType(.decimalPad)
            if let problem = model.problems?.distance {
                Text(problem)
                    .font(Typography.body(14))
                    .foregroundStyle(Palette.amber)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var kindTile: some View {
        Tile {
            TileTitle(words("trip_edit_saved_as"))
            HStack(spacing: 8) {
                KindChoice(title: words("trip_business"), on: model.kind?.category == "BUSINESS") {
                    model.chosen = "BUSINESS"
                }
                KindChoice(title: words("trip_personal"), on: model.kind?.category == "PERSONAL") {
                    model.chosen = "PERSONAL"
                }
            }
            if let note = model.kind?.note {
                Text(note)
                    .font(Typography.body(13))
                    .foregroundStyle(Palette.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

private struct TileTitle: View {
    let title: String
    init(_ title: String) { self.title = title }

    var body: some View {
        Text(title)
            .font(Typography.body(13, weight: .semibold))
            .foregroundStyle(Palette.textSecondary)
    }
}

/// A field of the form, in the look of the trip sheet's label field.
private struct EditField: View {
    let label: String
    @Binding var text: String

    var body: some View {
        TextField(label, text: $text)
            .font(Typography.body(16))
            .padding(12)
            .background(Palette.controlFill, in: RoundedRectangle(cornerRadius: 12))
            .foregroundStyle(Palette.text)
            .accessibilityLabel(label)
    }
}

/// Business or Personal: the one a save would store is filled.
private struct KindChoice: View {
    let title: String
    let on: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(Typography.body(16, weight: .bold))
                .frame(maxWidth: .infinity, minHeight: 46)
                .background(on ? Palette.accent : Palette.controlFill, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                .foregroundStyle(on ? Palette.markLetter : Palette.text)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}
