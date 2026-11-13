package com.shawnkowalchuk.milo.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which phone backs up to an account (slice 1e, 2026-11-12): every case of `cloudStart`. */
class CloudStartTest {
    private val me = "install-me"
    private val other = "install-other"

    private fun start(
        owner: String?,
        backingUp: Boolean? = null,
        cloudHasTrips: Boolean = true,
        uploadedHere: Boolean = false,
    ) = cloudStart(owner, me, backingUp, cloudHasTrips, uploadedHere)

    @Test
    fun `the phone the backup names backs up, whatever else`() {
        assertEquals(CloudStart.BACK_UP, start(owner = me))
        assertEquals(CloudStart.BACK_UP, start(owner = me, backingUp = false))
    }

    @Test
    fun `a phone that was backing up stops once another is named`() {
        assertEquals(CloudStart.MOVED, start(owner = other, backingUp = true))
    }

    @Test
    fun `a phone that never backed up to it is asked, when another is named`() {
        assertEquals(CloudStart.ASK, start(owner = other, backingUp = null))
        assertEquals(CloudStart.ASK, start(owner = other, backingUp = false))
    }

    @Test
    fun `nobody named and nothing in it, the backup is this phone's`() {
        assertEquals(CloudStart.BACK_UP, start(owner = null, cloudHasTrips = false))
    }

    @Test
    fun `nobody named, and this phone filled it in build 7 or 8, it carries on`() {
        assertEquals(CloudStart.BACK_UP, start(owner = null, uploadedHere = true))
    }

    @Test
    fun `nobody named, and another phone's trips in it, it is asked`() {
        assertEquals(CloudStart.ASK, start(owner = null, uploadedHere = false))
    }

    @Test
    fun `the owner document is written and read back`() {
        assertEquals(me, cloudOwnerOf(cloudOwnerDocument(me).json))
        assertEquals(CLOUD_OWNER, cloudOwnerDocument(me).path)
        assertEquals(null, cloudOwnerOf("{broken"))
    }
}
