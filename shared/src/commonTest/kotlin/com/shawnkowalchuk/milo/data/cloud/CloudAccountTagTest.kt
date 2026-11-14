package com.shawnkowalchuk.milo.data.cloud

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What ties a purchase to its account (ADR-013, ADR-016), on both phones: each value is checked
 * for the same id in firebase/test/unlock.test.js, so the server unlocks the account the phone
 * bought for.
 */
class CloudAccountTagTest {
    @Test
    fun theAccountTagIsTheSha256OfTheIdInHex() {
        assertEquals(
            "27dfbde6ec9d28c36258ee6edad10e40cdaeca1adf3f7ea7cdb23b494a1765ac",
            cloudAccountTag("owner-uid"),
        )
    }

    @Test
    fun theAppleAccountTokenIsTheTagWrittenAsAUuid() {
        assertEquals("27dfbde6-ec9d-28c3-6258-ee6edad10e40", cloudAppleAccountToken("owner-uid"))
    }

    @Test
    fun theAppleCallCarriesTheSignedTransaction() {
        assertEquals(
            """{"data":{"signedTransaction":"eyJ.abc.def"}}""",
            appleUnlockRequestBody("eyJ.abc.def"),
        )
    }
}
