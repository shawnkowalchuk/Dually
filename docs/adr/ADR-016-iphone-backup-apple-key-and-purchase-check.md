# ADR-016: The iPhone's online backup: a Sign in with Apple key, and Apple's purchases checked by their signature

Date: 2026-11-14
Status: Accepted. Shawn, asked whether to create the key: "yes create the key and build the backup".

## Context

The iPhone's online backup (phase 4h of ADR-014) is the backup of ADR-012, bought once as in ADR-013. Three things about it are Apple's own, and Android has no answer for them:

- **Sign in with Apple must be offered** wherever another company's sign-in is (App Review Guideline 4.8), and an app that lets an account be deleted must, for an account made with Sign in with Apple, **revoke the app's Apple tokens** when it is deleted (Apple, since 2022). Firebase Authentication does that revocation (`revokeToken(withAuthorizationCode:)`), but only once its Apple provider holds a **Sign in with Apple private key**, made in the Apple developer account. That key is a secret: CLAUDE.md asks for an ADR before one is added.
- **The purchase check** (ADR-013) has to ask "did Apple sell this?". Android's asks Google Play's Developer API with the functions' service account. Apple has two ways: the **App Store Server API**, which needs a second private key (an In-App Purchase key) on the server, or **StoreKit 2's signed transactions**: every purchase the phone sees arrives as a JWS signed by Apple, with Apple's certificate chain in it, which anyone can check against Apple's published root certificates. No key is needed for that.
- **Refunds:** Android looks for refunds once a day (Google Play's voided purchases). Apple's list of refunds is in the App Store Server API, behind the key; without it, Apple tells the server itself, through **App Store Server Notifications (version 2)**, signed in the same way, sent to a web address entered in App Store Connect.

## Decision

### The Sign in with Apple key

- **One key**, "Dually Sign in with Apple", made in the company's Apple developer account (team 3GA4CQ43F4) for the App ID `top.dually.triptracker`, with Sign in with Apple as its only service.
- Its file (`AuthKey_<key id>.p8`, Apple lets it be downloaded once) is kept in **`~/keys/`**, beside the signing key and the Firebase values, outside the repository and never committed.
- Its contents go into **Firebase Authentication's Apple provider** (Sign-in method, Apple, "OAuth code flow configuration": the team id, the key id and the private key). The private key is pasted there by Shawn: the assistant does not type a private key into a web page. Nothing in the app or in this repository holds it.
- **Done on 2026-11-14:** key "Dually Sign in with Apple", id `46NB688HYN`, in `~/keys/dually-AuthKey_46NB688HYN.p8`. Firebase will not keep a code-flow key without a **Services ID**, so one was registered for it: "Dually Sign in with Apple", `top.dually.triptracker.signin`. It is a name, not a secret, and nothing signs in through it (no web sign-in is configured on it); it is there for Firebase's form.
- It is used for one thing: when an account made with Sign in with Apple is deleted ("Delete my online backup and account"), the iPhone asks Apple to sign in once more, for a fresh authorization code, and hands that code to Firebase, which revokes the app's tokens with the key. Then the server's `deleteMyAccount` deletes the backup and the account, as on Android.
- If the key is lost, a new one is made and pasted in the same way; Apple allows two at a time. If it is ever exposed, it is revoked in the developer account and replaced.

### Apple's purchases, checked by their signature

- **No App Store Server API key.** The phone sends the purchase's signed transaction (`jwsRepresentation`) to the purchase check (`unlockBackup`, the same function Android calls, which tells the two apart by what it is sent).
- The check verifies the signature and the chain up to **Apple Root CA - G3**, with **Apple's own library, `@apple/app-store-server-library` (3.1.0)**, and only then reads it: the bundle `top.dually.triptracker`, the product `cloud_backup`, no revocation, and the account it was bought for.
- **The tie to the account**, as Android's "obfuscated account id": StoreKit 2 carries an `appAccountToken`, which must be a UUID. It is the first 32 hex digits of the account's tag (the SHA-256 of its Firebase id, ADR-013), written as a UUID. The app (`cloudAppleAccountToken`) and the server (`appleAccountToken`) compute it the same way, and a test on each side checks one shared value.
- **One purchase unlocks one account**, as on Android: the purchase is recorded under the SHA-256 of `apple:` and its original transaction id, which stays the same through every restore and on every iPhone of the Apple account. A purchase released by a deleted account can be restored to a new one.
- Sandbox purchases (TestFlight, Apple's test accounts) are accepted as Google Play's licence testers' are; each recorded purchase says which store and environment it came from.
- Apple's root certificate is public. It is kept in the repository (`firebase/functions/apple/`), downloaded from Apple's certificate authority page, and named with its SHA-256 in the findings log so that it can be checked again.

### Apple's refunds

- A function of its own, **`appleNotifications`**, takes Apple's notifications. It is public, like the callable functions, because Apple must reach it; it believes nothing it is sent until Apple's signature checks out, with the same library.
- A `REFUND` or `REVOKE` takes the unlock away and writes the deletion due 30 days later, exactly as Android's daily look does (`revokeRefunded`); everything else is answered and ignored.
- Its web address goes into App Store Connect (App Information, App Store Server Notifications, version 2, Production and Sandbox). That is a setting of Shawn's account, entered once, with his OK.

### Firebase's configuration on the iPhone

- `GoogleService-Info.plist` stays in `~/keys/dually-GoogleService-Info.plist` (ADR-012 treats it as a secret). **A build step copies it into the app** when it is there, and adds the Google sign-in's web address scheme (its `REVERSED_CLIENT_ID`) to the app's Info.plist. **A Release build without it stops**; a Debug build without it has no backup to offer, as on Android.
- **Firebase starts only once the backup is used** (ADR-012): at a sign-in, or at the start of an app that has an account signed in. Firestore keeps its cache in memory only.
- **Libraries**, at their current stable versions, pinned exactly in the Xcode project: Firebase's iOS SDK 13.2.0 (FirebaseAuth and FirebaseFirestore only) and Google Sign-In for iOS 10.1.0. The purchase check is called over HTTPS directly, as on Android, so FirebaseFunctions is not added.

## Consequences

- Better: one secret more, and only one. The purchase check and the refunds need no Apple key on the server, so there is nothing on the server to leak or to rotate.
- Better: the same check for both stores' purchases, the same unlock, the same "one purchase, one account".
- Worse: refunds reach the server only if the notification address stays right in App Store Connect, and Apple tries a notification for a few days only. A refund missed that way leaves an unlock that should have gone; the cost is one backup kept without pay. If that ever matters, the App Store Server API key is the fix, with an ADR of its own.
- Worse: the private key is pasted by hand into Firebase. Without it, deleting an account made with Sign in with Apple still deletes the backup and the account, but cannot revoke Apple's tokens, which App Review can refuse.
- Locked into: Apple's JWS format and its root certificate. Apple publishes when either changes; the certificate in the repository is replaced then.
