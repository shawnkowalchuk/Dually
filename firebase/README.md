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

## What Google Play needs (done once, 2026-11)

- **The product:** Play Console (account shawn.seawingman@gmail.com), Dually Trip Tracker, Monetize, One-time products: `cloud_backup`, "Cloud backup", US$19.99. Play Console only allows products once a build with the billing library has been uploaded to a track.
- **The purchase check's access:** the functions run as the project's default compute service account (`61962047633-compute@developer.gserviceaccount.com`). In Play Console, Users and permissions, it is invited with "View financial data, orders and cancellation survey responses" and "Manage orders and subscriptions" for Dually only. The Google Play Android Developer API is enabled in the Google Cloud project `dually-trip-tracker`.
- **Testing without paying:** Play Console, License testing: the testers' Google accounts buy with test cards that are never charged.
