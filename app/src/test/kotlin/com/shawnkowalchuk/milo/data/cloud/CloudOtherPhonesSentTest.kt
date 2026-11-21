package com.shawnkowalchuk.milo.data.cloud

import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.SentReportKind
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.otherPhonesSent
import com.shawnkowalchuk.milo.data.transfer.EXPORT_FORMAT_VERSION
import com.shawnkowalchuk.milo.data.transfer.ExportJson
import com.shawnkowalchuk.milo.data.transfer.toExported
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sync reads the reports the other phones recorded as sent from their folders, and keeps
 * them (issue #164): the iPhone learns that October was sent from the Android phone.
 */
class CloudOtherPhonesSentTest {
    /** The account's folder, read as Firestore lists a collection: the documents right in it. */
    private class Cloud(val stored: MutableMap<String, String>) : CloudStore {
        override suspend fun apply(
            account: String,
            documents: List<CloudDocument>,
            removals: List<String>,
        ) = Unit

        override suspend fun readCollections(
            account: String,
            collections: List<String>,
        ): Map<String, String> = stored.filterKeys { it.substringBeforeLast('/') in collections }
    }

    private val october = SentReport(
        id = 4,
        kind = SentReportKind.MONTH,
        firstDay = LocalDate.of(2026, 10, 1).toEpochDay(),
        lastDay = LocalDate.of(2026, 10, 31).toEpochDay(),
        sentAtMs = 5_000,
        tripCount = 22,
        distanceMetres = 845_200.0,
        revision = 0,
    )

    private fun sentDocument(vararg reports: SentReport) = ExportJson.encodeToString(
        CloudSentReports.serializer(),
        CloudSentReports(EXPORT_FORMAT_VERSION, reports.map { it.toExported() }),
    )

    private val android = "11111111-1111-1111-1111-111111111111"
    private val iphone = "22222222-2222-2222-2222-222222222222"

    private fun account(androidsSent: String) = Cloud(
        mutableMapOf(
            "phones/$android" to cloudPhoneDocument(android, "Pixel", "android").json,
            "phones/$android/$CLOUD_SENT_REPORTS" to androidsSent,
            "phones/$iphone" to cloudPhoneDocument(iphone, "iPhone", "ios").json,
            "phones/$iphone/$CLOUD_SENT_REPORTS" to sentDocument(
                october.copy(id = 9, sentAtMs = 1),
            ),
        ),
    )

    @Test
    fun `the other phone's sent reports are read from its folder, and this phone's are not`() =
        runTest {
            val others = readOtherPhones(account(sentDocument(october)), "account-1", iphone)

            assertEquals(mapOf(android to listOf(october)), others.sentReports)
        }

    @Test
    fun `a sent reports document that cannot be read gives none, and the sync goes on`() = runTest {
        val others = readOtherPhones(account("{ not json"), "account-1", iphone)

        assertEquals(mapOf(android to emptyList<SentReport>()), others.sentReports)
    }

    @Test
    fun `the sync keeps them, says so once, and is quiet when nothing changed`() = runTest {
        val settings = SettingsStore(FakeSettingsFile())
        val lines = mutableListOf<String>()
        val others = readOtherPhones(account(sentDocument(october)), "account-1", iphone)

        assertTrue(syncOtherPhonesSent(others, settings) { lines += it })
        assertFalse(syncOtherPhonesSent(others, settings) { lines += it })

        assertEquals(listOf(october.copy(id = -1)), settings.otherPhonesSent().first())
        assertEquals(
            listOf("Cloud backup: the other phones' sent reports came in, 1 in all."),
            lines,
        )
    }
}
