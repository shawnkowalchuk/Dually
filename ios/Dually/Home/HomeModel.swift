import DuallyShared
import Foundation

// What Home shows, read from the shared code (ADR-014, phase 4a). The figures are worked out
// and formatted in Kotlin (DuallyCore.homeFigures), so they say exactly what Android's say.
@MainActor
final class HomeModel: ObservableObject {
    @Published private(set) var figures: HomeFigures?
    @Published private(set) var failure: String?

    private let core: DuallyCore

    init(core: DuallyCore) {
        self.core = core
    }

    /// Reads the figures again, as of now.
    func refresh() async {
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        do {
            figures = try await core.homeFigures(nowMs: nowMs)
            failure = nil
        } catch {
            failure = error.localizedDescription
        }
    }
}
