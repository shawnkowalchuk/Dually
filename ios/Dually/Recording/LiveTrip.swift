import ActivityKit
import DuallyShared
import Foundation

// The app's half of the trip's Live Activity (phase 4e): started when a trip starts, however it
// started; its distance kept current while the trip records; ended the moment the trip ends, as
// Android's trip notification goes. TripSession tells it after every step of the recorder.
//
// iOS lets an app start a Live Activity only while it is in front, or from an action the user or
// an automation ran (Start a trip, iOS 17 on). A trip that starts in the background otherwise
// (the parked truck driving off) gets its Live Activity when Dually next comes to the front.
@available(iOS 16.1, *)
@MainActor
final class LiveTrip {
    private var activity: Activity<TripActivityAttributes>?
    private var shown: TripActivityAttributes.ContentState?
    private var shownAt = Date.distantPast
    /// The trip iOS refused to start an activity for, so as not to ask again at every fix.
    private var refusedFor: Int64?

    /// Shows [trip], or ends the activity when no trip is being recorded.
    func show(_ trip: RecordingNow?, distance: String, unit: String) {
        guard let trip else {
            end()
            return
        }
        let state = TripActivityAttributes.ContentState(
            distance: distance,
            unit: unit,
            waitingForTruck: trip.waitingForTruck
        )
        if activity?.attributes.tripId != trip.tripId {
            activity = adopt(trip.tripId) ?? start(trip, state)
            shown = activity == nil ? nil : state
            shownAt = Date()
            return
        }
        update(state)
    }

    /// Dually came to the front: a trip iOS refused an activity for in the background gets one.
    func cameToFront() {
        refusedFor = nil
    }

    /// The activity Dually started for [tripId] before a restart, if iOS still shows it. Any
    /// other is a trip that has ended meanwhile, and goes.
    private func adopt(_ tripId: Int64) -> Activity<TripActivityAttributes>? {
        var found: Activity<TripActivityAttributes>?
        for running in Activity<TripActivityAttributes>.activities {
            if running.attributes.tripId == tripId && found == nil {
                found = running
            } else {
                Self.endNow(running)
            }
        }
        return found
    }

    private func start(
        _ trip: RecordingNow,
        _ state: TripActivityAttributes.ContentState
    ) -> Activity<TripActivityAttributes>? {
        guard ActivityAuthorizationInfo().areActivitiesEnabled, refusedFor != trip.tripId else { return nil }
        let attributes = TripActivityAttributes(
            tripId: trip.tripId,
            startedAt: Date(timeIntervalSince1970: Double(trip.startedAtMs) / 1000)
        )
        do {
            if #available(iOS 16.2, *) {
                return try Activity.request(
                    attributes: attributes,
                    content: ActivityContent(state: state, staleDate: nil),
                    pushType: nil
                )
            }
            return try Activity.request(attributes: attributes, contentState: state, pushType: nil)
        } catch {
            refusedFor = trip.tripId
            return nil
        }
    }

    /// A new distance at most every 5 seconds, which is as often as a glance can use it; the
    /// grace period's start or end at once.
    private func update(_ state: TripActivityAttributes.ContentState) {
        guard let activity, state != shown else { return }
        let urgent = state.waitingForTruck != shown?.waitingForTruck
        guard urgent || Date().timeIntervalSince(shownAt) >= 5 else { return }
        shown = state
        shownAt = Date()
        Task {
            if #available(iOS 16.2, *) {
                await activity.update(ActivityContent(state: state, staleDate: nil))
            } else {
                await activity.update(using: state)
            }
        }
    }

    /// Takes [activity] off the Lock Screen and the Dynamic Island at once.
    private static func endNow(_ activity: Activity<TripActivityAttributes>) {
        Task {
            if #available(iOS 16.2, *) {
                await activity.end(nil, dismissalPolicy: .immediate)
            } else {
                await activity.end(using: nil, dismissalPolicy: .immediate)
            }
        }
    }

    private func end() {
        let ending = Activity<TripActivityAttributes>.activities
        activity = nil
        shown = nil
        for running in ending {
            Self.endNow(running)
        }
    }
}
