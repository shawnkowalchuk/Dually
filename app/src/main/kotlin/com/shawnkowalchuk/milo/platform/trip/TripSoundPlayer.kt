package com.shawnkowalchuk.milo.platform.trip

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.annotation.RawRes
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.data.settings.TripSound
import java.io.File
import java.io.IOException

/** What the event log calls each sound: the names the Settings screen gives them. */
internal val TripSound.inLog: String
    get() = when (this) {
        TripSound.CONNECT -> "Connect sound"
        TripSound.DRIVING_OFF -> "Trip-start sound"
    }

/** The sound built into the app for each, in `res/raw`. */
@get:RawRes
private val TripSound.bundled: Int
    get() = when (this) {
        TripSound.CONNECT -> R.raw.trip_start_chirp
        TripSound.DRIVING_OFF -> R.raw.trip_go_chime
    }

/** And what the event log calls it. */
private val TripSound.bundledInLog: String
    get() = when (this) {
        TripSound.CONNECT -> "the bundled chirp"
        TripSound.DRIVING_OFF -> "the bundled chime"
    }

/** What the event log calls the pop of MilO's one-year party, and the sound built in for it. */
private const val PARTY_POP_IN_LOG = "Party pop"
private const val PARTY_POP_BUNDLED_IN_LOG = "the bundled pop"

/**
 * Plays the two sounds ([TripSound]), each once: Shawn's audible proof that the truck connected
 * and a trip is being recorded (the connect sound), and since 2026-10-08 that the truck has
 * driven off with the trip recording (the trip-start sound).
 *
 * - **They are played as an alarm,** since 2026-10-07, by the owner's decision ("Sound: Always
 *   play"): he missed one once because the phone was in Bedtime mode. The audio attributes say
 *   "alarm", so the phone plays them at the alarm volume, and the ringer being on silent or on
 *   vibrate does not mute them. Do Not Disturb and Bedtime mode let alarms through unless they
 *   were set not to. With the alarm volume at zero they are played and not heard. Nothing here
 *   reads the ringer mode or changes a volume. (Until then the sound was a notification sound,
 *   muted by all four.)
 * - **They ask for the phone's own speaker.** At the moment of a Bluetooth connect the truck's
 *   audio is not ready, and a stereo drops the first second or two of a new stream, which is
 *   most of a short sound. Android treats the request as a preference and may route it
 *   elsewhere.
 * - **They are played by the service, not by a notification channel**, because MIUI is reported
 *   to switch channel sounds off (docs/research/2026-10-03-miui-dev-bluetooth-audio.md).
 *
 * The bundled sounds are original and synthesised, made by `tools/make_trip_start_chirp.py`:
 * `res/raw/trip_start_chirp.wav` for the connect sound (the name is older than the second sound
 * and stays, because a clip on Shawn's Mac replaces it by that name, README "Your own connect
 * sound") and `res/raw/trip_go_chime.wav` for the trip-start sound. If Shawn has chosen one of
 * his own audio files for a sound, MilO's copy of it is played (`OwnTripSound`), and the bundled
 * one is the fallback whenever the copy cannot be: it is gone, or it does not play after all.
 *
 * **A third sound, on one day a year:** the pop of MilO's one-year party ([playPartyPop]),
 * `res/raw/party_pop.wav`, made by `tools/make_party_pop.py`. It is played as the two are, by
 * the same means and at the same volume, and takes its turn among them. It has no setting of
 * its own; who asks for it, and when, is `platform/party`'s business.
 *
 * **One sound at a time,** in one of two ways:
 * - [waitTurn] true, the trip service's player: a sound asked for while another plays starts
 *   when that one has ended. The trip-start sound can come seconds after the connect sound, and
 *   a long clip of his own must not be cut off by it. One waits at most; a newer one replaces it.
 * - false, the Play button on the Settings screen: the sound that is playing is cut off, so two
 *   presses of Play start the sound again and do not play two on top of each other.
 *
 * Call it on the main thread, which is also where the player reports back.
 *
 * @param onNote a line for the event log.
 */
class TripSoundPlayer(
    private val context: Context,
    private val waitTurn: Boolean,
    private val onNote: (String) -> Unit,
) {
    /** The sound that is playing, if one is. */
    private var playing: MediaPlayer? = null

    /** With [waitTurn], the sound asked for while another played: how to ask for it again. */
    private var waiting: (() -> Unit)? = null

    /**
     * @param ownSoundUri MilO's copy of the file chosen for [which], or null for its bundled
     * sound.
     */
    // Lint offers String.toUri() from core-ktx, a library MilO does not declare (it is only on
    // the classpath through other libraries). One call does not justify adding it.
    @SuppressLint("UseKtx")
    fun play(which: TripSound, ownSoundUri: String?) {
        if (waitsItsTurn { play(which, ownSoundUri) }) return
        if (ownSoundUri == null) {
            playBundled(which)
            return
        }
        val started =
            start(which.inLog, "the chosen sound", onFailure = { playBundled(which) }) { player ->
                player.setDataSource(context, Uri.parse(ownSoundUri))
            }
        if (!started) playBundled(which)
    }

    /** Plays the pop of MilO's one-year party: the sound built in for it, and no other. */
    fun playPartyPop() {
        if (waitsItsTurn(::playPartyPop)) return
        start(PARTY_POP_IN_LOG, PARTY_POP_BUNDLED_IN_LOG, onFailure = {}) { player ->
            player.setBundled(R.raw.party_pop)
        }
    }

    /**
     * With [waitTurn] and a sound playing: keeps [again] for when that sound has ended.
     *
     * @return true if the sound that was asked for has to wait.
     */
    private fun waitsItsTurn(again: () -> Unit): Boolean {
        if (!waitTurn || playing == null) return false
        waiting = again
        return true
    }

    private fun playBundled(which: TripSound) {
        start(which.inLog, which.bundledInLog, onFailure = {}) { player ->
            player.setBundled(which.bundled)
        }
    }

    private fun MediaPlayer.setBundled(@RawRes sound: Int) {
        context.resources.openRawResourceFd(sound).use { file ->
            setDataSource(file.fileDescriptor, file.startOffset, file.length)
        }
    }

    /**
     * Prepares and plays one sound, and releases the player when it has finished or failed.
     *
     * @param inLog what the event log calls the sound.
     * @param onFailure called if the player fails after it was handed to Android (while
     * preparing or playing). A failure before that is reported by the return value.
     * @return false if the sound could not even be handed to Android.
     */
    private fun start(
        inLog: String,
        describe: String,
        onFailure: () -> Unit,
        setSource: (MediaPlayer) -> Unit,
    ): Boolean {
        playing?.release()
        val player = MediaPlayer()
        playing = player
        return try {
            player.setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            setSource(player)
            phoneSpeaker()?.let(player::setPreferredDevice)
            player.setOnPreparedListener(MediaPlayer::start)
            player.setOnCompletionListener { done ->
                finished(done)
                nextTurn()
            }
            player.setOnErrorListener { failed, what, extra ->
                finished(failed)
                onNote("$inLog failed while playing $describe (error $what/$extra)")
                onFailure()
                nextTurn()
                true
            }
            // Asynchronous: reading a file Shawn chose can be slow, and this is the main thread.
            player.prepareAsync()
            onNote("$inLog: playing $describe")
            true
        } catch (unreadable: IOException) {
            abandon(inLog, player, describe, unreadable)
        } catch (denied: SecurityException) {
            // The permission to read a chosen file is lost when the file is moved or deleted.
            abandon(inLog, player, describe, denied)
        } catch (invalid: IllegalArgumentException) {
            abandon(inLog, player, describe, invalid)
        } catch (wrongState: IllegalStateException) {
            abandon(inLog, player, describe, wrongState)
        }
    }

    /** Hands the player back to Android, and forgets it unless a newer sound has replaced it. */
    private fun finished(player: MediaPlayer) {
        player.release()
        if (playing === player) playing = null
    }

    /**
     * A sound has ended, or failed with nothing played in its place: the one that waited, if
     * one did, plays now.
     */
    private fun nextTurn() {
        if (playing != null) return
        val again = waiting ?: return
        waiting = null
        again()
    }

    private fun abandon(
        inLog: String,
        player: MediaPlayer,
        describe: String,
        failure: Exception,
    ): Boolean {
        finished(player)
        onNote("$inLog could not play $describe: $failure")
        return false
    }

    private fun phoneSpeaker(): AudioDeviceInfo? = context
        .getSystemService(AudioManager::class.java)
        .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
}

/**
 * Whether Android can play [file], asked before it becomes one of the two sounds: null if it can,
 * else what the player said. A file the player can prepare is one it can play.
 *
 * It reads the start of the file and can take a moment, so it is not for the main thread.
 */
fun playbackProblem(file: File): String? {
    val player = MediaPlayer()
    return try {
        player.setDataSource(file.path)
        player.prepare()
        null
    } catch (unreadable: IOException) {
        // What the player throws for a file that is not audio, or is damaged.
        unreadable.toString()
    } catch (invalid: IllegalArgumentException) {
        invalid.toString()
    } catch (wrongState: IllegalStateException) {
        wrongState.toString()
    } catch (denied: SecurityException) {
        denied.toString()
    } finally {
        player.release()
    }
}
