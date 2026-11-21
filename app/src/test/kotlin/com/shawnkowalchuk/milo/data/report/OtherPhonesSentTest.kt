package com.shawnkowalchuk.milo.data.report

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.keepOtherPhonesSent
import com.shawnkowalchuk.milo.data.settings.otherPhonesSent
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A report sent on another phone of the online backup account counts here too (issue #164):
 * the month is submitted, a new report of it is a revision, and only the phone that sent it can
 * take it off the list.
 */
class OtherPhonesSentTest {
    private val october = YearMonth.of(2026, 10)
    private val store = SettingsStore(FakeSettingsFile())
    private val dao = FakeSentReportDao()
    private val sent = SentReportRepository(dao, store.otherPhonesSent())

    /** October as the Android phone sent it, with that phone's own id. */
    private val androidsOctober = SentReport(
        id = 4,
        kind = SentReportKind.MONTH,
        firstDay = LocalDate.of(2026, 10, 1).toEpochDay(),
        lastDay = LocalDate.of(2026, 10, 31).toEpochDay(),
        sentAtMs = 5_000,
        tripCount = 22,
        distanceMetres = 845_200.0,
        revision = 0,
    )

    @Test
    fun `another phone's reports get ids of their own, and a second sync changes nothing`() =
        runTest {
            assertTrue(store.keepOtherPhonesSent(listOf(androidsOctober)))

            val kept = store.otherPhonesSent().first().single()
            assertEquals(androidsOctober.copy(id = -1), kept)
            assertTrue(kept.fromOtherPhone)
            assertFalse(store.keepOtherPhonesSent(listOf(androidsOctober)))
        }

    @Test
    fun `a report removed on the other phone goes from here at the next sync`() = runTest {
        store.keepOtherPhonesSent(listOf(androidsOctober))

        assertTrue(store.keepOtherPhonesSent(emptyList()))

        assertEquals(emptyList<SentReport>(), sent.currentSent())
    }

    @Test
    fun `a month the other phone sent is submitted here, and no row is written here`() = runTest {
        store.keepOtherPhonesSent(listOf(androidsOctober))

        val list = sent.currentSent()

        assertEquals(listOf(androidsOctober.copy(id = -1)), list)
        assertNotNull(monthSubmission(october, list))
        assertEquals(emptyList<SentReport>(), dao.rows)
    }

    @Test
    fun `this phone's and the other phone's are listed together, newest first`() = runTest {
        store.keepOtherPhonesSent(listOf(androidsOctober))
        val mine =
            sent.recordSent(
                ReportPeriod.Month(YearMonth.of(2026, 9)),
                sentAtMs = 9_000,
                tripCount = 3,
                distanceMetres = 1_000.0,
                unit = DistanceUnit.KILOMETRES,
            )

        assertEquals(listOf(mine.id, -1L), sent.currentSent().map { it.id })
    }

    @Test
    fun `the other phone's report is not removed here`() = runTest {
        store.keepOtherPhonesSent(listOf(androidsOctober))

        assertNull(sent.remove(-1))

        assertEquals(1, sent.currentSent().size)
    }

    @Test
    fun `a report of a month the other phone sent is a revision of it`() = runTest {
        store.keepOtherPhonesSent(listOf(androidsOctober))

        val mine =
            sent.recordSent(
                ReportPeriod.Month(october),
                sentAtMs = 9_000,
                tripCount = 22,
                distanceMetres = 845_200.0,
                unit = DistanceUnit.KILOMETRES,
            )

        assertEquals(1, mine.revision)
        val submission = monthSubmission(october, sent.currentSent())
        assertEquals(-1L, submission?.first?.id)
        assertEquals(mine.id, submission?.latest?.id)
    }

    @Test
    fun `removing this phone's report leaves the month submitted by the other phone's`() = runTest {
        store.keepOtherPhonesSent(listOf(androidsOctober))
        val mine =
            sent.recordSent(
                ReportPeriod.Month(october),
                sentAtMs = 9_000,
                tripCount = 22,
                distanceMetres = 845_200.0,
                unit = DistanceUnit.KILOMETRES,
            )

        val removed = sent.remove(mine.id)

        assertEquals(listOf(-1L), removed?.leftForPeriod?.map { it.id })
        assertNotNull(monthSubmission(october, sent.currentSent()))
    }

    @Test
    fun `of two phones' originals of one month the one sent first is the month's first`() {
        val iphones = androidsOctober.copy(id = 1, sentAtMs = 3_000)
        val androids = androidsOctober.copy(id = -1)

        val submission = monthSubmission(october, listOf(androids, iphones))

        assertEquals(1L, submission?.first?.id)
        assertEquals(-1L, submission?.latest?.id)
    }
}
