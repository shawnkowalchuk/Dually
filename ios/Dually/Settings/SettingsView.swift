import DuallyShared
import SwiftUI
import UniformTypeIdentifiers

// Settings on the iPhone (phase 4g), laid out as Android's Settings screen: Setup first, then
// Units and Look side by side, the driving alert, the work hours, and the trip numbers. Each press is stored
// at once and applies from the next trip; there is no Save, as on Android.
struct SettingsView: View {
    @StateObject private var model: SettingsModel
    @ObservedObject var setup: SetupModel
    let openSetup: () -> Void
    let openLog: () -> Void
    private let core: DuallyCore

    init(core: DuallyCore, setup: SetupModel, openSetup: @escaping () -> Void, openLog: @escaping () -> Void) {
        self.core = core
        _model = StateObject(wrappedValue: SettingsModel(core: core))
        self.setup = setup
        self.openSetup = openSetup
        self.openLog = openLog
    }

    var body: some View {
        PinnedScreen(title: "Settings") {
            SetupLinkTile(screen: setup.screen, open: openSetup)
            if let text = model.text {
                // Units and Look side by side, the first of the settings, as on Android.
                HStack(alignment: .top, spacing: 12) {
                    UnitsTile(miles: text.miles) { Task { await model.setMiles(!text.miles) } }
                    LookTile(light: text.light) { Task { await model.setLight(!text.light) } }
                }
                DrivingAlertTile(on: text.drivingAlert) { on in Task { await model.setDrivingAlert(on) } }
                // As on Android since 2026-11-12: the online backup stands open under Units.
                CloudBackupTile(core: core)
                OdometerTile(core: core, miles: text.miles)
                ReportDetailsTile(text: text, model: model)
                ReminderTile(text: text, model: model)
                WorkHoursTile(text: text, model: model)
                DailyCheckTile(text: text, model: model)
                TripNumbersTile(text: text, model: model)
                WidgetTile(text: text, model: model)
                YourDataTile(core: core)
                LogLinkTile(open: openLog)
            }
        }
        .task {
            setup.refresh()
            await model.load()
        }
    }
}

/// The way to the Log, as Android's tile, in its words.
private struct LogLinkTile: View {
    let open: () -> Void

    var body: some View {
        Button(action: open) {
            Tile {
                HStack {
                    Text("Log")
                        .font(Typography.body(17, weight: .semibold))
                        .foregroundStyle(Palette.text)
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Palette.textSecondary)
                }
                Text("What Dually did and when: every trip, connection and error. Support may ask for it.")
                    .font(Typography.body(14))
                    .foregroundStyle(Palette.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .buttonStyle(.plain)
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

/// The truck's odometer, as Android's tile in Settings (2026-11-14 on the iPhone, for Service):
/// the figure Dually works out from the last reading and the trips since, and the way to type a
/// new reading. A reading is never changed: a new one is added beside it, with its date.
private struct OdometerTile: View {
    let core: DuallyCore
    /// The unit chosen: the tile is read again when it changes.
    let miles: Bool
    @State private var text: OdometerText?
    @State private var editing = false
    @State private var typed = ""
    @State private var refused = false
    @State private var failed = false

    var body: some View {
        Tile {
            if let text {
                Text(text.title)
                    .font(Typography.body(17, weight: .semibold))
                    .foregroundStyle(Palette.text)
                if let figure = text.figure {
                    Text(figure)
                        .font(Typography.display(28))
                        .foregroundStyle(Palette.text)
                }
                Text(text.madeOf)
                    .font(Typography.body(14))
                    .foregroundStyle(Palette.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                if failed {
                    Text(serviceWords("settings_could_not_save"))
                        .font(Typography.body(14))
                        .foregroundStyle(Palette.amber)
                }
                if editing {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(text.field)
                            .font(Typography.body(13, weight: .semibold))
                            .foregroundStyle(Palette.textSecondary)
                        TextField(text.example, text: $typed)
                            .keyboardType(.numberPad)
                            .font(Typography.body(16))
                            .padding(12)
                            .background(Palette.controlFill, in: RoundedRectangle(cornerRadius: 12))
                            .foregroundStyle(Palette.text)
                            .accessibilityLabel(text.field)
                    }
                    if refused {
                        Text(text.refused)
                            .font(Typography.body(14))
                            .foregroundStyle(Palette.amber)
                    }
                    HStack(spacing: 8) {
                        WideButton(title: serviceWords("action_cancel"), filled: false) {
                            editing = false
                            refused = false
                        }
                        WideButton(title: text.save, filled: true) {
                            Task { await save() }
                        }
                    }
                } else {
                    WideButton(title: text.button, filled: false) {
                        typed = ""
                        editing = true
                    }
                }
            }
        }
        .task(id: miles) { text = try? await core.service.odometer() }
    }

    private func save() async {
        guard core.service.isReading(typed: typed) else {
            refused = true
            return
        }
        let stored = (try? await core.service.saveReading(typed: typed))?.boolValue ?? false
        failed = !stored
        refused = false
        editing = !stored
        text = try? await core.service.odometer()
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

/// Dark or light: one button, as Android's Look tile ("Tap for light").
private struct LookTile: View {
    let light: Bool
    let toggle: () -> Void

    var body: some View {
        Button(action: toggle) {
            Tile {
                Text("Look")
                    .font(Typography.body(14))
                    .foregroundStyle(Palette.textSecondary)
                Text(light ? "Light" : "Dark")
                    .font(Typography.body(18, weight: .semibold))
                    .foregroundStyle(Palette.text)
                Text(light ? "Tap for dark" : "Tap for light")
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
    /// Each day shown by itself, while all days still have the same hours.
    @State private var eachDay = false

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
                            .foregroundStyle(day.tracked ? Palette.onAccent : Palette.text)
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
            // Each work day with hours of its own, as Android's week shown day by day: once the
            // days differ they stay shown each by itself, and the line above gives them one set.
            Toggle("Each day its own hours", isOn: Binding(get: { eachDay || !text.sameHoursEveryDay }, set: { eachDay = $0 }))
                .font(Typography.body(15, weight: .semibold))
                .foregroundStyle(Palette.text)
                .tint(Palette.accent)
                .disabled(!text.sameHoursEveryDay)
            if eachDay || !text.sameHoursEveryDay {
                ForEach(Array(text.days.enumerated()), id: \.offset) { index, day in
                    if day.tracked {
                        HStack {
                            Text(day.name)
                                .font(Typography.body(14, weight: .semibold))
                                .foregroundStyle(Palette.text)
                                .frame(width: 44, alignment: .leading)
                            TimeField(label: "\(day.name) start", minute: Int(day.startMinute)) { start in
                                Task { await model.setDayHours(index, start: start, end: Int(day.endMinute)) }
                            }
                            Spacer()
                            Text("to").font(Typography.body(14)).foregroundStyle(Palette.textSecondary)
                            Spacer()
                            TimeField(label: "\(day.name) end", minute: Int(day.endMinute)) { end in
                                Task { await model.setDayHours(index, start: Int(day.startMinute), end: end) }
                            }
                        }
                    }
                }
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

/// The monthly reminder, as Android's tile, in its words: the switch, the day it starts on, and
/// when it comes.
private struct ReminderTile: View {
    let text: SettingsText
    @ObservedObject var model: SettingsModel

    var body: some View {
        Tile {
            Text("Monthly reminder")
                .font(Typography.body(17, weight: .semibold))
                .foregroundStyle(Palette.text)
            Toggle("Remind me to send the report", isOn: Binding(get: { text.reminderOn }, set: { on in
                Task { await model.setReminder(on) }
            }))
            .font(Typography.body(15))
            .foregroundStyle(Palette.text)
            .tint(Palette.accent)
            if text.reminderOn {
                StepperRow(
                    name: "Start on this day of the month",
                    value: text.reminderDay,
                    canLower: text.reminderEarlier,
                    canRaise: text.reminderLater,
                    lower: { Task { await model.stepReminderDay(later: false) } },
                    raise: { Task { await model.stepReminderDay(later: true) } }
                )
                Text(text.reminderLine)
                    .font(Typography.body(14, weight: .semibold))
                    .foregroundStyle(Palette.text)
                Text("In a month that has fewer days, the reminder starts on its last day. A month without a Business trip brings no reminder. Tap it to open that report.")
                    .font(Typography.body(13))
                    .foregroundStyle(Palette.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

/// The daily check, as Android's tile: on a work day with no trip by the time set, one
/// notification.
private struct DailyCheckTile: View {
    let text: SettingsText
    @ObservedObject var model: SettingsModel

    var body: some View {
        Tile {
            Text("Daily check")
                .font(Typography.body(17, weight: .semibold))
                .foregroundStyle(Palette.text)
            Toggle("Tell me when no trip has been recorded", isOn: Binding(get: { text.dailyCheckOn }, set: { on in
                Task { await model.setDailyCheck(on) }
            }))
            .font(Typography.body(15))
            .foregroundStyle(Palette.text)
            .tint(Palette.accent)
            if text.dailyCheckOn {
                HStack {
                    Text("Check at")
                        .font(Typography.body(15))
                        .foregroundStyle(Palette.text)
                    Spacer()
                    TimeField(label: "Daily check: time", minute: Int(text.dailyCheckMinute)) { minute in
                        Task { await model.setDailyCheckTime(minute) }
                    }
                }
                Text("On a work day, if no trip has been recorded by this time, Dually sends one notification. It catches the day Dually has quietly stopped noticing the truck. If a work day's hours start later, Dually checks from their start. A trip you add by hand does not count.")
                    .font(Typography.body(13))
                    .foregroundStyle(Palette.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

/// The home-screen widget, as Android's tile: its switch and the rate it prices Business
/// kilometres at, for reference.
private struct WidgetTile: View {
    let text: SettingsText
    @ObservedObject var model: SettingsModel
    @State private var typed = ""
    @State private var refused = false

    var body: some View {
        Tile {
            Text("Home-screen widget")
                .font(Typography.body(17, weight: .semibold))
                .foregroundStyle(Palette.text)
            Toggle("The trip, Start and End, and business kilometres in dollars", isOn: Binding(get: { text.widgetOn }, set: { on in
                Task { await model.setWidget(on) }
            }))
            .font(Typography.body(15))
            .foregroundStyle(Palette.text)
            .tint(Palette.accent)
            HStack {
                Text("Rate")
                    .font(Typography.body(15))
                    .foregroundStyle(Palette.text)
                Spacer()
                Text(text.widgetRate)
                    .font(Typography.figure(16))
                    .foregroundStyle(Palette.text)
            }
            HStack(spacing: 8) {
                TextField("0.70", text: $typed)
                    .keyboardType(.decimalPad)
                    .font(Typography.figure(16))
                    .padding(.horizontal, 12)
                    .frame(minHeight: 46)
                    .background(Palette.controlFill, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .accessibilityLabel("Dollars a kilometre")
                WideButton(title: "Save rate", filled: false) {
                    Task {
                        refused = !(await model.setWidgetRate(typed))
                        if !refused { typed = "" }
                    }
                }
            }
            if refused {
                Text(text.widgetRateLimits)
                    .font(Typography.body(13))
                    .foregroundStyle(Palette.amber)
            }
            Text("Home, the widget and the CarPlay screen show your Business kilometres in dollars at this rate, for reference. The report itself stays in kilometres. To add the widget: touch and hold the Home Screen, tap Edit, then Add Widget, then Dually.")
                .font(Typography.body(13))
                .foregroundStyle(Palette.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
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
                        .foregroundStyle(Palette.onAccent)
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
