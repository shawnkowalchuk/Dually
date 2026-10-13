package com.shawnkowalchuk.milo.platform.trip

import androidx.annotation.RawRes
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.data.settings.BuiltInSound

// The built-in sounds ([BuiltInSound]) as the app carries them: which file in `res/raw` each
// one is, and what the event log calls it. All are original and synthesised, and none is a
// recording. The chirp and the chime are made by `tools/make_trip_start_chirp.py`, the ten of
// 2026-10-13 by `tools/make_more_sounds.py`, each by the one function that script's list names
// beside its file. The files are committed; the build does not run the scripts.
//
// Each sound is named here by its resource itself and never looked up by a name at run time,
// so the release build's shrinking of unused resources keeps all twelve.

/**
 * The file of [this] sound in `res/raw`.
 *
 * The chirp's file keeps the name `trip_start_chirp`, which is older than the second sound: a
 * clip on Shawn's Mac replaces it by that name in the builds made there (README, "Your own
 * connect sound").
 */
@get:RawRes
internal val BuiltInSound.bundled: Int
    get() = when (this) {
        BuiltInSound.CHIRP -> R.raw.trip_start_chirp
        BuiltInSound.SEATBELT_CHIME -> R.raw.sound_seatbelt_chime
        BuiltInSound.KEY_FOB -> R.raw.sound_key_fob
        BuiltInSound.TURN_SIGNAL -> R.raw.sound_turn_signal
        BuiltInSound.COUNTER_BELL -> R.raw.sound_counter_bell
        BuiltInSound.COIN -> R.raw.sound_coin
        BuiltInSound.SONAR_PING -> R.raw.sound_sonar_ping
        BuiltInSound.CHIME -> R.raw.trip_go_chime
        BuiltInSound.HORN_TAP -> R.raw.sound_horn_tap
        BuiltInSound.ENGINE_REV -> R.raw.sound_engine_rev
        BuiltInSound.BURNOUT -> R.raw.sound_burnout
        BuiltInSound.RACE_START -> R.raw.sound_race_start
    }

/**
 * What the event log calls [this] sound: "Connect sound: playing the bundled chirp". The chirp
 * and the chime are called what they were called while they were the only two.
 */
internal val BuiltInSound.inLog: String
    get() = when (this) {
        BuiltInSound.CHIRP -> "the bundled chirp"
        BuiltInSound.SEATBELT_CHIME -> "the bundled seatbelt chime"
        BuiltInSound.KEY_FOB -> "the bundled key fob"
        BuiltInSound.TURN_SIGNAL -> "the bundled turn signal"
        BuiltInSound.COUNTER_BELL -> "the bundled counter bell"
        BuiltInSound.COIN -> "the bundled coin"
        BuiltInSound.SONAR_PING -> "the bundled sonar ping"
        BuiltInSound.CHIME -> "the bundled chime"
        BuiltInSound.HORN_TAP -> "the bundled horn tap"
        BuiltInSound.ENGINE_REV -> "the bundled engine rev"
        BuiltInSound.BURNOUT -> "the bundled burnout"
        BuiltInSound.RACE_START -> "the bundled race start"
    }
