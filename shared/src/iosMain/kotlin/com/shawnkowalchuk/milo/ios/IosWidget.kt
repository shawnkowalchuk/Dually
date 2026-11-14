package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.allowance.allowanceCents
import com.shawnkowalchuk.milo.core.allowance.formatCentsPerKm
import com.shawnkowalchuk.milo.core.allowance.formatWholeDollars
import com.shawnkowalchuk.milo.core.text.Locale
import com.shawnkowalchuk.milo.core.time.ZoneId
import com.shawnkowalchuk.milo.core.time.dateOf
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.daysSpan
import com.shawnkowalchuk.milo.core.util.formatShortMonthName
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.core.util.monthOf
import com.shawnkowalchuk.milo.core.util.monthSpan
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.data.trip.categoryTotals
import kotlinx.coroutines.flow.first

/**
 * The home-screen widget's figures on the iPhone (phase 4i), as Android's widget works them out
 * (HomeWidgetContent in the app): this month's and this year's Business kilometres priced at the
 * rate in the settings file, in kilometres whatever unit is shown, since the rate is per
 * kilometre. Swift keeps them where the widget reads them.
 */
class IosWidget internal constructor(
    private val trips: TripRepository,
    private val settings: SettingsStore,
    private val zone: () -> ZoneId,
    private val locale: () -> Locale,
) {
    /** "Nov $12 · 2026 $340 at $0.70/km", Android's line, or null when the widget is off. */
    suspend fun dollars(nowMs: Long): String? {
        val now = settings.current()
        if (!now.homeWidgetEnabled) return null
        val zoneNow = zone()
        val localeNow = locale()
        val year = localDateOf(nowMs, zoneNow).year
        val yearSpan = daysSpan(dateOf(year, 1, 1), dateOf(year, 12, 31), zoneNow)
        val yearTrips = trips.observeTripsStartedBetween(yearSpan.fromMs, yearSpan.untilMs).first()
        val month = monthOf(nowMs, zoneNow)
        val monthStart = monthSpan(month, zoneNow).fromMs
        fun priced(list: List<com.shawnkowalchuk.milo.data.trip.Trip>) = formatWholeDollars(
            allowanceCents(
                categoryTotals(list, DistanceUnit.KILOMETRES).business.tenths,
                now.homeWidgetCentsPerKm,
            ),
            localeNow,
        )
        return "${formatShortMonthName(month, localeNow)} ${priced(
            yearTrips.filter {
                it.startedAtMs >= monthStart
            },
        )} · " +
            "$year ${priced(
                yearTrips,
            )} at ${formatCentsPerKm(now.homeWidgetCentsPerKm, localeNow)}/km"
    }
}
