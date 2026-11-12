# firebase/: the cloud backup's server side

The security rules of Dually's optional cloud backup (ADR-012, ADR-013), for the Firebase project `dually-trip-tracker` (Firestore in Montréal). The app is the only client; there is no server code of Dually's own yet (the purchase check comes in slice 1d).

Not the website's Firebase: `website/` is hosted by the project `milotriplog`, with `firebase.json` at the repository's root (ADR-003). This folder has its own `firebase.json` and `.firebaserc`, so a command run here can only reach `dually-trip-tracker`.

## What is here

- `firestore.rules`: who may read and write what. Each account reads and removes its own documents under `users/{uid}/`, writes them only while `unlocks/{uid}` exists, and nobody but the server writes an unlock. Each document is one string field, `json`.
- `test/rules.test.js`: the rules' tests, on Firebase's Firestore emulator, as project `demo-dually`, which never reaches the real project.
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
