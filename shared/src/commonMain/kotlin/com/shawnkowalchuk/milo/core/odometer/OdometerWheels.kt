package com.shawnkowalchuk.milo.core.odometer

import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.tenthsOf
import com.shawnkowalchuk.milo.core.util.tenthsOfWhole
import kotlin.math.floor

// The odometer as Home's row of wheels shows it (Shawn's choice of 2026-10-10: "for the live
// odometer please use build 3", the drawing called "Dashboard wheels"): every digit of the
// whole kilometres on a wheel of its own, and the last of them turning as the truck is driven.
//
// **The wheels show whole kilometres only** (Shawn, the same evening, of the wheel for the
// tenth they had at first: "the 1/10 at the end looks like it is another zero on the odometer.
// can we have the last number turn slowly to the next number"; of three moving drawings he
// chose "Always creeping"). There is no wheel for the tenth. While a trip is counted, the last
// wheel of the kilometres stands between its digit and the next, as far on as the kilometre
// has been driven, so it turns all the time and takes a kilometre for a digit.
//
// **Standing, the wheels show the figure Settings shows.** Settings' odometer tile and the
// report for the accountant print [OdometerFigure.value]: the reading and the trips since,
// rounded to the nearest whole kilometre or mile. An odometer that counts no trip shows that
// number, on wheels that stand on their digits. **Counting,** the wheels show the same reading
// and trips before that rounding ([inTenths], and what the trip has driven past its tenth):
// 123,460.6 km is 123460 with the last wheel six tenths of the way from 0 to 1, where Settings
// says 123,461. When the trip ends the wheels stand on Settings' number again.
// Nothing about [odometerAt] or what the report prints is changed by this file.
//
// **The kilometre counts from the reading.** Shawn types the whole kilometres the dashboard
// shows, so MilO does not know where in that kilometre the truck stood. The last wheel stands
// on its digit at each reading and turns on from there; the truck's own odometer can change
// its last digit at another moment.

private const val TENTHS_PER_WHOLE = 10L
private const val HALF_IN_TENTHS = 5L

/** A trip's exact distance is never said to be further than this from the tenth it is printed as. */
private const val HALF_A_TENTH = 0.5

/**
 * What the row of wheels shows of one odometer.
 *
 * @param whole the whole kilometres or miles on the wheels: a digit to a wheel.
 * @param turn while this odometer counts the trip being recorded: how far the last wheel has
 * turned on from the last digit of [whole] to the next, from 0 up to 1. Null while it counts
 * no trip: the wheels then stand on their digits, and [whole] is [OdometerFigure.value].
 */
data class OdometerWheels(val whole: Long, val unit: DistanceUnit, val turn: Double?)

/**
 * This figure as the wheels show it.
 *
 * @param tripMetres the distance so far of the trip being recorded, if this odometer is
 * counting it (the figure then already holds it, rounded to a tenth), or null.
 */
fun OdometerFigure.wheels(tripMetres: Double? = null): OdometerWheels {
    if (tripMetres == null) return OdometerWheels(value, unit, turn = null)
    // The figure to the tenth, and what the trip has driven past the tenth it is printed as:
    // together the odometer to the metre, in whole units.
    val exact = (
        (
            inTenths() + pastItsTenth(
                tripMetres,
                unit,
            )
            ) / TENTHS_PER_WHOLE
        ).coerceAtLeast(0.0)
    val whole = floor(exact)
    return OdometerWheels(whole.toLong(), unit, turn = exact - whole)
}

/**
 * The odometer in tenths of its unit: the reading, as whole tenths, and the trips between the
 * reading and the moment, as [OdometerFigure.drivenTenths] holds them. They are added for a
 * moment after the reading, which "now" always is, and taken away for one before it.
 *
 * **Rounded to the nearest whole unit it is [OdometerFigure.value], always.** That is checked
 * here and not taken on trust: a figure this function cannot account for is taken as its whole
 * number with a tenth of nought, so the wheels can be less exact than Settings' figure but can
 * never say something else.
 */
fun OdometerFigure.inTenths(): Long {
    val readingTenths = tenthsOfWhole(reading.value, from = reading.unit, to = unit)
    val after = readingTenths + drivenTenths
    val before = readingTenths - drivenTenths
    return when {
        wholeOf(after) == value -> after
        before >= 0 && wholeOf(before) == value -> before
        else -> Math.multiplyExact(value, TENTHS_PER_WHOLE)
    }
}

/** Tenths as [odometerAt] rounds them to the whole number it shows: half a unit goes upwards. */
private fun wholeOf(tenths: Long): Long =
    Math.floorDiv(tenths + HALF_IN_TENTHS, TENTHS_PER_WHOLE).coerceAtLeast(0)

/**
 * How far a trip of [tripMetres] so far is past the tenth it is printed as, in tenths of
 * [unit]: the trip's exact distance, less the whole tenths of `tenthsOf`, the one rounding of
 * a trip. From -0.5 (not yet at its tenth) to 0.5.
 *
 * A trip is printed to the nearest tenth, so this is nought when the trip is at exactly that
 * tenth (4,600 m: "4.6"), a quarter 25 m after, and minus a half at 4,650 m, where the printed
 * figure changes to "4.7". Added to the figure in tenths it gives the odometer to the metre,
 * which grows evenly with the distance.
 */
fun pastItsTenth(tripMetres: Double, unit: DistanceUnit): Double {
    val exact = tripMetres / unit.metresPerTenth.toDouble()
    return (exact - tenthsOf(tripMetres, unit)).coerceIn(-HALF_A_TENTH, HALF_A_TENTH)
}
