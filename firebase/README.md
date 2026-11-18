# firebase/: the cloud backup's server side

The security rules of Dually's optional cloud backup (ADR-012, ADR-013), for the Firebase project `dually-trip-tracker` (Firestore in Montréal). The app is the only client. The one piece of server code is the purchase check of the one-time unlock, in `functions/` (slice 1d).

Not the website's Firebase: `website/` is hosted by the project `milotriplog`, with `firebase.json` at the repository's root (ADR-003). This folder has its own `firebase.json` and `.firebaserc`, so a command run here can only reach `dually-trip-tracker`.

## What is here

- `firestore.rules`: who may read and write what. Each account reads and removes its own documents under `users/{uid}/`, writes them only while `unlocks/{uid}` exists, and nobody but the server writes an unlock. Each document is one string field, `json`.
- `test/rules.test.js`: the rules' tests, on Firebase's Firestore emulator, as project `demo-dually`, which never reaches the real project.
- `functions/`: the Cloud Functions (Node 22, Montréal). `unlockBackup`, which the app calls with a purchase's token: it asks Google Play about the purchase and, if it is paid for by this account, writes `unlocks/{uid}`. `checkRefunds`, daily: refunds take the unlock away. The logic is in `unlock.js`; `index.js` and `play.js` connect it to Firebase and Google Play.
- `test/unlock.test.js`: the purchase check on the emulator, with a stand-in for Google Play.
- `package.json` and `package-lock.json`: the test tools (firebase-tools, Firebase's JavaScript library, @firebase/rules-unit-testing), pinned exactly; Dependabot keeps them current. They never ship in the app.

## Running the tests

CI runs them on every pull request (the "Cloud backup's security rules" job). On the Mac:

```bash
cd firebase && npm ci
```

```bash
cd firebase && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" PATH="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin:$PATH" npm test
```

The emulator needs Java 21 or later; the Mac's own is 17, Android Studio's is newer. It listens on port 8386 (8080 is taken on the Mac).

## Publishing the rules

What runs on the project must be `firestore.rules`, word for word. Until the deploy is automated, it is published by hand, after its pull request is merged: Firebase console, Firestore Database, Rules, paste the file, Publish. Write the date in FINDINGS_LOG.

## Deploying the functions

Firebase's pay-as-you-go plan (Blaze) is needed for Cloud Functions, and the Firebase CLI must be signed in as an owner of the project, once, on the Mac:

```bash
cd firebase && npx firebase login
```

Then, after the pull request that changes `functions/` is merged:

```bash
cd firebase/functions && npm ci && cd .. && npx firebase deploy --only functions
```

## What Apple needs (2026-11-14, ADR-016)

- **The product:** App Store Connect, Dually Trip Tracker, In-App Purchases: `cloud_backup`, non-consumable, US$19.99. Its review screenshot is the iPhone's Online backup tile.
- **The purchase check needs no Apple key.** The iPhone sends Apple's signed transaction; `apple.js` checks it with Apple's own library (`@apple/app-store-server-library`) against `apple/AppleRootCA-G3.cer`, Apple's public root certificate (SHA-256 `63:34:3A:BF:…:3E:91:79`, from https://www.apple.com/certificateauthority/). Production transactions name the app's App Store id, 6834196352; sandbox ones (TestFlight) are accepted too.
- **Refunds:** `appleNotifications` (https://northamerica-northeast1-dually-trip-tracker.cloudfunctions.net/appleNotifications) takes App Store Server Notifications, version 2. Its address is entered in App Store Connect, the app, App Information, App Store Server Notifications, for Production and Sandbox (done 2026-11-14; first deployed the same day).
- **Sign in with Apple:** switched on in Firebase Authentication, with the key `46NB688HYN` (in `~/keys`, pasted into the provider's "OAuth code flow configuration" by Shawn) and the Services ID `top.dually.triptracker.signin` that Firebase asks for beside it. The key lets Firebase revoke an account's Apple tokens when the account is deleted.

## What Google Play needs (done once, 2026-11)

- **The product:** Play Console (account shawn.seawingman@gmail.com), Dually Trip Tracker, Monetize, One-time products: `cloud_backup`, "Cloud backup", US$19.99. Play Console only allows products once a build with the billing library has been uploaded to a track.
- **The purchase check's access:** the functions run as their own service account, `purchase-check@dually-trip-tracker.iam.gserviceaccount.com` (made 2026-11-11), whose one role in the project is Cloud Datastore User. In Play Console, Users and permissions, it is invited with "View financial data, orders and cancellation survey responses" and "Manage orders and subscriptions" for Dually only. The Google Play Android Developer API is enabled in the Google Cloud project `dually-trip-tracker`.
- **Testing without paying:** Play Console, License testing: the testers' Google accounts buy with test cards that are never charged.
