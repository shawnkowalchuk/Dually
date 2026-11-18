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
    /// The odometers as their wheels show them, one for each vehicle (ADR-018), the one a trip
    /// with the truck is driven in counting.
    @Published private(set) var odometers: [HomeOdometerText] = []
    /// Today's finished trips, newest first, as Android's Home lists them.
    @Published private(set) var today: TodayText?
    /// What the last Shortcuts automation said of the truck, or nil before any has run.
    @Published private(set) var truck: TruckTileText?
    /// Last month's report while the monthly reminder says it is waiting, as Android's amber tile.
    @Published private(set) var reportWaiting: ReminderPlan?
    /// The trip being recorded, or nil.
    @Published private(set) var recording: RecordingNow?
    @Published private(set) var failure: String?
    @Published private(set) var recordingFailure: String?

    private let session: TripSession
    private var following: AnyCancellable?
    private var turning: AnyCancellable?
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
        // Today's trips and the truck when a trip starts or ends, or its truck goes and comes
        // back: an automation can do that while Home is open.
        turning = session.$recording
            .map { trip in trip.map { $0.waitingForTruck ? 2 : 1 } ?? 0 }
            .removeDuplicates()
            .sink { [weak self] _ in
                Task { await self?.readTodayAndTruck() }
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
            figures = try await session.core.homeFigures(nowMs: session.core.timeNow())
            failure = nil
        } catch {
            failure = error.localizedDescription
        }
        await readOdometer(session.recording)
        await readTodayAndTruck()
    }

    private func readTodayAndTruck() async {
        today = try? await session.core.trips.today()
        truck = try? await session.core.truckTile(nowMs: session.core.timeNow())
        let reminder = try? await session.core.reminders.reminderPlan(nowMs: session.core.timeNow())
        reportWaiting = reminder?.dueNow == true ? reminder : nil
    }

    private func readOdometer(_ trip: RecordingNow?) async {
        do {
            odometers = try await session.core.homeOdometer.readAll(
                trip: trip,
                nowMs: session.core.timeNow()
            )
        } catch {
            failure = error.localizedDescription
        }
    }
}
