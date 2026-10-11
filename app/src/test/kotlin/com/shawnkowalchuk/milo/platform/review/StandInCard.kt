package com.shawnkowalchuk.milo.platform.review

/**
 * Google Play's review card, replaced by a stand-in that counts what it is asked for. Out of
 * the box it gives a flow at once, as Google Play does for a copy it installed.
 */
class StandInCard : ReviewCard {
    /** What the next request is answered with. */
    var answer: suspend () -> ReviewFlow = { given() }

    /** How often Google Play was asked for a flow, and how often one was launched. */
    var requests = 0
    var launches = 0

    /** A flow that counts its launch. */
    fun given(): ReviewFlow = ReviewFlow.Given {
        launches++
        true
    }

    override suspend fun request(): ReviewFlow {
        requests++
        return answer()
    }
}
