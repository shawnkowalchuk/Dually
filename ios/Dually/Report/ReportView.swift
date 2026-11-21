import DuallyShared
import MessageUI
import SwiftUI

// The report for the accountant on the iPhone (phase 4g), opened from Trips for the month on
// screen, as Android's Report screen is: the month's Business trips and kilometres, then the PDF
// and the CSV, emailed to the accountant (Mail's own screen, with the shared subject and first
// lines) or shared any other way. A report sent from Mail is recorded as sent, so the month
// counts as submitted and the next report of it is a revision; one shared another way is
// recorded with "Mark as sent", as on Android.
@MainActor
final class ReportModel: ObservableObject {
    @Published private(set) var month: ReportMonthText?
    @Published private(set) var made: MadeReport?
    @Published var failure: String?
    @Published private(set) var year: Int
    @Published private(set) var monthNumber: Int
    /// Android's three periods (2026-11-15 on the iPhone): 0 a month, 1 a date range, 2 a year.
    @Published var kind = 0
    @Published var rangeFirst: Date
    @Published var rangeLast: Date
    /// Every report recorded as sent, newest first.
    @Published private(set) var sentList: [SentReportText] = []
    /// The offer to delete the shown month's GPS points, or nil.
    @Published private(set) var points: MonthPointsText?
    /// What became of the last press on that offer.
    @Published private(set) var pointsNote: String?

    let core: DuallyCore
    private let fonts = ReportFonts()

    init(core: DuallyCore, year: Int, month: Int) {
        self.core = core
        self.year = year
        self.monthNumber = month
        let first = Calendar.current.date(from: DateComponents(year: year, month: month, day: 1)) ?? Date()
        let end = Calendar.current.date(byAdding: DateComponents(month: 1, day: -1), to: first) ?? first
        rangeFirst = first
        rangeLast = min(end, Date())
    }

    func load() async {
        made = nil
        month = try? await core.report.month(
            kind: Int32(kind), year: Int32(year), month: Int32(monthNumber),
            firstDay: epochDay(rangeFirst), lastDay: epochDay(rangeLast)
        )
        sentList = (try? await core.report.sentList()) ?? []
        points = kind == 0 ? try? await core.report.monthPoints(year: Int32(year), month: Int32(monthNumber)) : nil
    }

    /// A day as the shared code counts it: days since 1 January 1970, in the iPhone's calendar.
    private func epochDay(_ date: Date) -> Int64 {
        let parts = Calendar.current.dateComponents([.year, .month, .day], from: date)
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(identifier: "UTC")!
        let midnight = utc.date(from: parts) ?? date
        return Int64((midnight.timeIntervalSince1970 / 86_400).rounded(.down))
    }

    func setOption(_ change: @escaping () async throws -> Void) async {
        try? await change()
        await load()
    }

    func deletePoints() async {
        guard let shown = points else { return }
        if let freed = try? await core.report.deleteMonthPoints(year: Int32(year), month: Int32(monthNumber)) {
            let size = ByteCountFormatter.string(fromByteCount: freed.int64Value, countStyle: .file)
            pointsNote = "\(size) of GPS points deleted. Every trip and the report are as they were."
        } else {
            pointsNote = "The GPS points of \(shown.monthName) could not be deleted. They are as they were. The Log says why."
        }
        await load()
    }

    func keepPoints() async {
        try? await core.report.keepMonthPoints(year: Int32(year), month: Int32(monthNumber))
        await load()
    }

    /// A later month (or for a whole year, a later year) than the shown one has begun.
    var canGoForward: Bool {
        let now = Calendar.current.dateComponents([.year, .month], from: Date())
        if kind == 2 { return year < (now.year ?? year) }
        return (year, monthNumber) < (now.year ?? year, now.month ?? monthNumber)
    }

    func step(by months: Int) async {
        let index = year * 12 + (monthNumber - 1) + (kind == 2 ? months * 12 : months)
        let now = Calendar.current.dateComponents([.year, .month], from: Date())
        guard index <= (now.year ?? year) * 12 + (now.month ?? monthNumber) - 1 else { return }
        year = index / 12
        monthNumber = index % 12 + 1
        await load()
    }

    /// Makes the two files in the app's temporary folder.
    func make() async {
        do {
            let files = try await core.report.files(
                kind: Int32(kind), year: Int32(year), month: Int32(monthNumber),
                firstDay: epochDay(rangeFirst), lastDay: epochDay(rangeLast), measure: fonts
            )
            let folder = FileManager.default.temporaryDirectory.appendingPathComponent("reports", isDirectory: true)
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            let pdf = folder.appendingPathComponent(files.pdfName)
            let csv = folder.appendingPathComponent(files.csvName)
            try ReportPdf.data(files.pages, fonts: fonts).write(to: pdf, options: .atomic)
            try Data(files.csv.utf8).write(to: csv, options: .atomic)
            made = MadeReport(pdf: pdf, csv: csv, subject: files.subject, body: files.body,
                              accountant: files.accountant, pageCount: files.pages.count)
            failure = nil
        } catch {
            failure = "The report could not be made: \(error.localizedDescription)"
        }
    }

    func recordSent() async {
        try? await core.report.recordSent(
            kind: Int32(kind), year: Int32(year), month: Int32(monthNumber),
            firstDay: epochDay(rangeFirst), lastDay: epochDay(rangeLast)
        )
        await load()
        // A month sent takes its reminder away.
        await Reminders.plan(core: core)
    }
}

/// The two files of a report that was made, and its email.
struct MadeReport {
    let pdf: URL
    let csv: URL
    let subject: String
    let body: String
    let accountant: String?
    let pageCount: Int
}

struct ReportView: View {
    @StateObject private var model: ReportModel
    let back: () -> Void
    let openSettings: () -> Void
    @State private var mailing = false
    @State private var sentByMail = false
    /// The run across the foot of the screen after Mail sent the report (2026-11-16, as on
    /// Android after "I sent it"), with whether its sounds are heard.
    @State private var run: (pictures: RunPictures, heard: Bool)?
    @Environment(\.scenePhase) private var scenePhase

    init(core: DuallyCore, year: Int, month: Int, back: @escaping () -> Void, openSettings: @escaping () -> Void) {
        _model = StateObject(wrappedValue: ReportModel(core: core, year: year, month: month))
        self.back = back
        self.openSettings = openSettings
    }

    var body: some View {
        PinnedScreen(title: "Report", onBack: back) {
            // Android's three periods: a month, any days, a whole year.
            Picker("Period", selection: $model.kind) {
                Text("Month").tag(0)
                Text("Date range").tag(1)
                Text("Year").tag(2)
            }
            .pickerStyle(.segmented)
            .onChange(of: model.kind) { _ in Task { await model.load() } }
            if model.kind == 2 {
                Text("1 January to 31 December. The report opens with a page of the year's months, Business and Personal, then lists the trips. Like a date range, it never marks a month as submitted.")
                    .font(Typography.body(13))
                    .foregroundStyle(Palette.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if model.kind == 1 {
                Tile {
                    DatePicker("From", selection: $model.rangeFirst, in: ...model.rangeLast, displayedComponents: .date)
                    DatePicker("To", selection: $model.rangeLast, in: model.rangeFirst...Date(), displayedComponents: .date)
                    Text("Any days you choose. Such a report is kept in the list of sent reports, and never marks a month as submitted.")
                        .font(Typography.body(13))
                        .foregroundStyle(Palette.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .font(Typography.body(15))
                .foregroundStyle(Palette.text)
                .onChange(of: model.rangeFirst) { _ in Task { await model.load() } }
                .onChange(of: model.rangeLast) { _ in Task { await model.load() } }
            }
            if let month = model.month {
                HStack {
                    Text(month.title)
                        .font(Typography.display(26))
                        .foregroundStyle(Palette.text)
                    Spacer()
                    if model.kind != 1 {
                        MonthArrow(systemName: "chevron.left", label: model.kind == 2 ? "Year before" : "Month before") {
                            Task { await model.step(by: -1) }
                        }
                        MonthArrow(systemName: "chevron.right", label: model.kind == 2 ? "Year after" : "Month after") {
                            Task { await model.step(by: 1) }
                        }
                        .disabled(!model.canGoForward)
                        .opacity(model.canGoForward ? 1 : 0.35)
                    }
                }
                Tile(ground: Palette.accentTile) {
                    Text("Business").font(Typography.body(13, weight: .semibold))
                    HStack(alignment: .firstTextBaseline, spacing: 4) {
                        Text(month.distance).font(Typography.figure(30))
                        Text(month.unit).font(Typography.body(14))
                    }
                    Text(month.tripCount == 1 ? "1 business trip" : "\(month.tripCount) business trips")
                        .font(Typography.body(13))
                }
                .foregroundStyle(Palette.markLetter)
                if month.sentBefore {
                    Text("A report of this month was sent before. The next one is a revision that replaces it.")
                        .font(Typography.body(14))
                        .foregroundStyle(Palette.amber)
                }
                if let missing = month.missingForSending {
                    Tile {
                        Text("Before it can be emailed, Settings needs \(missing).")
                            .font(Typography.body(15))
                            .foregroundStyle(Palette.amber)
                            .fixedSize(horizontal: false, vertical: true)
                        WideButton(title: "Open Settings", filled: false, action: openSettings)
                    }
                }
                actions(month)
                if sentByMail {
                    Text("Sent, and recorded as sent.")
                        .font(Typography.body(14))
                        .foregroundStyle(Palette.textSecondary)
                }
                if let failure = model.failure {
                    Text(failure).font(Typography.body(14)).foregroundStyle(Palette.amber)
                }
                if let points = model.points {
                    PointsTile(points: points, delete: { Task { await model.deletePoints() } }, keep: {
                        Task { await model.keepPoints() }
                    })
                }
                if let note = model.pointsNote {
                    Text(note).font(Typography.body(14)).foregroundStyle(Palette.textSecondary)
                }
                OptionsTile(month: month, model: model)
                SentListTile(sent: model.sentList)
            }
        }
        .task { await model.load() }
        .overlay {
            if let run {
                GeometryReader { geometry in
                    MascotRunView(player: RunPlayer(
                        pictures: run.pictures,
                        screenWidth: geometry.size.width,
                        soundsHeard: run.heard
                    ) { self.run = nil })
                }
            }
        }
        // Cut short when Dually is put away, as on Android; leaving the screen ends it too.
        .onChange(of: scenePhase) { phase in if phase != .active { run = nil } }
        .sheet(isPresented: $mailing) {
            if let made = model.made {
                MailComposer(made: made) { sent in
                    mailing = false
                    if sent {
                        sentByMail = true
                        Task {
                            await model.recordSent()
                            await startRun()
                        }
                    }
                }
                .ignoresSafeArea()
            }
        }
    }

    /// One run for each report Mail sent, where the iPhone does not ask for less motion. Its
    /// sounds are heard when either of Dually's two sounds is switched on, as the party's pop.
    private func startRun() async {
        guard !UIAccessibility.isReduceMotionEnabled, let pictures = RunPictures() else { return }
        let heard = (try? await model.core.party.popHeard())?.boolValue ?? true
        run = (pictures, heard)
    }

    @ViewBuilder
    private func actions(_ month: ReportMonthText) -> some View {
        if let made = model.made {
            VStack(spacing: 8) {
                if month.missingForSending == nil && MFMailComposeViewController.canSendMail() {
                    WideButton(title: "Email to the accountant", filled: true) { mailing = true }
                }
                HStack(spacing: 8) {
                    ShareLink(item: made.pdf) { ShareLabel(title: "Share PDF") }
                    ShareLink(item: made.csv) { ShareLabel(title: "Share CSV") }
                }
                WideButton(title: "Mark as sent", filled: false) { Task { await model.recordSent() } }
                Text("Use Mark as sent for a report you sent some other way.")
                    .font(Typography.body(13))
                    .foregroundStyle(Palette.textSecondary)
            }
        } else if month.missingForPdf == nil {
            WideButton(title: "Make the report", filled: true) { Task { await model.make() } }
        }
    }
}

/// Android's Report options: what a report holds beyond the Business trips, kept for every report.
private struct OptionsTile: View {
    let month: ReportMonthText
    @ObservedObject var model: ReportModel
    private var report: IosReport { TripSession.shared.core.report }

    var body: some View {
        Tile {
            Text("Report options")
                .font(Typography.body(17, weight: .semibold))
                .foregroundStyle(Palette.text)
            option("List Personal trips too", month.listPersonal) { on in try await report.setListPersonal(on: on) }
            option("Show the Business use percentage", month.businessShare) { on in try await report.setBusinessShare(on: on) }
            option("Show each trip's odometer", month.tripOdometer) { on in try await report.setTripOdometer(on: on) }
            Text("Kept for every report you make, the PDF and the CSV.")
                .font(Typography.body(13))
                .foregroundStyle(Palette.textSecondary)
        }
    }

    private func option(_ title: String, _ on: Bool, _ set: @escaping (Bool) async throws -> Void) -> some View {
        Toggle(title, isOn: Binding(get: { on }, set: { value in Task { await model.setOption { try await set(value) } } }))
            .font(Typography.body(15))
            .foregroundStyle(Palette.text)
            .tint(Palette.accent)
    }
}

/// Android's offer to delete a sent month's GPS points: the routes, never the trips.
private struct PointsTile: View {
    let points: MonthPointsText
    let delete: () -> Void
    let keep: () -> Void

    private var words: String {
        let size = ByteCountFormatter.string(fromByteCount: points.bytes, countStyle: .file)
        let count = Int(points.tripCount)
        return count == 1
            ? "Your \(points.monthName) report is sent, and its 1 trip keeps its distance, times and addresses. Its GPS points, the route it drove, take \(size) on this phone. Delete them to free the room?"
            : "Your \(points.monthName) report is sent, and its \(count) trips keep their distance, times and addresses. Their GPS points, the route of each drive, take \(size) on this phone. Delete them to free the room?"
    }

    var body: some View {
        Tile {
            Text(points.title)
                .font(Typography.body(17, weight: .semibold))
                .foregroundStyle(Palette.text)
            Text(words)
                .font(Typography.body(14))
                .foregroundStyle(Palette.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 8) {
                WideButton(title: "Delete GPS points", filled: false, action: delete)
                WideButton(title: "Keep", filled: false, action: keep)
            }
        }
    }
}

/// Android's list of sent reports, newest first.
private struct SentListTile: View {
    let sent: [SentReportText]

    var body: some View {
        Tile {
            Text("Sent reports")
                .font(Typography.body(17, weight: .semibold))
                .foregroundStyle(Palette.text)
            if sent.isEmpty {
                Text("No report has been sent yet.")
                    .font(Typography.body(14))
                    .foregroundStyle(Palette.textSecondary)
            }
            ForEach(Array(sent.enumerated()), id: \.element.id) { index, report in
                if index > 0 { Divider().overlay(Palette.controlFill) }
                HStack(alignment: .firstTextBaseline) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(report.period)
                            .font(Typography.body(15, weight: .semibold))
                            .foregroundStyle(Palette.text)
                        Text(report.line)
                            .font(Typography.body(13))
                            .foregroundStyle(Palette.textSecondary)
                        if let revision = report.revision {
                            Text(revision)
                                .font(Typography.body(13))
                                .foregroundStyle(Palette.textSecondary)
                        }
                        if report.fromOtherPhone {
                            Text("Sent from your other phone")
                                .font(Typography.body(13))
                                .foregroundStyle(Palette.textSecondary)
                        }
                    }
                    Spacer()
                    Text(report.distance)
                        .font(Typography.figure(15))
                        .foregroundStyle(Palette.text)
                }
                .padding(.vertical, 4)
            }
        }
    }
}

private struct ShareLabel: View {
    let title: String

    var body: some View {
        Text(title)
            .font(Typography.body(16, weight: .bold))
            .frame(maxWidth: .infinity, minHeight: 46)
            .background(Palette.controlFill, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .foregroundStyle(Palette.text)
    }
}

private struct MonthArrow: View {
    let systemName: String
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 16, weight: .bold))
                .frame(width: 44, height: 44)
                .background(Palette.controlFill, in: Circle())
                .foregroundStyle(Palette.text)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

/// Mail's own screen with the report attached, addressed to the accountant.
private struct MailComposer: UIViewControllerRepresentable {
    let made: MadeReport
    let done: (Bool) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(done: done) }

    func makeUIViewController(context: Context) -> MFMailComposeViewController {
        let mail = MFMailComposeViewController()
        mail.mailComposeDelegate = context.coordinator
        if let accountant = made.accountant { mail.setToRecipients([accountant]) }
        mail.setSubject(made.subject)
        mail.setMessageBody(made.body, isHTML: false)
        if let pdf = try? Data(contentsOf: made.pdf) {
            mail.addAttachmentData(pdf, mimeType: "application/pdf", fileName: made.pdf.lastPathComponent)
        }
        if let csv = try? Data(contentsOf: made.csv) {
            mail.addAttachmentData(csv, mimeType: "text/csv", fileName: made.csv.lastPathComponent)
        }
        return mail
    }

    func updateUIViewController(_ controller: MFMailComposeViewController, context: Context) {}

    final class Coordinator: NSObject, MFMailComposeViewControllerDelegate {
        let done: (Bool) -> Void
        init(done: @escaping (Bool) -> Void) { self.done = done }

        func mailComposeController(
            _ controller: MFMailComposeViewController,
            didFinishWith result: MFMailComposeResult,
            error: Error?
        ) {
            done(result == .sent)
        }
    }
}
