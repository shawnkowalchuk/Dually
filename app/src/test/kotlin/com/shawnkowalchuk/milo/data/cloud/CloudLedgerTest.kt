package com.shawnkowalchuk.milo.data.cloud

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The ledger of what was uploaded, against a real folder. */
class CloudLedgerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val folder: File get() = File(temporaryFolder.root, "cloud")
    private val ledger by lazy { CloudLedger(folder) }

    @Test
    fun `what is written is read back for the same account`() {
        ledger.write("a", mapOf("trips/1" to "f1", "backup/settings" to "f2"))

        assertEquals(mapOf("trips/1" to "f1", "backup/settings" to "f2"), ledger.read("a"))
    }

    @Test
    fun `no file, another account and a damaged file all read as nothing uploaded`() {
        assertTrue(ledger.read("a").isEmpty())

        ledger.write("a", mapOf("trips/1" to "f1"))
        assertTrue(ledger.read("b").isEmpty())

        File(folder, "uploaded.json").writeText("{\"layout\":1,\"acc")
        assertTrue(ledger.read("a").isEmpty())

        File(folder, "uploaded.json").writeText("{\"layout\":2,\"account\":\"a\",\"documents\":{}}")
        assertTrue(ledger.read("a").isEmpty())
    }

    @Test
    fun `a write leaves no half-written file behind`() {
        ledger.write("a", mapOf("trips/1" to "f1"))
        ledger.write("a", mapOf("trips/2" to "f2"))

        assertEquals(listOf("uploaded.json"), folder.list()?.toList())
        assertEquals(mapOf("trips/2" to "f2"), ledger.read("a"))
    }

    @Test
    fun `clear forgets everything`() {
        ledger.write("a", mapOf("trips/1" to "f1"))

        ledger.clear()
        ledger.clear()

        assertTrue(ledger.read("a").isEmpty())
        assertFalse(File(folder, "uploaded.json").exists())
    }
}
