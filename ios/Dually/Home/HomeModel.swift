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
    /// The odometer as its wheels show it, counting while a trip with the truck is driven.
    @Published private(set) var odometer: HomeOdometerText?
    /// The trip being recorded, or nil.
    @Published private(set) var recording: RecordingNow?
    @Published private(set) var failure: String?
    @Published private(set) var recordingFailure: String?

    private let session: TripSession
    private var following: AnyCancellable?
    var location: LocationRecorder { session.location }

    init(session: TripSession) {
        self.session = session
        session.$recording.assign(to: &$recording)
        session.$failure.assign(to: &$recordingFailure)
        // The odometer follows the trip being recorded, every two seconds at most: the wheels
        // roll softly between its figures, as Android's do.
        following = session.$recording
            .throttle(for: .seconds(2), scheduler: RunLoop.main, latest: true)
            .sink { [weak self] trip in
                Task { await self?.readOdometer(trip) }
            }
    }

    /// Reads everything again, as of now. A trip that was open when the app last stopped has its
    /// GPS started again.
    func refresh() async {
        await session.refresh()
        await readFigures()
    }

    /// Dually came to the front: as [refresh], and a trip that started in the background gets
    /// its Live Activity.
    func cameToFront() async {
        await session.cameToFront()
        await readFigures()
    }

    func start() async {
        await session.start()
    }

    func end() async {
        await session.end()
        await readFigures()
    }

    /// "km" or "mi", as Settings has it.
    var unitWord: String { session.core.unitWord() }

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
        await readOdometer(session.recording)
    }

    private func readOdometer(_ trip: RecordingNow?) async {
        do {
            odometer = try await session.core.homeOdometer.read(
                trip: trip,
                nowMs: Int64(Date().timeIntervalSince1970 * 1000)
            )
        } catch {
            failure = error.localizedDescription
        }
    }
}
