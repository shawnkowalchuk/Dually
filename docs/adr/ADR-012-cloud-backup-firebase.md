# ADR-012: Optional cloud backup on Firebase, with sign-in, and no GPS points

Date: 2026-11-11
Status: Accepted. **Android built** (slices 1a to 1f, merged into main on 2026-11-12, released first as build 9 on Google Play's internal testing track); iPhone not built. Replaces, for the app only, ADR-003's "the app gets no Firebase".

## Context

Shawn, 2026-11-11: "add the ability to save your files to my server. This should upload every time a user finishes a trip. it should upload any changes they make." He first wanted backup to be free and paid for by ads, then chose a one-time purchase and no ads (ADR-013).

What stands today:

- Dually has no backend, no account and no internet permission. Its privacy page says so word for word ("no account, no server, no ads and no analytics, and the app has no internet permission"), and so do the comparison page, the private-mileage-tracker page and the Play Console's data safety answers (ADR-004).
- Data leaves the phone three ways, all started by the user or by Android: the PDF and CSV handed to another app, the export file ([Backup, export and import](../APP_ENCYCLOPEDIA.md#backup-export-and-import), format 3), and Android's own backup (25 MB cap, all or nothing, points to a new phone only).
- The export format already describes everything worth keeping: every closed trip with its label, vehicle and recorded values, every sent report, and thirteen settings. `data/transfer/` writes it, checks it and imports it with a safety copy and a resume after a cut-off.
- GPS points are about 83 MB a year at Shawn's driving. Since 2026-10-14 the points of a sent, ended month can be deleted on the phone (`data/point/PointCleanup`). No screen, figure or report reads a closed trip's points.
- Repositories are the only way into the data (ARCHITECTURE §6), and both surfaces, the phone UI and Android Auto, write through them.

Asked on 2026-11-11, Shawn chose: **Firebase** (over his own server); **backup only**, the phone is the source of truth (over two-way sync); **standard protection**, encrypted in transit and at rest (over end-to-end encryption, where a lost key loses the backup); and **no GPS points in the backup** ("Don't back up points", over "current month only" and "mirror the phone").

## Decision

### What the user sees

- Backup is **off by default** and stays off until the user turns it on in Settings. With it off, Dually sends nothing anywhere, as today.
- Turning it on: sign in, unlock with the one-time purchase (ADR-013), and the first upload of every trip on the phone. Then every change uploads by itself.
- **Restore** on a new phone: sign in, "Restore my trips", and the trips, sent reports and settings come back through the import that exists.
- **Delete my account and backup** in Settings, and a page on dually.top that explains how (both stores require an in-app deletion; Google Play also asks for a web address).

### Accounts

- **Firebase Authentication.** Android: Sign in with Google (through Android's Credential Manager). iPhone: Sign in with Apple and Sign in with Google (Apple requires its own sign-in wherever another company's is offered).
- An account exists only for backup. Nothing else in the app asks for one.

### What is uploaded

- **Trips, sent reports and the settings that travel**, the same fields as the export file of format 3, **without GPS points**. One trip's data is a few hundred bytes; a year of Shawn's trips is well under 1 MB.
- **Every closed trip and every change to one:** finished, edited, relabelled, made Business or Personal, discarded, deleted, recovered. A deleted trip is uploaded as deleted (the row is never removed on the phone, so it is not removed in the cloud either). The trip being recorded (`OPEN`) is not uploaded until it closes.
- **A sent report** when it is recorded, and **the travelling settings** when one changes.
- Never: GPS points, the event log, crash files, chosen sounds, safety copies.

### How it gets there

- **A comparison, not an outbox** (changed in slice 1a, 2026-11-11; the first version of this record said an outbox table written in the same transaction as each change). Each run reads every trip and the sent reports in one transaction, and the travelling settings, turns them into the documents the cloud is to hold, and compares the fingerprint of each with the one it had when it was last uploaded. It sends what differs and removes what the phone no longer has. A trip is changed in about ten places (its end, its address, an edit, a label, Business or Personal, a delete, an import), and an outbox would have to be fed by every one of them, and by every one added later; database triggers would do that, but Room's schema files do not hold them and the tests cannot run SQLite. The comparison cannot miss a change, needs no change to the database, and handles an import and a restore by itself. A few thousand trips are a few hundred kilobytes to read. What was uploaded is kept in a ledger file under `no_backup/cloud/`, with the account it belongs to (`data/cloud/CloudLedger`).
- **WorkManager** runs the comparison: one job, needing a network, retried with back-off. **Built in slice 1c:** it is asked for 30 seconds after the trips, the sent reports or the travelling settings last changed (Room's change signal for the two tables, so every writer is covered, the car's screen and an import included), at each start of the app, and daily. A request while one waits or runs is dropped, and the job runs the comparison again until it finds nothing left, at most five times. A trip that ends in a dead zone uploads when there is signal. WorkManager is already in the planned stack (ARCHITECTURE §2) and enters the build with this. As today, nothing that can wait is used to *start* a trip: the upload is after the fact and never touches trip detection.
- **Trip ids are the keys** (changed in slice 1a, 2026-11-11; the first version of this record said a new UUID column). A trip's document is `trips/{id}`. The table numbers upwards from the highest id it ever held (`AUTOINCREMENT`), so no id is used twice on a phone, and an import and a restore keep the ids. The cloud is a copy of one phone's table, never a merge of two, so a new phone's own trip 1 cannot meet a backed-up trip 1: it either restores (and its trips are replaced) or starts backing up, and then the account's documents are removed first and its own table uploaded. The ledger cannot do that removal by itself: a phone's ledger names only what that phone sent. So taking over the backup, and getting the unlock back after the cloud copy was deleted, both clear the cloud folder or the ledger before the first run (slice 1e).
- **One phone backs up per account.** The account remembers which install is backing up. Signing in on another phone offers "Restore here and back up from this phone"; the old phone is then told, at its next upload, that backup moved, and stops. There is no merging of two phones' trips (that would be sync, which Shawn did not choose).

### Where it lives

- **Cloud Firestore**, in the **Montréal region (`northamerica-northeast1`)**, so the data of a Canadian company's users stays in Canada. The region of a Firestore database cannot be changed after it is created.
- One document per trip under `users/{uid}/trips/{id}`, and `users/{uid}/backup/sentReports` and `users/{uid}/backup/settings` beside them. **Built in slice 1c, 2026-11-11:** each document is one string field, `json`, holding exactly the JSON the app fingerprinted, the export format's own. A restore reads it with the export's reader, and the rules can check its shape without knowing the format. **Security rules** (`firebase/firestore.rules`, tested on the emulator in CI) let a signed-in user read and remove only their own documents, and write them only while `unlocks/{uid}` exists. That document is the unlock (ADR-013): the account can read its own, and no app can write one; the purchase check on the server (slice 1d) and the console can. Firestore's cache on the phone is in memory only. **App Check** (Play Integrity on Android, App Attest on iPhone) keeps other programs out.
- **A new Firebase project for the app, "Dually" (`dually-trip-tracker`, created 2026-11-11)**, not the website's `milotriplog` project. The website's deploy key then has no rights over anyone's trips, and the app's project can be on a paid plan without touching the site. The purchase check (ADR-013) runs as Cloud Functions, which need Firebase's pay-as-you-go plan (Blaze); a **budget alert** is set at creation.

### Firebase stays silent until backup is on

- Firebase's automatic start (its `FirebaseInitProvider`) is removed from the merged manifest; Dually starts Firebase itself, and only once backup is turned on. No Analytics, Crashlytics, Performance, Messaging or Remote Config is added.
- This is checked on the phone before release: with backup off, the phone's network log shows Dually making no connection at all.

### The app's configuration file

- Firebase's `google-services.json` (and `GoogleService-Info.plist` on iPhone) holds the project's identifiers and a Firebase API key. Google says these are not secrets, since the security rules protect the data. **They are treated as secrets anyway**, as CLAUDE.md asks of any API key.
- **Changed in slice 1b, 2026-11-11:** Android uses no `google-services.json` and no Google Services Gradle plugin. Dually starts Firebase by hand anyway, so it needs only the four values (project id, app id, API key, web client id). They are in `~/keys/dually-firebase.properties`, beside the signing key, outside the repository; the build puts them in `BuildConfig`. **CI is given none of them:** it builds debug only, and a debug build without the file has no backup to offer ("Not in this build"), which also keeps Dependabot's pull requests (which get no secrets) green. **A release build without the file stops** and says where it goes. One plugin fewer to keep current, and no secret in GitHub.

### What changes with it

- The **internet permission** (and network state, for WorkManager) is added. On Android it applies to the whole app, whether backup is on or off. The sign-in's libraries also declare fingerprint, biometric (Credential Manager, for passkeys) and READ_GSERVICES (Firebase's reCAPTCHA, for sign-in by email or phone); Dually uses none of them, and the manifest removes all three (slice 1b).
- The **privacy page, the comparison page and the private-mileage-tracker page** change in the same pull request as the code (ADR-003's rule). New wording: private by default; an optional paid backup sends trips, without GPS points, to Dually's server in Canada.
- The **Play Console's data safety form** gains: precise location (start and end positions), email address (sign-in), and purchase history, all optional, for app functionality, encrypted in transit, deletable. Apple's privacy label says the same.
- **CLAUDE.md and ENGINEERING_STANDARDS §12** ("Dually has no backend, no API keys and no sign-in") are updated in the pull request that builds it, since they describe the app as it is.

### Where the code goes

- `data/cloud/`: the documents (built on `data/transfer/ExportMapping`), the comparison, the ledger and the run (slice 1a), and the restore, which builds an export document of format 3 without points and hands it to `DataImport`.
- `platform/cloud/`: Firebase sign-in, the Firestore client, the WorkManager job, and the start of Firebase.
- `feature/settings/`: a Cloud backup tile next to the one for backup, export and import.
- All rules that are not about Firebase itself (what is queued, when, how a trip maps) sit in `data/` or `core/` and are written to move into the shared module of ADR-014.

### Built in slice 1e, 2026-11-12
- **Ownership:** `users/{uid}/backup/owner` names the install backing up; each upload reads the backup first (`cloudStart`). Another phone named: the phone stops (if it was backing up) or asks Restore here / Back up this phone.
- **Restore:** through the import, as planned: the backup becomes an export file of format 3 without points.
- **Deletion:** the callable function `deleteMyAccount` deletes the backup, the unlock, any waiting deletion and the sign-in account, and releases the purchase so that it can be restored to a new account. Refunds' backups are deleted by `runDeletions` in the daily job, 30 days after the refund.
- **Rules:** `backup/owner` added to the known paths; an account may list its own `trips` and `backup` folders.

## Consequences

- Better: a lost or replaced phone keeps every trip, for users who want that, without a file to remember to export.
- Better: small and bounded. Without GPS points a user's backup grows by well under 1 MB a year, which is what makes a one-time price workable (ADR-013), and no user's route history is ever on the server.
- Worse: Dually is no longer an app that *cannot* send anything. It is an app that sends nothing unless the user turns backup on. The privacy pitch gets one sentence longer, and the old claim must be removed everywhere it appears.
- Worse: an account system, a server, rules and functions to keep secure, and Google's and Apple's account-deletion and data-safety duties. Firebase carries most of the operating weight; the rules and the functions are Dually's own and get tests (the Firebase emulator in CI).
- Worse: a restore has no drawn routes; trips cannot be measured again from their points after a move to a new phone.
- Worse: the Firebase project has a monthly bill once on Blaze, small at first, watched by the budget alert.
- Locked into: Firestore's data model and region. Leaving later means an export from Firestore and a new server; the documents are the export format, so the shape is portable.
- Unknown until tried: how the Play review reads the new data safety answers; App Check on HyperOS; WorkManager on HyperOS's battery rules (the trip service has needed special treatment there, ARCHITECTURE §10).
