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

    private let core: DuallyCore
    private let fonts = ReportFonts()

    init(core: DuallyCore, year: Int, month: Int) {
        self.core = core
        self.year = year
        self.monthNumber = month
    }

    func load() async {
        made = nil
        month = try? await core.report.month(year: Int32(year), month: Int32(monthNumber))
    }

    /// A later month than the shown one has begun: the forward arrow works.
    var canGoForward: Bool {
        let now = Calendar.current.dateComponents([.year, .month], from: Date())
        return (year, monthNumber) < (now.year ?? year, now.month ?? monthNumber)
    }

    func step(by months: Int) async {
        let index = year * 12 + (monthNumber - 1) + months
        let now = Calendar.current.dateComponents([.year, .month], from: Date())
        guard index <= (now.year ?? year) * 12 + (now.month ?? monthNumber) - 1 else { return }
        year = index / 12
        monthNumber = index % 12 + 1
        await load()
    }

    /// Makes the two files in the app's temporary folder.
    func make() async {
        do {
            let files = try await core.report.files(year: Int32(year), month: Int32(monthNumber), measure: fonts)
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
        try? await core.report.recordSent(year: Int32(year), month: Int32(monthNumber))
        await load()
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

    init(core: DuallyCore, year: Int, month: Int, back: @escaping () -> Void, openSettings: @escaping () -> Void) {
        _model = StateObject(wrappedValue: ReportModel(core: core, year: year, month: month))
        self.back = back
        self.openSettings = openSettings
    }

    var body: some View {
        PinnedScreen(title: "Report", onBack: back) {
            if let month = model.month {
                HStack {
                    Text(month.title)
                        .font(Typography.display(26))
                        .foregroundStyle(Palette.text)
                    Spacer()
                    MonthArrow(systemName: "chevron.left", label: "Month before") { Task { await model.step(by: -1) } }
                    MonthArrow(systemName: "chevron.right", label: "Month after") { Task { await model.step(by: 1) } }
                        .disabled(!model.canGoForward)
                        .opacity(model.canGoForward ? 1 : 0.35)
                }
                Tile(ground: Palette.accent) {
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
            }
        }
        .task { await model.load() }
        .sheet(isPresented: $mailing) {
            if let made = model.made {
                MailComposer(made: made) { sent in
                    mailing = false
                    if sent {
                        sentByMail = true
                        Task { await model.recordSent() }
                    }
                }
                .ignoresSafeArea()
            }
        }
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
