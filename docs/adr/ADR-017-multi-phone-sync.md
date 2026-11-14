# ADR-017: Every phone on a backup account holds the whole logbook (full sync)

Date: 2026-11-14
Status: Accepted. Steps 1 and 2 built on 2026-11-14 on the branch `app/multi-phone` (every phone backs up into its own folder; every phone holds the others' finished trips, read-only; one merge question), for TestFlight and Google Play's internal testing; steps 3 and 4 not built. Replaces ADR-012's "one phone backs up per account" and "backup only, the phone is the source of truth". The proposal Shawn decided from is his doc "Multi-phone online backup proposal".

## Context

Since ADR-012 one phone backs up to an account. A second phone that signs in is asked to "Restore my trips here" (its trips replaced by the backup) or "Back up this phone instead" (the backup replaced by its trips), and the phone that loses stops backing up. On 2026-11-14 Shawn signed in on the iPhone with the Google account his Android phone backs up to, found the two buttons, and said: "it should automatically restore the phone ... if there are trips already on the existing phone it should then ask if you want to merge them. i dont like the idea of hitting extra buttons."

Offered three ways (A: each phone backs up apart; B: every phone sees every trip, edited only where recorded; C: full sync, any phone edits any trip), he chose C ("i think im leaning toward c", then "yes rewrite it around c"), and answered its four questions:

- Two phones change the same trip before either has seen the other's change: **the later change wins**; the earlier one is kept in the trip's history and the phone whose change was replaced shows a notice.
- Shared settings: **the report details, the odometer and the service book**; work hours and the trip-detection rules stay per phone.
- Trips in a month already sent to the accountant: **locked once sent**, on every phone. (Open: whether a deliberate "Unlock this month", asked first, is allowed for a correction. Asked in the doc; needed by step 4 only.)
- A business with several drivers on one account: **any phone may change any trip, for now**; an owner-only setting can come later.
- Asked what happens when both of his phones record the same drive (Dually on the Android phone and the iPhone, both in the truck): **count it once** (2026-11-14). He also said the build should be fast: "i think we bang it out in a couple hours".

## Decision

### What the user sees

- **Every phone signed in to an account backs up at once.** No phone stops because another signed in.
- **Signing in on a phone brings every trip down by itself,** with no button. If the phone already has closed trips of its own, it asks one question: "Merge this phone's N trips with the M in your logbook?" **Merge** (the default) keeps both; **Use only the logbook's trips** puts this phone's trips in a safety copy first and removes them.
- **One logbook on every phone:** Trips, Home, the report, the widget, CarPlay and Android Auto count every phone's trips. A trip shows which phone recorded it when it was not this one ("from Shawn's iPhone").
- **Any phone may change any closed trip** (step 4): times, distance, addresses, label, Business or Personal, delete, recover. Trips of a month whose report was sent are locked on every phone.
- **"Stop backing up this phone"** retires a phone: its trips stay in the logbook. Deleting the account deletes every phone's folder.
- **Up to 5 phones per account** on one purchase, said on the store pages.

### Where it lives

- **A folder per phone:** `users/{uid}/phones/{install}/` holds the trips that phone recorded (`trips/{id}`, the phone's own trip id, as today), its sent reports and its per-phone settings (`backup/…`, as today), and a record of the phone (`phone`: its name, Android or iPhone, its last upload). A trip's **home** is the folder of the phone that recorded it, for life; edits from other phones are written there.
- **Shared settings** (step 3): `users/{uid}/shared/…`: the report details, the odometer readings and the service book, the later change winning.
- **The documents stay the export format's JSON in one `json` field** (ADR-012), so the rules and the restore keep working the same way. A trip document gains its change number, the phone and time of its last change, and its recent history (step 4).
- **Today's backups move into their phone's folder by themselves:** a phone of the new version that finds the old top-level folder naming it as owner uploads into its new folder and then removes the old documents. Older app versions still write the old folder; the new versions read it as one more phone ("your other phone, on an older version of Dually") until it is updated. The security rules allow both.

### On the phone

- **The trips table gains the trip's home** (`homePhone`, null for this phone's own trips, and `homeTripId`, its id on that phone), a database change on both apps (version 8). Other phones' trips are rows like any other, so every total, list and report already counts them; trip detection only ever makes this phone's own.
- **The same drive recorded by two phones is counted once:** another phone's trip that overlaps one of this phone's own trips by more than half of the shorter of the two is not added to this phone's logbook (each phone keeps its own recording of that drive); of two other phones' trips that overlap so, the one that started first is kept.
- **The upload is the comparison of today** (ADR-012), for this phone's own trips, into its own folder. **The download** reads what changed in the other phones' folders since it last looked (Firestore listeners while the app is open; at the start, at the front and after each upload otherwise) and writes it into the table by its home and id. A document removed from a folder removes its row.
- **The export file carries every trip with its home** (format 4); an import of format 3 takes every trip as this phone's own, as before.

### Built in four steps, on a branch

1. A folder per phone, phone records, the move of today's backups, the rules (about 3 days).
2. One logbook on every phone: the database change, the download, the sign-in with its one merge question, the marks on other phones' trips (about 7 days).
3. Shared settings, and a new phone that gets everything (about 3 days).
4. Editing from any phone: change numbers, the later change wins, history and the notice, the lock on sent months (about 8 days).

The steps gather on the branch `app/multi-phone`, each as a pull request into it, as the backup's slices did on `app/cloud-backup`, and go to main together once steps 1, 2 and 4 work on Shawn's two phones, so that no release has a logbook that cannot be edited where it is read.

## Consequences

- Better: every phone is protected, and a new or second phone has the whole logbook without a button.
- Better: one report for all of a person's driving, from any phone.
- Worse: Dually is no longer "the phone is the source of truth". A wrong change made on one phone reaches every phone; the history and the lock on sent months limit the harm.
- Worse: two phones' changes to one trip can meet; the later wins, and the user is told.
- Worse: more reads and writes per user (each phone reads the others' changes); still pennies a year without GPS points. Watched by the budget alert (ADR-012).
- Worse: a database change on both apps, and an export format 4.
- The privacy page gains one sentence: trips are copied "from each phone you sign in on" and to it. The data uploaded does not change.
- Locked into: a trip's home folder for life, and the export format as the document format.
