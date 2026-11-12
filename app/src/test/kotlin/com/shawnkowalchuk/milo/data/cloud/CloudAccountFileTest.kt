package com.shawnkowalchuk.milo.data.cloud

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The file that says which account the cloud backup is signed in to, against a real folder. */
class CloudAccountFileTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val folder: File get() = File(temporaryFolder.root, "cloud")
    private val file by lazy { CloudAccountFile(folder) }
    private val account = CloudAccount(id = "uid-1", email = "driver@example.com")

    @Test
    fun `no file is no account, and what is written is read back`() {
        assertNull(file.read())

        file.write(account)

        assertEquals(account, file.read())
    }

    @Test
    fun `the unlock is kept, and a file written before it existed reads as not unlocked`() {
        file.write(account.copy(unlocked = true))
        assertEquals(true, file.read()?.unlocked)

        File(
            folder,
            "account.json",
        ).writeText("{\"id\":\"uid-1\",\"email\":\"driver@example.com\"}")
        assertEquals(account, file.read())
    }

    @Test
    fun `the account tag is the SHA-256 of the id in hex, as the server computes it`() {
        // firebase/test/unlock.test.js checks the same value for the same id.
        assertEquals(
            "27dfbde6ec9d28c36258ee6edad10e40cdaeca1adf3f7ea7cdb23b494a1765ac",
            cloudAccountTag("owner-uid"),
        )
    }

    @Test
    fun `a damaged file is no account`() {
        file.write(account)
        File(folder, "account.json").writeText("{\"id\":\"uid")

        assertNull(file.read())
    }

    @Test
    fun `a sign-out forgets the account, and the ledger beside it is left`() {
        val ledger = CloudLedger(folder)
        ledger.write(account.id, mapOf("trips/1" to "f1"))
        file.write(account)

        file.clear()
        file.clear()

        assertNull(file.read())
        assertEquals(mapOf("trips/1" to "f1"), ledger.read(account.id))
    }
}
