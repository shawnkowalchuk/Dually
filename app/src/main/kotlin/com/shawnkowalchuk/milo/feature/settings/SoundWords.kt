package com.shawnkowalchuk.milo.feature.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.data.settings.BuiltInSound
import com.shawnkowalchuk.milo.data.settings.SoundChoice
import com.shawnkowalchuk.milo.data.settings.TripSound

// The words the "Sounds" tile on Settings and each sound's own screen must say alike: what the
// two sounds are called, and what each sound to choose from is called.

/**
 * What [this] sound is called in full: the name of its own screen, and what a screen reader
 * says its switch on Settings is.
 */
@get:StringRes
internal val TripSound.title: Int
    get() = when (this) {
        TripSound.CONNECT -> R.string.settings_sound_title
        TripSound.DRIVING_OFF -> R.string.settings_trip_sound_title
    }

/** What a built-in sound is called on the screen. */
@get:StringRes
internal val BuiltInSound.label: Int
    get() = when (this) {
        BuiltInSound.CHIRP -> R.string.sound_name_chirp
        BuiltInSound.SEATBELT_CHIME -> R.string.sound_name_seatbelt_chime
        BuiltInSound.KEY_FOB -> R.string.sound_name_key_fob
        BuiltInSound.TURN_SIGNAL -> R.string.sound_name_turn_signal
        BuiltInSound.COUNTER_BELL -> R.string.sound_name_counter_bell
        BuiltInSound.COIN -> R.string.sound_name_coin
        BuiltInSound.SONAR_PING -> R.string.sound_name_sonar_ping
        BuiltInSound.CHIME -> R.string.sound_name_chime
        BuiltInSound.HORN_TAP -> R.string.sound_name_horn_tap
        BuiltInSound.ENGINE_REV -> R.string.sound_name_engine_rev
        BuiltInSound.BURNOUT -> R.string.sound_name_burnout
        BuiltInSound.RACE_START -> R.string.sound_name_race_start
    }

/**
 * What a sound of Shawn's own is called: the name of the file he picked, or "Your own sound"
 * if the phone gave none.
 */
@Composable
internal fun ownSoundName(name: String?): String =
    name ?: stringResource(R.string.settings_sound_line_own)

/**
 * The name of the sound in use: a built-in one's own name ("Seatbelt chime"; until 2026-10-13
 * "Built-in chirp" or "Built-in chime", while each sound had one), or his own sound's.
 */
@Composable
internal fun SoundChoice.inUseName(): String =
    if (ownUri == null) stringResource(builtIn.label) else ownSoundName(ownName)
