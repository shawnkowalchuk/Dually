package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit

// The Report screen's options (since 2026-10-16, Shawn: "put the option to include more stuff
// for the reports or customize them", and "once they set a specific setup ... save it for future
// reports"). Both off out of the box, so a report is as it was until a switch is pressed. In the
// export file? No: like the other report details they belong to this phone's settings file,
// which Android's backup carries.

private val REPORT_LIST_PERSONAL = booleanPreferencesKey("report_list_personal")
private val REPORT_BUSINESS_SHARE = booleanPreferencesKey("report_business_share")

/**
 * What a report holds beyond the Business trips.
 *
 * @param listPersonal list the Personal trips too, marked and in grey, in no total.
 * @param businessShare say what share of the period's driving was Business.
 */
data class ReportOptions(val listPersonal: Boolean = false, val businessShare: Boolean = false)

internal fun Preferences.readReportOptions(): ReportOptions = ReportOptions(
    listPersonal = this[REPORT_LIST_PERSONAL] ?: false,
    businessShare = this[REPORT_BUSINESS_SHARE] ?: false,
)

suspend fun SettingsStore.setReportListsPersonal(on: Boolean) {
    dataStore.edit { it[REPORT_LIST_PERSONAL] = on }
}

suspend fun SettingsStore.setReportShowsBusinessShare(on: Boolean) {
    dataStore.edit { it[REPORT_BUSINESS_SHARE] = on }
}
