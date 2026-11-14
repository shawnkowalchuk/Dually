import DuallyShared
import FirebaseAuth
import FirebaseCore
import FirebaseFirestore
import Foundation

// Firebase on the iPhone, for the online backup (phase 4h; ADR-012, ADR-016), as the shared code
// asks it (IosCloud's AppleCloud): Android's FirestoreCloudStore and CloudUnlockCheck. What is
// sent, when and why is the shared code's; this only carries it.
//
// Firebase starts here the first time the backup needs it, and never before: an app with nobody
// signed in makes no connection (ADR-012, "Firebase stays silent until backup is on"). Its
// settings come from the GoogleService-Info.plist a build step copies in from ~/keys; a build
// without it has no backup to offer.
final class FirebaseCloud: AppleCloud {
    static let shared = FirebaseCloud()

    /// The functions' region, as Android's (`CloudFirebase.functionUrl`).
    private static let region = "northamerica-northeast1"
    /// The field of a backup document that holds its JSON (firebase/firestore.rules).
    private static let jsonField = "json"

    private let options: FirebaseOptions?

    private init() {
        if let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") {
            options = FirebaseOptions(contentsOfFile: path)
        } else {
            options = nil
        }
    }

    /// Whether this build carries Firebase's settings.
    var available: Bool { options != nil }

    /// The iPhone's Google sign-in client, from the same settings.
    var googleClientID: String? { options?.clientID }

    /// Starts Firebase if it has not started yet. False in a build without its settings.
    @discardableResult
    func start() -> Bool {
        guard let options else { return false }
        if FirebaseApp.app() == nil {
            FirebaseApp.configure(options: options)
            // As on Android: Firestore's copy on the phone is in memory only (ADR-012).
            let settings = FirestoreSettings()
            settings.cacheSettings = MemoryCacheSettings()
            Firestore.firestore().settings = settings
        }
        return true
    }

    func signedInAccount() -> String? {
        guard start() else { return nil }
        return Auth.auth().currentUser?.uid
    }

    func apply(account: String, documents: [CloudDocument], removals: [String], reply: CloudReply) {
        guard start() else {
            reply.refused(reason: "No Firebase settings in this build")
            return
        }
        let firestore = Firestore.firestore()
        let batch = firestore.batch()
        for path in removals {
            batch.deleteDocument(firestore.document("users/\(account)/\(path)"))
        }
        for document in documents {
            batch.setData(
                [Self.jsonField: document.json],
                forDocument: firestore.document("users/\(account)/\(document.path)")
            )
        }
        batch.commit { error in
            guard let error else {
                reply.done()
                return
            }
            let (message, refused) = Self.kind(of: error)
            if refused { reply.refused(reason: message) } else { reply.unreachable(reason: message) }
        }
    }

    func read(account: String, path: String?, reply: CloudReadReply) {
        guard start() else {
            reply.refused(reason: "No Firebase settings in this build")
            return
        }
        let firestore = Firestore.firestore()
        Task {
            do {
                var found: [String: String] = [:]
                if let path {
                    let document = try await firestore.document("users/\(account)/\(path)")
                        .getDocument(source: .server)
                    if let json = document.data()?[Self.jsonField] as? String { found[path] = json }
                } else {
                    for folder in ["trips", "backup"] {
                        let all = try await firestore.collection("users/\(account)/\(folder)")
                            .getDocuments(source: .server)
                        for document in all.documents {
                            if let json = document.data()[Self.jsonField] as? String {
                                found["\(folder)/\(document.documentID)"] = json
                            }
                        }
                    }
                }
                reply.documents(byPath: found)
            } catch {
                let (message, refused) = Self.kind(of: error)
                if refused { reply.refused(reason: message) } else { reply.unreachable(reason: message) }
            }
        }
    }

    func holdsUnlock(account: String, reply: CloudHoldsReply) {
        guard start() else {
            reply.unknown()
            return
        }
        Firestore.firestore().document("unlocks/\(account)").getDocument(source: .server) { document, error in
            if error != nil || document == nil {
                reply.unknown()
            } else {
                reply.holds(unlocked: document?.exists == true)
            }
        }
    }

    /// The server's functions are called over HTTPS directly, as on Android
    /// (`data/cloud/CloudCallable.kt`), with the account's Firebase ID token.
    func call(name: String, body: String, reply: CloudCallReply) {
        guard start(),
              let user = Auth.auth().currentUser,
              let project = options?.projectID,
              let address = URL(string: "https://\(Self.region)-\(project).cloudfunctions.net/\(name)")
        else {
            reply.offline()
            return
        }
        user.getIDToken { token, _ in
            guard let token else {
                reply.offline()
                return
            }
            var request = URLRequest(url: address, timeoutInterval: 60)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
            request.httpBody = Data(body.utf8)
            URLSession.shared.dataTask(with: request) { data, response, _ in
                guard let answer = response as? HTTPURLResponse else {
                    reply.offline()
                    return
                }
                let text = data.flatMap { String(data: $0, encoding: .utf8) } ?? ""
                reply.answered(code: Int32(answer.statusCode), body: text)
            }.resume()
        }
    }

    /// Firestore's error as the backup knows it: refused by the rules (no unlock, or signed out),
    /// or anything else, tried again later. Its code only, since it goes to the Log.
    private static func kind(of error: Error) -> (String, Bool) {
        let failure = error as NSError
        guard failure.domain == FirestoreErrorDomain else {
            return ("\(failure.domain) \(failure.code)", false)
        }
        let code = FirestoreErrorCode.Code(rawValue: failure.code)
        let refused = code == .permissionDenied || code == .unauthenticated
        return ("Firestore \(failure.code)", refused)
    }
}
