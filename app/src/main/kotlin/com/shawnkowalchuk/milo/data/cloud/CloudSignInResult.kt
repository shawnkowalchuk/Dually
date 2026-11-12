package com.shawnkowalchuk.milo.data.cloud

/** How one press of "Sign in with Google" for the cloud backup ended (ADR-012). */
sealed interface CloudSignInResult {
    /** Signed in, and the account is kept (`CloudAccountFile`). */
    data class SignedIn(val account: CloudAccount) : CloudSignInResult

    /** The user closed Google's sheet. Nothing to say. */
    data object Cancelled : CloudSignInResult

    /** The phone has no Google account to sign in with. */
    data object NoGoogleAccount : CloudSignInResult

    /** Google or Firebase could not be reached. */
    data object Offline : CloudSignInResult

    /** Anything else. [cause] is for the event log: the kind of failure, never the account. */
    data class Failed(val cause: String) : CloudSignInResult
}
