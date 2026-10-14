# ADR-003: A website on Firebase Hosting, deployed from GitHub with a deploy key
Date: 2026-10-08
Status: Accepted

## Context

Shawn bought the domain `milotriplog.top` and created a Firebase project for MilO ("i created a web project in firebase for another project reactimate. i want to do the same for milo"). His other project, Reactimate, is a web app on Firebase Hosting at `reactimate.top`, deployed by a GitHub Actions job on every push to `main` with a Firebase service-account key kept as a GitHub secret.

MilO is not a web app. It is an Android app with no backend, no account and no internet permission, and its privacy promise rests on that (ENGINEERING_STANDARDS §12). The repository and its APK download are public since 2026-10-08.

Three constraints from the standing rules:

- No secret is ever committed, and a new secret gets an ADR first (CLAUDE.md, ENGINEERING_STANDARDS §12). Until now the only secret was the signing keystore and its password, on the Mac.
- Every version is pinned and kept current; every GitHub Action is pinned to a commit SHA (§14).
- No one-off styling: the site should look like the app (§8).

## Decision

Asked on 2026-10-08, Shawn chose:

- **Content: "Landing + privacy page".** One page that says what MilO does, with the screenshots, a "Download the latest APK" button that opens the newest GitHub Release, the install steps and a privacy summary; and a privacy policy page. A not-found page besides.
- **Deploy: "Auto on merge, like Reactimate".** `.github/workflows/website.yml` deploys `website/` to the live site whenever a change to it (or to `firebase.json`, `.firebaserc` or the workflow) reaches `main`, and on demand from the Actions tab.
- **Tech: "Plain HTML and CSS".** Hand-written pages in `website/`, no build step, no npm, no JavaScript at all.
- **The Firebase project** is `milotriplog` (`.firebaserc`): its display name is "MilOtriplog", and the console's Project settings showed the ID `milotriplog` (Shawn's screenshot, 2026-10-08). It is on the free Spark plan, which includes Hosting and a custom domain.

And so:

- **The app is not changed and gets no Firebase.** No Firebase SDK, no analytics, no internet permission. The website is a separate thing that happens to live in the same repository.
- **The site lives in `website/`,** which is Firebase Hosting's public folder (`firebase.json`). The screenshots moved there from `docs/screenshots/`, so the README and the site show one copy. The font is a copy of the app's `sora.ttf`, with its licence beside it (`website/fonts/Sora-OFL.txt`), as the SIL Open Font License asks.
- **The site loads nothing from anywhere else** and runs no script: a Content-Security-Policy that allows only the site's own images, styles and font says so to the browser. It sets no cookies and has no analytics.
- **The deploy key** is a Google Cloud service-account key with Firebase Hosting rights. It lives only in the repository's GitHub Actions secrets, as `FIREBASE_SERVICE_ACCOUNT_MILOTRIPLOG`, which is the name `firebase init hosting:github` gives it. That command, run once on any computer that has a copy of `firebase.json` and `.firebaserc` (Shawn's Windows PC, on 2026-10-08), makes a service account for GitHub with the Hosting rights it needs and no more, and stores the key in GitHub; the key is never downloaded into the repository (`.gitignore` names the usual file names as a backstop). The workflow fails with instructions if the secret is missing.
- **Pins:** the deploy action is `FirebaseExtended/action-hosting-deploy` v0.11.0, by commit SHA, which Dependabot keeps current with the other actions. The Firebase command-line tool it runs is pinned to 15.33.0 in the workflow, where Dependabot cannot see it: raised by hand (`[DEBT]`).
- **The domain** is connected in the Firebase console (Hosting, Add custom domain), with the records it gives entered at the registrar of `milotriplog.top`. Firebase provides the HTTPS certificate.

## Consequences

- Better: a public page and a privacy policy at a real address, deployed by a merge like Reactimate's, with nothing to remember on the Mac.
- Worse: a second secret, and the first one outside the Mac. Whoever holds it can replace the website (not the app, not the repository, not the releases). It is revoked by deleting the service account's key in the Google Cloud console and is replaced by running `firebase init hosting:github` again.
- Worse: a third workflow, and Google sees the site's visitors as any web host does (the privacy page says so).
- The privacy page makes promises about the app. A change to what the app keeps or sends must change `website/privacy.html` in the same pull request.
- Locked into: Firebase Hosting for the site, which is easy to leave: the pages are plain files.

## Addendum, 2026-10-13: a new name, a new look, a new address

### Context

Google Play refused the app's listing under its Impersonation policy and named the icon, the name "MilO Trip Log" and the screenshots; another company's mileage app is called "Milo Track". Shawn decided to rebrand ("we need to rebrand the app ... to comply"), gave the name ("So the first thing we will do is change the name to Dually Trip Tracker."), a design canvas for the look ("here is the theme and color and design ... we will start with the website and app do not change the maskot tell we review together"), a line for the footer ("owned and operated by 2795748 Alberta Ltd.") and a new address ("i will register a new domain name Dually.top").

Three things this record decided are no longer so: that the site looks like the app because it copies the app's design system, that its font is a copy of the app's `sora.ttf`, and that its address is `milotriplog.top`.

### Decision

- **The site is "Dually Trip Tracker", Dually for short,** on every page.
- **The look is Shawn's design, written down in `website/styles.css`:** its colours, corners and typefaces are named once at the top of that file, and the pages use the names. It is no longer a copy of the app's design system; the app is given the same look by a change of its own. The rule of §8 stands as it did: no page has a value of its own.
- **The typefaces are Archivo and IBM Plex Mono,** served from `website/fonts/` with each one's licence beside it (both the SIL Open Font License 1.1). Sora and its licence left the site. Nothing is fetched from a font service: the design's canvas loads its fonts from Google's, and the site's Content-Security-Policy and its privacy page rule that out.
- **The address is `https://dually.top`,** and the support address `support@dually.top`. Every page, the sitemap and the link previews name them. The domain was not yet registered when this was built.
- **Unchanged:** plain HTML and CSS, no build, no script; the Content-Security-Policy, word for word; a deploy on a merge to `main`, with the deploy key in GitHub's Actions secrets; and that the app gets no Firebase. **The Firebase project stays `milotriplog`,** with the secret's name that follows from it: a project's ID cannot be changed, and no visitor sees it.

### Consequences

- **The rebranded site cannot go live by a merge alone.** A merge deploys to the Hosting site whatever domain is connected to it, so the pages would be live at the old address the moment they are merged, naming a new one that does not answer yet. The domain has to be registered and connected, and `support@dually.top` has to forward, before the merge; and the old address should then send its visitors on rather than show the same pages (APP_ENCYCLOPEDIA, Website, "Before the rebrand may go live").
- Worse: a first visit fetches about 800 KB of fonts where it fetched 111 KB (FINDINGS_LOG, 2026-10-13, `[DEBT]`).
- Worse: the site's look and the app's are now two descriptions of one design, kept alike by hand, where the site used to copy the app's file and say so.
- The mascot's pictures were made for the old card's colour and are not changed by this addendum: his review is Shawn's, and until then his wave shows that colour.
