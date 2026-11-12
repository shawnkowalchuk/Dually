package com.shawnkowalchuk.milo.data.cloud

/**
 * How many runs one upload makes at most. A run only sends what the phone had when it read it,
 * so an upload runs again until a run finds nothing left to send: a trip that ended while the
 * last batch was going up is sent by the same upload. Five is far more than a phone makes changes
 * in the time a run takes; the next change starts another upload anyway.
 */
const val CLOUD_RUNS_PER_UPLOAD = 5

/** What one upload came to, which is what the job that ran it is told to do next. */
enum class CloudUploadOutcome {
    /** The cloud holds what the phone has. */
    DONE,

    /** Stopped part way, by the network or the phone: try again later. */
    TRY_LATER,

    /** The cloud said no (no unlock, or signed out): trying again changes nothing. */
    REFUSED,
}

/**
 * One upload: runs of [backup] for [account] until one sends nothing, or [runs] of them have.
 * Each run that did something, and each that stopped, is told to [log] in a line for the event
 * log: counts and the kind of failure, never a trip's content or the account.
 */
suspend fun uploadUntilQuiet(
    backup: CloudBackup,
    account: String,
    log: suspend (String) -> Unit,
    runs: Int = CLOUD_RUNS_PER_UPLOAD,
): CloudUploadOutcome {
    repeat(runs) {
        when (val run = backup.run(account)) {
            is CloudRun.Done -> {
                if (run.uploaded == 0 && run.removed == 0) return CloudUploadOutcome.DONE
                log("Cloud backup: ${run.uploaded} sent, ${run.removed} removed.")
            }

            is CloudRun.Stopped -> {
                val refused = run.cause is CloudRefusedException
                log(
                    "Cloud backup stopped after ${run.uploaded} sent and ${run.removed} removed: " +
                        "${run.cause.javaClass.simpleName}, ${run.cause.message}." +
                        if (refused) " Not tried again." else " Tried again later.",
                )
                return if (refused) CloudUploadOutcome.REFUSED else CloudUploadOutcome.TRY_LATER
            }
        }
    }
    return CloudUploadOutcome.DONE
}
