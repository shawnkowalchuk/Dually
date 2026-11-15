package com.shawnkowalchuk.milo.ios

import com.shawnkowalchuk.milo.core.odometer.formatOdometer
import com.shawnkowalchuk.milo.core.text.Locale
import com.shawnkowalchuk.milo.core.time.ZoneId
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.formatDistance
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.data.trip.TripSoFar
import com.shawnkowalchuk.milo.feature.home.homeOdometers
import com.shawnkowalchuk.milo.feature.service.englishServiceFormat
import com.shawnkowalchuk.milo.feature.service.englishServiceText
import kotlinx.coroutines.flow.first

// Home's odometer on the iPhone (2026-11-14; Shawn, of the reading the backup had brought in
// from the Android phone: "it shows up under settings but not on the home screen"), as
// Android's Home odometer tile (feature/home/HomeOdometerTile.kt): a row of wheels standing on
// the figure Settings shows, and while a trip moves it, the last wheel turning on to the next
// digit. Which odometer a trip moves and how far its wheel has turned are the shared code's
// (`homeOdometers`, `OdometerFigure.wheels`); this words them in English. The iPhone pairs no
// vehicles, so it has the one odometer, as an Android phone with none or one paired.

class IosHomeOdometer internal constructor(
    private val settings: SettingsStore,
    private val trips: TripRepository,
    private val zone: () -> ZoneId,
    private val locale: () -> Locale,
) {
    /**
     * The odometer at [nowMs] as Home's tile shows it.
     *
     * @param trip the trip being recorded, or null. It moves the odometer once the truck has
     * been seen in it, as on Android.
     */
    suspend fun read(trip: RecordingNow?, nowMs: Long): HomeOdometerText {
        val stored = settings.current()
        val finished = trips.observeFinishedTrips().first()
        val locale = locale()
        val soFar = trip?.let { TripSoFar(it.tripId, it.startedAtMs, it.metres, it.truckSeen) }
        val odometer = homeOdometers(stored, finished, nowMs, zone(), soFar).first()
        val title = englishServiceText("home_odometer_title")
        val wheels = odometer.wheels
            ?: return HomeOdometerText(
                title = title,
                hasReading = false,
                whole = 0,
                turn = 0.0,
                counting = false,
                unit = unitWord(stored.distanceUnit),
                spoken = "",
                caption = englishServiceText("home_odometer_none"),
            )
        val miles = wheels.unit == DistanceUnit.MILES
        val figure = formatOdometer(wheels.whole, locale)
        val caption = odometer.tripMetres?.let { metres ->
            val soFarWords = englishServiceFormat(
                if (miles) "distance_mi" else "distance_km",
                locale,
                formatDistance(metres, wheels.unit, locale),
            )
            englishServiceFormat("home_odometer_this_trip", locale, soFarWords)
        } ?: englishServiceText("home_odometer_from_reading")
        return HomeOdometerText(
            title = title,
            hasReading = true,
            whole = wheels.whole,
            turn = wheels.turn ?: 0.0,
            counting = wheels.turn != null,
            unit = unitWord(wheels.unit),
            spoken = englishServiceFormat(
                if (miles) "distance_mi_spoken" else "distance_km_spoken",
                locale,
                figure,
            ),
            caption = caption,
        )
    }
}

/**
 * Home's odometer tile, worded.
 *
 * @param hasReading false before the first reading: the tile then says only [caption], which
 * asks for one.
 * @param whole the whole kilometres or miles on the wheels, a digit to a wheel.
 * @param turn while [counting]: how far the last wheel has turned on from the last digit of
 * [whole] to the next, from 0 up to 1. Nought while it stands.
 * @param counting whether the trip being recorded moves this odometer: its last wheel then
 * turns, in the accent.
 * @param unit "km" or "mi", after the wheels.
 * @param spoken what VoiceOver says for the wheels: "123,461 kilometres".
 * @param caption under the wheels: where the figure comes from, or "+4.6 km this trip".
 */
data class HomeOdometerText(
    val title: String,
    val hasReading: Boolean,
    val whole: Long,
    val turn: Double,
    val counting: Boolean,
    val unit: String,
    val spoken: String,
    val caption: String,
)
