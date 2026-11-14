import AuthenticationServices
import CryptoKit
import FirebaseAuth
import Foundation
import GoogleSignIn
import UIKit

// Signing in to the online backup on the iPhone (phase 4h; ADR-012, ADR-016): Sign in with Apple,
// which Apple asks for wherever another company's sign-in is offered, and Sign in with Google, so
// that someone who bought the backup on Android finds the same account here. Either way the
// account is Firebase's, and its id names the backup's folder, as on Android.

/// How a sign-in ended.
enum BackupSignInResult {
    case signedIn(id: String, email: String)
    case cancelled
    case offline
    case failed(String)
}

@MainActor
enum BackupSignIn {
    /// The nonce of the Sign in with Apple request under way: Apple signs its hash into the
    /// token, and Firebase checks the two match, so that a token cannot be replayed.
    private static var appleNonce: String?

    /// Fills in Sign in with Apple's request, for the system's own button.
    static func prepare(_ request: ASAuthorizationAppleIDRequest) {
        let nonce = randomNonce()
        appleNonce = nonce
        request.requestedScopes = [.email]
        request.nonce = sha256(nonce)
    }

    /// Apple's answer to the system's button, given to Firebase.
    static func apple(_ result: Result<ASAuthorization, Error>) async -> BackupSignInResult {
        switch result {
        case .failure(let error):
            if (error as? ASAuthorizationError)?.code == .canceled { return .cancelled }
            return .failed("Apple \((error as NSError).code)")
        case .success(let authorization):
            guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
                  let token = credential.identityToken.flatMap({ String(data: $0, encoding: .utf8) }),
                  let nonce = appleNonce
            else { return .failed("Apple: no identity token") }
            let firebase = OAuthProvider.appleCredential(
                withIDToken: token,
                rawNonce: nonce,
                fullName: credential.fullName
            )
            return await signIn(with: firebase)
        }
    }

    /// Google's own sheet, then Firebase.
    static func google() async -> BackupSignInResult {
        guard FirebaseCloud.shared.start(),
              let clientID = FirebaseCloud.shared.googleClientID,
              let presenter = topViewController()
        else { return .failed("Google: not set up in this build") }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        do {
            let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: presenter)
            guard let idToken = result.user.idToken?.tokenString else {
                return .failed("Google: no ID token")
            }
            let firebase = GoogleAuthProvider.credential(
                withIDToken: idToken,
                accessToken: result.user.accessToken.tokenString
            )
            return await signIn(with: firebase)
        } catch {
            let failure = error as NSError
            if failure.domain == kGIDSignInErrorDomain && failure.code == GIDSignInError.canceled.rawValue {
                return .cancelled
            }
            if failure.domain == NSURLErrorDomain { return .offline }
            return .failed("Google \(failure.code)")
        }
    }

    /// Signs out of Firebase and Google on this iPhone. The cloud keeps what it has.
    static func signOut() {
        guard FirebaseCloud.shared.start() else { return }
        try? Auth.auth().signOut()
        GIDSignIn.sharedInstance.signOut()
    }

    /// Whether the account signed in was made with Sign in with Apple.
    static var isAppleAccount: Bool {
        guard FirebaseCloud.shared.start() else { return false }
        return Auth.auth().currentUser?.providerData.contains { $0.providerID == "apple.com" } == true
    }

    /// Before an account made with Sign in with Apple is deleted, Apple asks for its tokens to be
    /// revoked (ADR-016): Apple is asked once more for a fresh authorization code, and Firebase,
    /// which holds the Sign in with Apple key, revokes with it. False if the person closed
    /// Apple's sheet or it did not work; nothing is deleted then.
    static func revokeApple() async -> Bool {
        let request = ASAuthorizationAppleIDProvider().createRequest()
        prepare(request)
        guard let authorization = try? await AppleAuthorization().run(request),
              let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let code = credential.authorizationCode.flatMap({ String(data: $0, encoding: .utf8) })
        else { return false }
        do {
            try await Auth.auth().revokeToken(withAuthorizationCode: code)
            return true
        } catch {
            return false
        }
    }

    private static func signIn(with credential: AuthCredential) async -> BackupSignInResult {
        do {
            let result = try await Auth.auth().signIn(with: credential)
            return .signedIn(id: result.user.uid, email: result.user.email ?? "")
        } catch {
            let failure = error as NSError
            if AuthErrorCode(rawValue: failure.code) == .networkError { return .offline }
            return .failed("Firebase \(failure.code)")
        }
    }

    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        var top = scene?.windows.first { $0.isKeyWindow }?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }

    private static func randomNonce() -> String {
        var bytes = [UInt8](repeating: 0, count: 32)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        return bytes.map { String(format: "%02x", $0) }.joined()
    }

    private static func sha256(_ text: String) -> String {
        SHA256.hash(data: Data(text.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}

/// Apple's sign-in sheet shown from code, for the authorization code a deletion needs. Apple
/// calls its delegate on the main thread.
private final class AppleAuthorization: NSObject, ASAuthorizationControllerDelegate,
    ASAuthorizationControllerPresentationContextProviding {
    private var continuation: CheckedContinuation<ASAuthorization, Error>?
    // Held for as long as the sheet is open.
    private var controller: ASAuthorizationController?
    private var anchor = ASPresentationAnchor()

    @MainActor
    func run(_ request: ASAuthorizationAppleIDRequest) async throws -> ASAuthorization {
        anchor = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow } ?? ASPresentationAnchor()
        return try await withCheckedThrowingContinuation { continuation in
            self.continuation = continuation
            let controller = ASAuthorizationController(authorizationRequests: [request])
            controller.delegate = self
            controller.presentationContextProvider = self
            self.controller = controller
            controller.performRequests()
        }
    }

    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization authorization: ASAuthorization
    ) {
        continuation?.resume(returning: authorization)
        continuation = nil
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        continuation?.resume(throwing: error)
        continuation = nil
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        anchor
    }
}
