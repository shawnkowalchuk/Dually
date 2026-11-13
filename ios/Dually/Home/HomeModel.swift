import Combine
import DuallyShared
import Foundation

// What Home shows and does, through the shared code (ADR-014). The figures are worked out and
// formatted in Kotlin (DuallyCore.homeFigures), so they say what Android's say; a trip is
// recorded by the shared trip rules and ledger through the app's one TripSession, which the
// Shortcuts automation's actions use too (phase 4d).
@MainActor
final class HomeModel: ObservableObject {
    @Published private(set) var figures: HomeFigures?
    /// The trip being recorded, or nil.
    @Published private(set) var recording: RecordingNow?
    @Published private(set) var failure: String?
    @Published private(set) var recordingFailure: String?

    private let session: TripSession
    var location: LocationRecorder { session.location }

    init(session: TripSession) {
        self.session = session
        session.$recording.assign(to: &$recording)
        session.$failure.assign(to: &$recordingFailure)
    }

    /// Reads everything again, as of now. A trip that was open when the app last stopped has its
    /// GPS started again.
    func refresh() async {
        await session.refresh()
        await readFigures()
    }

    func start() async {
        await session.start()
    }

    func end() async {
        await session.end()
        await readFigures()
    }

    /// The running trip's distance as Home writes it: "12.3".
    func distanceText(_ trip: RecordingNow) -> String {
        session.core.distanceText(metres: trip.metres)
    }

    private func readFigures() async {
        do {
            figures = try await session.core.homeFigures(nowMs: Int64(Date().timeIntervalSince1970 * 1000))
            failure = nil
        } catch {
            failure = error.localizedDescription
        }
    }
}
