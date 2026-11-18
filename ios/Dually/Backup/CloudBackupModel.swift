import AuthenticationServices
import DuallyShared
import Foundation

// The Online backup tile's state and presses on the iPhone (phase 4h), as Android's
// CloudBackupViewModel: the tile's words come ready from the shared code (IosCloud.tile), and
// each press says what it came to in one line. The purchase or the restore asks for a sign-in
// first where nobody is signed in, and an account that turns out to hold the unlock already is
// not asked to pay again (Android, since 2026-11-12).
@MainActor
final class CloudBackupModel: ObservableObject {
    /// What a press waits for a sign-in to do.
    enum Waiting {
        case purchase, restore
    }

    @Published private(set) var tile: CloudTile?
    @Published private(set) var note: CloudNote?
    @Published private(set) var working = false
    /// The sign-in sheet is open, for [waiting].
    @Published var signingIn = false
    private var waiting: Waiting?
    private var price: String?
    private let core: DuallyCore

    init(core: DuallyCore) {
        self.core = core
    }

    func load() async {
        if price == nil && FirebaseCloud.shared.available { price = await BackupPurchase.price() }
        tile = try? await core.cloud.tile(available: FirebaseCloud.shared.available, price: price)
    }

    /// "Purchase online backup".
    func purchase() {
        if tile?.signedIn != true {
            ask(.purchase)
            return
        }
        work { await self.buy() }
    }

    /// "Restore purchase".
    func restorePurchase() {
        if tile?.signedIn != true {
            ask(.restore)
            return
        }
        work { await self.restore() }
    }

    /// Sign in with Apple's own button answered.
    func signedInWithApple(_ result: Result<ASAuthorization, Error>) {
        signingIn = false
        work { await self.afterSignIn(await BackupSignIn.apple(result)) }
    }

    /// "Sign in with Google".
    func signInWithGoogle() {
        signingIn = false
        work { await self.afterSignIn(await BackupSignIn.google()) }
    }

    func signOut() {
        work {
            BackupSignIn.signOut()
            try? await self.core.cloud.signedOut()
        }
    }

    /// "Merge": this iPhone's trips join the logbook (ADR-017).
    func merge() {
        work { self.note = try? await self.core.cloud.merge() }
    }

    /// "Use only the backup's trips", once the person said yes.
    func useBackupOnly() {
        work { self.note = try? await self.core.cloud.useBackupOnly() }
    }

    /// "Delete my online backup and account", once the person said yes. An account made with
    /// Sign in with Apple has its Apple tokens revoked first (ADR-016).
    func delete() {
        work {
            if BackupSignIn.isAppleAccount {
                guard await BackupSignIn.revokeApple() else { return }
            }
            guard let answer = try? await self.core.cloud.deleteAccount() else { return }
            self.note = answer.note
            if answer.deleted { BackupSignIn.signOut() }
        }
    }

    private func ask(_ what: Waiting) {
        waiting = what
        note = nil
        signingIn = true
    }

    private func afterSignIn(_ result: BackupSignInResult) async {
        let then = waiting
        waiting = nil
        switch result {
        case .signedIn(let id, let email):
            note = try? await core.cloud.signedIn(id: id, email: email)
            // Unlocked already (bought on another phone): nothing to buy.
            if core.cloud.account()?.unlocked == true { return }
            switch then {
            case .purchase: await buy()
            case .restore: await restore()
            case nil: break
            }
        case .cancelled:
            note = nil
        case .offline:
            note = try? await core.cloud.signInFailed(cancelled: false, offline: true, cause: "")
        case .failed(let cause):
            note = try? await core.cloud.signInFailed(cancelled: false, offline: false, cause: cause)
        }
    }

    private func buy() async {
        guard let token = core.cloud.appleAccountToken() else { return }
        switch await BackupPurchase.buy(accountToken: token) {
        case .bought(let signed, let transaction):
            let answer = try? await core.cloud.purchased(signed: signed)
            note = answer?.note
            if answer?.finish == true { await transaction.finish() }
        case .pending:
            note = core.cloud.purchasePending()
        case .cancelled:
            note = nil
        case .notOffered:
            note = core.cloud.notOffered()
        case .failed(let cause):
            note = try? await core.cloud.purchaseFailed(cause: cause)
        }
    }

    private func restore() async {
        note = try? await core.cloud.restorePurchases(signed: await BackupPurchase.owned())
    }

    /// Runs [block] with the buttons held, clears the last note first, and reads the tile again.
    private func work(_ block: @escaping () async -> Void) {
        guard !working else { return }
        working = true
        note = nil
        Task {
            await block()
            await load()
            working = false
        }
    }
}
