import DuallyShared
import SwiftUI

// Service on the iPhone (2026-11-14; Shawn: "also missing the service button at the bottom"), the
// bar's fourth button as on Android: each item that needs looking after, when it is due, Done,
// and its history. Every rule and word is the shared code's (IosService); this lays them out as
// Android's Service screen does.
@MainActor
final class ServiceModel: ObservableObject {
    @Published private(set) var text: ServiceText?
    @Published private(set) var failed = false
    let core: DuallyCore

    init(core: DuallyCore) {
        self.core = core
    }

    func load() async {
        text = try? await core.service.read()
    }

    /// Runs a change, then reads the book again; says so on the screen if it was not stored.
    func change(_ work: @escaping () async throws -> KotlinBoolean) async {
        let stored = (try? await work())?.boolValue ?? false
        failed = !stored
        await load()
    }
}

/// The shared Service words, as Android's strings.xml has them.
func serviceWords(_ name: String) -> String {
    EnglishServiceWordsKt.englishServiceText(name: name)
}

struct ServiceView: View {
    @ObservedObject var model: ServiceModel
    @State private var editing: ItemChoice?
    @State private var finishing: DoneChoice?
    @State private var opened: Int64?
    @State private var deleting: ServiceItemText?

    var body: some View {
        PinnedScreen(title: serviceWords("service_title")) {
            if let text = model.text {
                if text.items.isEmpty {
                    Tile {
                        Text(serviceWords("service_none"))
                            .font(Typography.body(15))
                            .foregroundStyle(Palette.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                ForEach(text.items, id: \.id) { item in
                    ItemTile(
                        item: item,
                        open: opened == item.id,
                        toggle: { opened = opened == item.id ? nil : item.id },
                        done: { finishing = DoneChoice(item: item) },
                        edit: { editing = ItemChoice(item: item) },
                        delete: { deleting = item }
                    )
                }
                if model.failed {
                    Text(serviceWords("settings_could_not_save"))
                        .font(Typography.body(14))
                        .foregroundStyle(Palette.amber)
                }
                WideButton(title: serviceWords("service_add"), filled: false) {
                    editing = ItemChoice(item: nil)
                }
            } else {
                Text(serviceWords("service_reading"))
                    .font(Typography.body(15))
                    .foregroundStyle(Palette.textSecondary)
            }
        }
        .task { await model.load() }
        .sheet(item: $editing) { choice in
            if let text = model.text {
                ItemSheet(model: model, text: text, item: choice.item) { editing = nil }
            }
        }
        .sheet(item: $finishing) { choice in
            if let text = model.text {
                DoneSheet(model: model, text: text, item: choice.item) { finishing = nil }
            }
        }
        .confirmationDialog(
            serviceWords("service_delete_title"),
            isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }),
            titleVisibility: .visible,
            presenting: deleting
        ) { item in
            Button(serviceWords("service_delete"), role: .destructive) {
                Task { await model.change { try await model.core.service.delete(id: item.id) } }
            }
            Button(serviceWords("action_cancel"), role: .cancel) {}
        } message: { _ in
            Text(serviceWords("service_delete_text"))
        }
    }
}

/// The item whose Done is open.
private struct DoneChoice: Identifiable {
    let item: ServiceItemText
    var id: Int64 { item.id }
}

/// The form opened for an item, or for a new one.
private struct ItemChoice: Identifiable {
    let item: ServiceItemText?
    var id: Int64 { item?.id ?? 0 }
}

/// One item: its name, a tick or the exclamation mark, when it is due, when it was last done,
/// and Done; pressed, Edit, Delete and "Done before".
private struct ItemTile: View {
    let item: ServiceItemText
    let open: Bool
    let toggle: () -> Void
    let done: () -> Void
    let edit: () -> Void
    let delete: () -> Void

    private var attention: Bool { item.state != "OK" }

    var body: some View {
        Tile {
            Button(action: toggle) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(item.name)
                        .font(Typography.body(17, weight: .semibold))
                        .foregroundStyle(Palette.text)
                    HStack(alignment: .top, spacing: 8) {
                        Image(systemName: attention ? "exclamationmark.circle.fill" : "checkmark.circle.fill")
                            .foregroundStyle(attention ? Palette.amber : Palette.accent)
                            .accessibilityHidden(true)
                        Text(item.due)
                            .font(Typography.body(15))
                            .foregroundStyle(attention ? Palette.amber : Palette.text)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Text(item.lastDone)
                        .font(Typography.body(13))
                        .foregroundStyle(Palette.textSecondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            WideButton(title: serviceWords("service_done"), filled: attention, action: done)
            if open {
                HStack(spacing: 8) {
                    WideButton(title: serviceWords("service_edit"), filled: false, action: edit)
                    WideButton(title: serviceWords("service_delete"), filled: false, action: delete)
                }
                if !item.history.isEmpty {
                    Text(serviceWords("service_history"))
                        .font(Typography.body(13, weight: .semibold))
                        .foregroundStyle(Palette.textSecondary)
                        .padding(.top, 4)
                    ForEach(item.history, id: \.self) { line in
                        Text(line)
                            .font(Typography.body(14))
                            .foregroundStyle(Palette.text)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
        }
    }
}

/// A new item or a change to one, as Android's ItemDialog: the ready-made names, the name, the
/// two intervals, and when it was last done. Save stays off until the form can be stored.
private struct ItemSheet: View {
    @ObservedObject var model: ServiceModel
    let text: ServiceText
    let item: ServiceItemText?
    let close: () -> Void
    @State private var name: String
    @State private var every: String
    @State private var months: String
    @State private var lastDay: Date
    @State private var lastOdometer: String

    init(model: ServiceModel, text: ServiceText, item: ServiceItemText?, close: @escaping () -> Void) {
        self.model = model
        self.text = text
        self.item = item
        self.close = close
        _name = State(initialValue: item?.name ?? "")
        _every = State(initialValue: item?.every ?? "")
        _months = State(initialValue: item?.months ?? "")
        let day = item.map { ($0.lastYear, $0.lastMonth, $0.lastDay) }
            ?? (text.todayYear, text.todayMonth, text.todayDay)
        _lastDay = State(initialValue: ServiceDays.date(day.0, day.1, day.2))
        _lastOdometer = State(initialValue: item?.lastOdometer ?? text.newOdometer)
    }

    private var canSave: Bool {
        model.core.service.canSave(name: name, every: every, months: months, lastOdometer: lastOdometer)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    let rows = stride(from: 0, to: text.presets.count, by: 2).map {
                        Array(text.presets[$0..<min($0 + 2, text.presets.count)])
                    }
                    ForEach(rows.indices, id: \.self) { row in
                        HStack(spacing: 8) {
                            ForEach(rows[row], id: \.name) { preset in
                                PresetButton(title: preset.name, on: name == preset.name) {
                                    name = preset.name
                                    every = preset.every
                                    months = preset.months
                                }
                            }
                        }
                    }
                    ServiceField(label: serviceWords("service_item_name"), text: $name)
                    Text(serviceWords("service_item_either"))
                        .font(Typography.body(13))
                        .foregroundStyle(Palette.textSecondary)
                    ServiceField(label: text.everyDistanceLabel, text: $every).keyboardType(.numberPad)
                    ServiceField(label: serviceWords("service_item_every_months"), text: $months)
                        .keyboardType(.numberPad)
                    DatePicker(
                        serviceWords("service_item_last_done"),
                        selection: $lastDay,
                        in: ...ServiceDays.date(text.todayYear, text.todayMonth, text.todayDay),
                        displayedComponents: .date
                    )
                    .font(Typography.body(16))
                    .foregroundStyle(Palette.text)
                    ServiceField(label: text.lastOdometerLabel, text: $lastOdometer).keyboardType(.numberPad)
                    WideButton(title: serviceWords("service_save"), filled: canSave) {
                        guard canSave else { return }
                        let day = ServiceDays.calendar.dateComponents([.year, .month, .day], from: lastDay)
                        Task {
                            await model.change {
                                try await model.core.service.save(
                                    id: item?.id ?? 0,
                                    name: name,
                                    every: every,
                                    months: months,
                                    year: Int32(day.year ?? 2026),
                                    month: Int32(day.month ?? 1),
                                    day: Int32(day.day ?? 1),
                                    lastOdometer: lastOdometer
                                )
                            }
                            close()
                        }
                    }
                    .opacity(canSave ? 1 : 0.5)
                }
                .padding(20)
            }
            .background(Palette.page.ignoresSafeArea())
            .navigationTitle(serviceWords(item == nil ? "service_item_new" : "service_item_edit"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(serviceWords("action_cancel"), action: close).foregroundStyle(Palette.text)
                }
            }
        }
        // A press beside it does not close it, as on Android, where typing was lost that way.
        .interactiveDismissDisabled(true)
    }
}

/// Done for an item, as Android's DoneDialog: today, at the odometer now (which can be changed),
/// with a cost and a note if he likes.
private struct DoneSheet: View {
    @ObservedObject var model: ServiceModel
    let text: ServiceText
    let item: ServiceItemText
    let close: () -> Void
    @State private var odometer: String
    @State private var cost = ""
    @State private var note = ""

    init(model: ServiceModel, text: ServiceText, item: ServiceItemText, close: @escaping () -> Void) {
        self.model = model
        self.text = text
        self.item = item
        self.close = close
        _odometer = State(initialValue: item.odometerNow)
    }

    var body: some View {
        let problems = model.core.service.doneProblems(odometer: odometer, cost: cost)
        let right = problems.odometer == nil && problems.cost == nil
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text(serviceWords("service_done_text"))
                        .font(Typography.body(15))
                        .foregroundStyle(Palette.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                    ServiceField(label: text.doneOdometerLabel, text: $odometer).keyboardType(.numberPad)
                    if let wrong = problems.odometer { Problem(text: wrong) }
                    ServiceField(label: serviceWords("service_done_cost"), text: $cost).keyboardType(.decimalPad)
                    if let wrong = problems.cost { Problem(text: wrong) }
                    ServiceField(label: serviceWords("service_done_note"), text: $note)
                    WideButton(title: serviceWords("service_done"), filled: right) {
                        guard right else { return }
                        Task {
                            await model.change {
                                try await model.core.service.done(id: item.id, odometer: odometer, cost: cost, note: note)
                            }
                            close()
                        }
                    }
                    .opacity(right ? 1 : 0.5)
                }
                .padding(20)
            }
            .background(Palette.page.ignoresSafeArea())
            .navigationTitle(item.doneTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(serviceWords("action_cancel"), action: close).foregroundStyle(Palette.text)
                }
            }
        }
        .interactiveDismissDisabled(true)
    }
}

/// Days in the Gregorian calendar in the phone's zone, as the shared code reads them.
enum ServiceDays {
    static let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .current
        return calendar
    }()

    static func date(_ year: Int32, _ month: Int32, _ day: Int32) -> Date {
        calendar.date(from: DateComponents(year: Int(year), month: Int(month), day: Int(day), hour: 12)) ?? Date()
    }
}

/// A field of the forms, in the look of the edit sheet's.
struct ServiceField: View {
    let label: String
    @Binding var text: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(Typography.body(13, weight: .semibold))
                .foregroundStyle(Palette.textSecondary)
            TextField(label, text: $text)
                .font(Typography.body(16))
                .padding(12)
                .background(Palette.controlFill, in: RoundedRectangle(cornerRadius: 12))
                .foregroundStyle(Palette.text)
                .accessibilityLabel(label)
        }
    }
}

private struct Problem: View {
    let text: String

    var body: some View {
        Text(text)
            .font(Typography.body(14))
            .foregroundStyle(Palette.amber)
            .fixedSize(horizontal: false, vertical: true)
    }
}

/// A ready-made name: the one the name field holds is filled, as the edit sheet's choices.
private struct PresetButton: View {
    let title: String
    let on: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(Typography.body(15, weight: .bold))
                .frame(maxWidth: .infinity, minHeight: 42)
                .background(on ? Palette.accent : Palette.controlFill, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                .foregroundStyle(on ? Palette.markLetter : Palette.text)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}
