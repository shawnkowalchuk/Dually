package com.shawnkowalchuk.milo.feature.report

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.nextRevision
import com.shawnkowalchuk.milo.data.report.period
import com.shawnkowalchuk.milo.data.report.removalEffect
import com.shawnkowalchuk.milo.data.report.sentFor

// What Android's Report screen shows of a report sent before. In the app, not in :shared with the
// rest of ReportMaking.kt (moved 2026-11-13), because SentLine and Resend are the screen's own.

/** The question "Email the report" asks first, or null for a period never sent before. */
fun resendOf(period: ReportPeriod, sent: List<SentReport>): Resend? {
    val sentBefore = sentFor(period, sent)
    val last = sentBefore.lastOrNull() ?: return null
    return Resend(last = last.asLine(sent), revision = nextRevision(sentBefore))
}

internal fun SentReport.asLine(sent: List<SentReport>): SentLine = SentLine(
    id = id,
    period = period,
    sentAtMs = sentAtMs,
    tripCount = tripCount,
    distanceMetres = distanceMetres,
    unit = distanceUnit,
    revision = revision,
    removal = removalEffect(this, sent),
)
