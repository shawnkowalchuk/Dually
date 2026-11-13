package com.shawnkowalchuk.milo.data.cloud

/** What Google Play's purchase sheet came to, before the server has looked at it. */
sealed interface PlayPurchase {
    /** Paid for: the token goes to the purchase check. */
    data class Bought(val token: String) : PlayPurchase

    /** Paid by a means that takes time (cash at a shop); Google Play says when it is paid. */
    data object Pending : PlayPurchase

    /** The user closed the sheet. */
    data object Cancelled : PlayPurchase

    /** This Google Play account bought it before: restored rather than bought again. */
    data object AlreadyOwned : PlayPurchase

    /** Google Play cannot sell it here: no Play Store, a country it is not sold in, no product. */
    data object Unavailable : PlayPurchase

    /** Anything else, by Google Play's response code, for the log. */
    data class Failed(val cause: String) : PlayPurchase
}

/** What pressing "Unlock" or "Restore purchase" came to (ADR-013). */
sealed interface CloudUnlockResult {
    /** The account holds the unlock: uploads begin. */
    data object Unlocked : CloudUnlockResult

    data object Pending : CloudUnlockResult

    data object Cancelled : CloudUnlockResult

    /** Google Play cannot sell the backup on this phone. */
    data object NotOffered : CloudUnlockResult

    /** The purchase was made while signed in to another account of Dually's backup. */
    data object OtherAccount : CloudUnlockResult

    /** The purchase was refunded, and a refunded purchase unlocks nothing again. */
    data object Refunded : CloudUnlockResult

    /** "Restore purchase" found no purchase of the backup in this phone's Google Play account. */
    data object NothingToRestore : CloudUnlockResult

    /** The purchase check could not be reached. Paid purchases are kept: try again. */
    data object Offline : CloudUnlockResult

    /**
     * The purchase check was reached, and Google Play would not answer it (since 2026-11-12):
     * the check's access to Play's orders is not in force yet, or Play is down. The purchase is
     * kept; Restore purchase later.
     */
    data object PlayNotReady : CloudUnlockResult

    /** Anything else, for the log: the kind of failure, never a token. */
    data class Failed(val cause: String) : CloudUnlockResult
}

/** The purchase check on the server (firebase/functions/unlock.js), or a stand-in in the tests. */
fun interface CloudUnlockServer {
    /** Asks the server to unlock the account signed in with the purchase [token]. */
    suspend fun unlock(token: String): CloudUnlockResult
}

/**
 * What a press of "Unlock" comes to, once Google Play's sheet has answered: a paid purchase is
 * checked on the server; one owned already is restored.
 *
 * @param owned the tokens of the backup's purchases this phone's Google Play account holds.
 */
suspend fun unlockAfter(
    purchase: PlayPurchase,
    owned: suspend () -> List<String>,
    server: CloudUnlockServer,
): CloudUnlockResult = when (purchase) {
    is PlayPurchase.Bought -> server.unlock(purchase.token)
    PlayPurchase.AlreadyOwned -> restoreFrom(owned(), server)
    PlayPurchase.Pending -> CloudUnlockResult.Pending
    PlayPurchase.Cancelled -> CloudUnlockResult.Cancelled
    PlayPurchase.Unavailable -> CloudUnlockResult.NotOffered
    is PlayPurchase.Failed -> CloudUnlockResult.Failed("Google Play: ${purchase.cause}")
}

/**
 * "Restore purchase": each purchase of the backup this phone's Google Play account holds is
 * sent to the server until one unlocks the account. Usually there is one. If none does, the
 * answer is the first one's, which says why.
 */
suspend fun restoreFrom(tokens: List<String>, server: CloudUnlockServer): CloudUnlockResult {
    if (tokens.isEmpty()) return CloudUnlockResult.NothingToRestore
    var first: CloudUnlockResult? = null
    for (token in tokens) {
        val result = server.unlock(token)
        if (result == CloudUnlockResult.Unlocked) return result
        if (first == null) first = result
    }
    return checkNotNull(first)
}
