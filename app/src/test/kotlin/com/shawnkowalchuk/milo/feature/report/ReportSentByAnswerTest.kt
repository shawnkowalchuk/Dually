package com.shawnkowalchuk.milo.feature.report

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.report.FakeSentReportDao
import com.shawnkowalchuk.milo.data.report.SentReportRepository
import com.shawnkowalchuk.milo.data.settings.ReportHandOver
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import com.shawnkowalchuk.milo.platform.trip.UnreadableSettingsFile
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the list of sent reports tells the app that "I sent it" has recorded a report: the
 * moment Google Play is asked for its review card (ADR-009). Only that answer does. "Mark as
 * sent", "Not sent", a removal, and an answer that could not be stored do not; and a records
 * object that is handed nobody to tell records exactly as it always did.
 */
class ReportSentByAnswerTest {
    private val dao = FakeSentReportDao()
    private val log = FakeEventLogDao()
    private val nowMs = 1_791_300_000_000L
    private val settings = SettingsStore(FakeSettingsFile())
    private var told = 0
    private val records =
        ReportRecords(
            sent = SentReportRepository(dao),
            settings = settings,
            eventLog = EventLogRepository(log),
            onSentByAnswer = { told++ },
            clock = { nowMs },
        )
    private val month = ReportPeriod.Month(YearMonth.of(2026, 9))
    private val handedOver =
        ReportHandOver(month, 21, 2314, nowMs - 60_000, DistanceUnit.KILOMETRES)

    @Test
    fun `I sent it tells once, after the report is in the list`() = runTest {
        settings.setReportHandOver(handedOver)

        assertTrue(records.answered(handedOver, sent = true))

        assertEquals(1, told)
        assertEquals(1, dao.rows.size)
        assertNull(settings.current().reportHandOver)
    }

    @Test
    fun `a revision sent later tells again - asking once is not this class's business`() = runTest {
        records.answered(handedOver, sent = true)

        records.answered(handedOver.copy(atMs = nowMs), sent = true)

        assertEquals(2, told)
        assertEquals(listOf(0, 1), dao.rows.map { it.revision })
    }

    @Test
    fun `Not sent tells nothing`() = runTest {
        settings.setReportHandOver(handedOver)

        assertTrue(records.answered(handedOver, sent = false))

        assertEquals(0, told)
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `Mark as sent tells nothing, though it writes the same row`() = runTest {
        val stored = records.markedSent(month, 21, 2314, DistanceUnit.KILOMETRES)

        assertNotNull(stored)
        assertEquals(1, dao.rows.size)
        assertEquals(0, told)
    }

    @Test
    fun `removing a sent report tells nothing`() = runTest {
        val stored = records.markedSent(month, 21, 2314, DistanceUnit.KILOMETRES)

        assertTrue(records.removed(stored!!.id))

        assertEquals(0, told)
    }

    @Test
    fun `an answer that could not be recorded tells nothing`() = runTest {
        // The list of sent reports refuses the row.
        dao.failNextInsert = IllegalStateException("the disk is full")
        assertFalse(records.answered(handedOver, sent = true))
        assertEquals(0, told)

        // The settings file cannot forget the waiting report: nothing is recorded at all.
        val stuck =
            ReportRecords(
                sent = SentReportRepository(dao),
                settings = SettingsStore(UnreadableSettingsFile),
                eventLog = EventLogRepository(log),
                onSentByAnswer = { told++ },
                clock = { nowMs },
            )
        assertFalse(stuck.answered(handedOver, sent = true))
        assertEquals(0, told)
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `with nobody to tell, an answer is recorded exactly as with somebody`() = runTest {
        val quietDao = FakeSentReportDao()
        val quietLog = FakeEventLogDao()
        val quiet =
            ReportRecords(
                SentReportRepository(quietDao),
                SettingsStore(FakeSettingsFile()),
                EventLogRepository(quietLog),
            ) { nowMs }

        assertTrue(quiet.answered(handedOver, sent = true))
        assertTrue(records.answered(handedOver, sent = true))

        assertEquals(quietDao.rows, dao.rows)
        assertEquals(quietLog.entries.map { it.message }, log.entries.map { it.message })
    }
}
