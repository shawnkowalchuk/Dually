import BackgroundTasks
import DuallyShared
import Foundation

// The online backup once a day on the iPhone (2026-11-16), as Android's WorkManager runs it once a
// day: a background app refresh that uploads what an earlier upload could not, so a change whose
// upload failed while the app was asleep does not wait for the next time it is opened. iOS picks
// the moment (about a day after the last, when the iPhone has a connection); nothing runs where
// nobody is signed in to the backup. It closes the `[DEBT]` "The iPhone has no daily upload".
enum DailyBackup {
    static let identifier = "top.dually.triptracker.backup"

    /// Registers the task. Called once, while the app launches, before it finishes launching.
    static func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: nil) { task in
            guard let refresh = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            run(refresh)
        }
    }

    /// Asks iOS for the next run, about a day from now. Asked again at each run and each start.
    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: identifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 24 * 60 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }

    private static func run(_ task: BGAppRefreshTask) {
        schedule()
        let upload = Task { @MainActor in
            let core = TripSession.shared.core
            guard FirebaseCloud.shared.available, core.cloud.account() != nil else {
                task.setTaskCompleted(success: true)
                return
            }
            let done = (try? await core.cloud.upload(reason: "the daily upload"))?.boolValue ?? false
            task.setTaskCompleted(success: done)
        }
        task.expirationHandler = { upload.cancel() }
    }
}
