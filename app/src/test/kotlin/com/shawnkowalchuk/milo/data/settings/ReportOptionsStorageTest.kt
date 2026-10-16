package com.shawnkowalchuk.milo.data.settings

import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Report screen's options, as the settings file keeps them for every later report. */
class ReportOptionsStorageTest {
    private val store = SettingsStore(FakeSettingsFile())

    @Test
    fun `both are off out of the box, so a report is as it was`() = runTest {
        assertEquals(ReportOptions(), store.current().reportOptions)
    }

    @Test
    fun `each switch is kept by itself, and nothing else changes`() = runTest {
        store.setReportListsPersonal(true)
        assertEquals(ReportOptions(listPersonal = true), store.current().reportOptions)

        store.setReportShowsBusinessShare(true)
        store.setReportListsPersonal(false)

        assertEquals(
            MiloSettings(reportOptions = ReportOptions(businessShare = true)),
            store.current(),
        )
    }
}
