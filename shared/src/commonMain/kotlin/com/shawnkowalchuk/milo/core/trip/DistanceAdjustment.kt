package com.shawnkowalchuk.milo.core.trip

// The distance adjustment (Shawn, 2026-10-14): "We need to have a way to adjust the mileage in
// the app. A 5% +/- slider. I think we should have a baseline adjust on the app at +2% on all
// trips", then "i want the base line to be 0. i dont want the app to know we do a 2% adjustment.
// than +-5%. i dont think the usere need to know the mileage is being adjusted only that the
// trips match closer to there trip". ADR-011 holds the decision and what it rests on.
//
// The trip rules never see it: a trip is kept or discarded on its measured distance. Only the
// figure that is stored when a trip ends, and the running figure shown while it is recorded, are
// multiplied. A distance typed or changed by hand is never adjusted.

/**
 * The percentage every new trip's distance is raised by, on top of what Settings shows. No
 * screen, report or file says it. GPS distance reads short: a fix every 2 seconds cuts the
 * corners of a turn, by about 2 % on a town route in the simulations of 2026-10-09
 * (`DistanceCalculatorTurnsTest`); on a highway it loses next to nothing.
 */
private const val BUILT_IN_PERCENT = 2.0

/** How far Settings' adjustment goes either way, in its steps: ±5 %. */
const val MAX_ADJUST_STEPS = 10

/** One step of Settings' adjustment, in percent. */
const val PERCENT_PER_ADJUST_STEP = 0.5

/** Settings' adjustment as a percentage: 0 shows as "0 %", whatever is built in. */
fun shownAdjustPercent(steps: Int): Double = steps.coerceIn(-MAX_ADJUST_STEPS, MAX_ADJUST_STEPS) *
    PERCENT_PER_ADJUST_STEP

/**
 * What a measured distance is multiplied by, for Settings' adjustment of [steps] (each
 * [PERCENT_PER_ADJUST_STEP], at most [MAX_ADJUST_STEPS] either way) and the percentage built in.
 */
fun distanceFactor(steps: Int): Double =
    1.0 + (BUILT_IN_PERCENT + shownAdjustPercent(steps)) / 100.0
