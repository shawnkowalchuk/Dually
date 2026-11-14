import DuallyShared
import SwiftUI
import UniformTypeIdentifiers

// Settings on the iPhone (phase 4g), laid out as Android's Settings screen: Setup first, then
// Units and Driving alert side by side, the work hours, and the trip numbers. Each press is stored
// at once and applies from the next trip; there is no Save, as on Android.
struct SettingsView: View {
    @StateObject private var model: SettingsModel
    @ObservedObject var setup: SetupModel
    let openSetup: () -> Void
    private let core: DuallyCore

    init(core: DuallyCore, setup: SetupModel, openSetup: @escaping () -> Void) {
        self.core = core
        _model = StateObject(wrappedValue: SettingsModel(core: core))
        self.setup = setup
        self.openSetup = openSetup
    }

    var body: some View {
        PinnedScreen(title: "Settings") {
            SetupLinkTile(screen: setup.screen, open: openSetup)
            if let text = model.text {
                HStack(alignment: .top, spacing: 12) {
                    UnitsTile(miles: text.miles) { Task { await model.setMiles(!text.miles) } }
                    DrivingAlertTile(on: text.drivingAlert) { on in Task { await model.setDrivingAlert(on) } }
                }
                ReportDetailsTile(text: text, model: model)
                WorkHoursTile(text: text, model: model)
                TripNumbersTile(text: text, model: model)
                YourDataTile(core: core)
            }
        }
        .task {
            setup.refresh()
            await model.load()
        }
    }
}

/// Setup's count, and the way to it, as Android's SetupLinkTile at the top of Settings.
private struct SetupLinkTile: View {
    let screen: SetupScreen?
    let open: () -> Void

    var body: some View {
        Button(action: open) {
            Tile {
                HStack {
                    Text("Setup")
                        .font(Typography.body(17, weight: .semibold))
                        .foregroundStyle(Palette.text)
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Palette.textSecondary)
                }
                if let screen {
                    Text("\(screen.ready) of \(screen.total) ready")
                        .font(Typography.display(22))
                        .foregroundStyle(screen.complete ? Palette.text : Palette.amber)
                    Text(screen.complete
                         ? "Trips start and end with your vehicle."
                         : "Until it is finished, trips don't start by themselves.")
                        .font(Typography.body(14))
                        .foregroundStyle(Palette.textSecondary)
                }
            }
        }
        .buttonStyle(.plain)
    }
}

/// Kilometres or miles: one button, as Android's Units tile ("Tap for miles").
private struct UnitsTile: View {
    let miles: Bool
    let toggle: () -> Void

    var body: some View {
        Button(action: toggle) {
            Tile {
                Text("Units")
                    .font(Typography.body(14))
                    .foregroundStyle(Palette.textSecondary)
                Text(miles ? "Miles" : "Kilometres")
                    .font(Typography.body(18, weight: .semibold))
                    .foregroundStyle(Palette.text)
                Text(miles ? "Tap for kilometres" : "Tap for miles")
                    .font(Typography.body(13))
                    .foregroundStyle(Palette.textSecondary)
            }
        }
        .buttonStyle(.plain)
        .frame(maxHeight: .infinity)
    }
}

/// The driving alert's switch, as Android's tile: "Tell me when I drive without a trip".
private struct DrivingAlertTile: View {
    let on: Bool
    let set: (Bool) -> Void

    var body: some View {
        Tile {
            Text("Driving alert")
                .font(Typography.body(14))
                .foregroundStyle(Palette.textSecondary)
            Text("Tell me when I drive without a trip")
                .font(Typography.body(14, weight: .semibold))
                .foregroundStyle(Palette.text)
                .fixedSize(horizontal: false, vertical: true)
            Toggle("Driving alert", isOn: Binding(get: { on }, set: set))
                .labelsHidden()
                .tint(Palette.accent)
        }
        .frame(maxHeight: .infinity)
    }
}

/// The work schedule, as Android's: the seven day buttons, the hours, the week's sum, and what
/// becomes of a trip outside them.
private struct WorkHoursTile: View {
    let text: SettingsText
    @ObservedObject var model: SettingsModel

    var body: some View {
        Tile {
            Text("Work hours · trips in these hours are Business")
                .font(Typography.body(17, weight: .semibold))
                .foregroundStyle(Palette.text)
            HStack(spacing: 6) {
                ForEach(Array(text.days.enumerated()), id: \.offset) { index, day in
                    Button {
                        Task { await model.setWorkDay(index, tracked: !day.tracked) }
                    } label: {
                        Text(day.name)
                            .font(Typography.body(13, weight: .semibold))
                            .frame(maxWidth: .infinity, minHeight: 38)
                            .background(day.tracked ? Palette.accent : Palette.controlFill,
                                        in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                            .foregroundStyle(day.tracked ? Palette.markLetter : Palette.text)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("\(day.name), \(day.tracked ? "work day" : "not a work day")")
                }
            }
            .padding(.top, 4)
            HStack {
                TimeField(label: "Start", minute: Int(text.startMinute)) { start in
                    Task { await model.setHours(start: start, end: Int(text.endMinute)) }
                }
                Spacer()
                Text("to").font(Typography.body(14)).foregroundStyle(Palette.textSecondary)
                Spacer()
                TimeField(label: "End", minute: Int(text.endMinute)) { end in
                    Task { await model.setHours(start: Int(text.startMinute), end: end) }
                }
            }
            .padding(.top, 6)
            if let refused = model.hoursRefused {
                Text(refused)
                    .font(Typography.body(14))
                    .foregroundStyle(Palette.amber)
            }
            Text(text.week)
                .font(Typography.body(14))
                .foregroundStyle(Palette.textSecondary)
            if !text.sameHoursEveryDay {
                Text("Your work days have hours of their own. Changing the hours here gives every day these.")
                    .font(Typography.body(13))
                    .foregroundStyle(Palette.amber)
            }
            Text("Trips outside the work hours")
                .font(Typography.body(15, weight: .semibold))
                .foregroundStyle(Palette.text)
                .padding(.top, 10)
            HStack(spacing: 8) {
                WideButton(title: "Save as Personal", filled: !text.ignoreOutsideHours) {
                    Task { await model.setIgnoreOutside(false) }
                }
                WideButton(title: "Ignore them", filled: text.ignoreOutsideHours) {
                    Task { await model.setIgnoreOutside(true) }
                }
            }
            Text(text.ignoreOutsideHours
                 ? "Recorded, then left out of the list and the totals. On Trips, switch on \"Show deleted and discarded\" to see one and count it."
                 : "Recorded and saved as Personal.")
                .font(Typography.body(13))
                .foregroundStyle(Palette.textSecondary)
        }
    }
}

/// A time of day, picked with iOS's own time picker, as Android's uses the platform's clock.
private struct TimeField: View {
    let label: String
    let minute: Int
    let set: (Int) -> Void

    var body: some View {
        DatePicker(
            label,
            selection: Binding(
                get: { Self.date(minute) },
                set: { set(Self.minute(of: $0)) }
            ),
            displayedComponents: .hourAndMinute
        )
        .labelsHidden()
        .datePickerStyle(.compact)
        .tint(Palette.accent)
        .accessibilityLabel(label)
    }

    private static func date(_ minute: Int) -> Date {
        Calendar.current.date(bySettingHour: minute / 60, minute: minute % 60, second: 0, of: Date()) ?? Date()
    }

    private static func minute(of date: Date) -> Int {
        let parts = Calendar.current.dateComponents([.hour, .minute], from: date)
        return (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
    }
}

/// Android's "Trips" tile: the three numbers of the trip rules and the distance adjustment.
private struct TripNumbersTile: View {
    let text: SettingsText
    @ObservedObject var model: SettingsModel

    var body: some View {
        Tile {
            Text("Trips · applies from the next trip")
                .font(Typography.body(17, weight: .semibold))
                .foregroundStyle(Palette.text)
            StepperRow(
                name: "Wait after the truck disconnects",
                value: text.grace,
                canLower: text.graceShorter,
                canRaise: text.graceLonger,
                lower: { Task { await model.stepGrace(longer: false) } },
                raise: { Task { await model.stepGrace(longer: true) } }
            )
            StepperRow(
                name: "End a trip after the truck has not moved for",
                value: text.parkedLimit,
                canLower: text.parkedShorter,
                canRaise: text.parkedLonger,
                lower: { Task { await model.stepParked(longer: false) } },
                raise: { Task { await model.stepParked(longer: true) } }
            )
            StepperRow(
                name: "Shortest trip that counts",
                value: text.shortestTrip,
                canLower: text.shortestShorter,
                canRaise: text.shortestLonger,
                lower: { Task { await model.stepShortest(longer: false) } },
                raise: { Task { await model.stepShortest(longer: true) } }
            )
            StepperRow(
                name: "Distance adjustment",
                value: text.adjustment,
                canLower: text.adjustDown,
                canRaise: text.adjustUp,
                lower: { Task { await model.stepAdjustment(up: false) } },
                raise: { Task { await model.stepAdjustment(up: true) } }
            )
            Text("If Dually's trips come out shorter or longer than your odometer, adjust them here. It changes the trip being recorded and every later one; a trip that has ended keeps its distance.")
                .font(Typography.body(13))
                .foregroundStyle(Palette.textSecondary)
        }
    }
}

/// The report's details, as Android's "Report for the accountant" tile: each stored as it is
/// typed; the address only once it is a whole one, outlined in amber until then.
private struct ReportDetailsTile: View {
    let text: SettingsText
    @ObservedObject var model: SettingsModel
    @State private var name = ""
    @State private var company = ""
    @State private var vehicle = ""
    @State private var email = ""
    @State private var emailRefused = false
    @State private var loaded = false

    var body: some View {
        Tile {
            Text("Report for the accountant")
                .font(Typography.body(17, weight: .semibold))
                .foregroundStyle(Palette.text)
            field("Your name", "[Your name]", $name) { typed in Task { await model.setReportText("name", typed) } }
            field("Company", "[Company]", $company) { typed in Task { await model.setReportText("company", typed) } }
            field("Vehicle", "Make, model and plate", $vehicle) { typed in Task { await model.setReportText("vehicle", typed) } }
            field("Accountant's email address", "name@example.com", $email, email: true) { typed in
                Task { emailRefused = !(await model.setAccountantEmail(typed)) }
            }
            if emailRefused {
                Text("Not saved: this is not a complete email address. It has to look like name@example.com, with nothing else in the field.")
                    .font(Typography.body(13))
                    .foregroundStyle(Palette.amber)
            }
            Text("A report cannot be made without your name, or emailed without the address. Company and vehicle can be left empty, and are then left off the report.")
                .font(Typography.body(13))
                .foregroundStyle(Palette.textSecondary)
        }
        .onAppear {
            guard !loaded else { return }
            loaded = true
            name = text.reportName
            company = text.reportCompany
            vehicle = text.reportVehicle
            email = text.accountantEmail
        }
    }

    private func field(
        _ label: String,
        _ example: String,
        _ value: Binding<String>,
        email: Bool = false,
        changed: @escaping (String) -> Void
    ) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(Typography.body(13)).foregroundStyle(Palette.textSecondary)
            TextField(example, text: value)
                .font(Typography.body(16))
                .foregroundStyle(Palette.text)
                .padding(10)
                .background(Palette.controlFill, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(email && emailRefused ? Palette.amber : .clear, lineWidth: 1.5)
                )
                .keyboardType(email ? .emailAddress : .default)
                .textInputAutocapitalization(email ? .never : .words)
                .autocorrectionDisabled(email)
                .onChange(of: value.wrappedValue) { changed($0) }
        }
        .padding(.top, 4)
    }
}

/// Android's "Your data" tile: everything exported to one file (raw GPS points included), which
/// Android imports too, and a file imported here, which replaces what the iPhone holds after
/// a question. A copy of what it held is kept first.
struct YourDataTile: View {
    let core: DuallyCore
    @State private var exported: URL?
    @State private var choosing = false
    @State private var found: (url: URL, check: ImportCheck)?
    @State private var said: String?

    var body: some View {
        Tile {
            Text("Your data")
                .font(Typography.body(17, weight: .semibold))
                .foregroundStyle(Palette.text)
            if let exported {
                ShareLink(item: exported) {
                    Text("Share the export")
                        .font(Typography.body(16, weight: .bold))
                        .frame(maxWidth: .infinity, minHeight: 46)
                        .background(Palette.accent, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .foregroundStyle(Palette.markLetter)
                }
            } else {
                WideButton(title: "Export all data", filled: false) {
                    Task {
                        if let file = try? await core.transfer.exportAll() {
                            exported = URL(fileURLWithPath: file.path)
                            said = file.tripCount == 1 ? "1 trip exported." : "\(file.tripCount) trips exported."
                        } else {
                            said = "A trip is being recorded. End it first, then export."
                        }
                    }
                }
            }
            WideButton(title: "Import data", filled: false) { choosing = true }
            if let said {
                Text(said).font(Typography.body(14)).foregroundStyle(Palette.textSecondary)
            }
            Text("An export holds every trip, report sent and setting, and the GPS points. Dually on Android imports it too.")
                .font(Typography.body(13))
                .foregroundStyle(Palette.textSecondary)
        }
        .fileImporter(isPresented: $choosing, allowedContentTypes: [.json, .data]) { result in
            guard case let .success(url) = result else { return }
            Task { await look(at: url) }
        }
        .alert(
            "Replace everything on this iPhone?",
            isPresented: Binding(get: { found != nil }, set: { if !$0 { found = nil } }),
            presenting: found
        ) { item in
            Button("Replace", role: .destructive) {
                Task { said = try? await core.transfer.importChecked(path: item.url.path, check: item.check) }
            }
            Button("Cancel", role: .cancel) {}
        } message: { item in
            Text("The file holds \(item.check.tripCount) trips\(item.check.hasSettings ? " and settings" : ""). They replace the \(item.check.storedTrips) trips on this iPhone. A copy of those is kept first.")
        }
    }

    /// Copies the chosen file into Dually's own folder (a file from elsewhere may only be read
    /// while it is open), then checks it.
    private func look(at url: URL) async {
        let open = url.startAccessingSecurityScopedResource()
        defer { if open { url.stopAccessingSecurityScopedResource() } }
        let copy = FileManager.default.temporaryDirectory.appendingPathComponent(url.lastPathComponent)
        try? FileManager.default.removeItem(at: copy)
        do {
            try FileManager.default.copyItem(at: url, to: copy)
        } catch {
            said = "That file could not be read."
            return
        }
        guard let check = try? await core.transfer.check(path: copy.path) else { return }
        if let problem = check.problem {
            said = problem
        } else {
            found = (copy, check)
        }
    }
}
