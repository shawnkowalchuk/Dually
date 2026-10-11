package com.shawnkowalchuk.milo.platform.sentrun

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.annotation.RawRes
import com.shawnkowalchuk.milo.R
import java.io.IOException

/** The two sounds of the mascot's run across the Report screen. */
enum class RunSound {
    /** Wheels whirring up, as he gets to the spot he runs on. */
    WHIRR,

    /** Rubber going sideways, as he brakes and slides out. */
    SQUEAL,
}

/** The sound built into the app for each, in `res/raw`. */
@get:RawRes
private val RunSound.file: Int
    get() = when (this) {
        RunSound.WHIRR -> R.raw.report_run_whirr
        RunSound.SQUEAL -> R.raw.report_run_squeal
    }

/** What the event log calls each. */
private val RunSound.inLog: String
    get() = when (this) {
        RunSound.WHIRR -> "The whirr of the mascot's run"
        RunSound.SQUEAL -> "The squeal of the mascot's slide"
    }

/**
 * Plays the two sounds of the mascot's run across the Report screen ([RunSound]), which Shawn
 * chose on 2026-10-11 with the film of it: `res/raw/report_run_whirr.wav` and
 * `res/raw/report_run_squeal.wav`, made by `tools/make_run_sounds.py` from sine waves and a
 * counted-out noise.
 *
 * **They are screen sounds, not signals for the road,** and so they are not played as the
 * connect sound, the trip-start sound and the party's pop are (`TripSoundPlayer`, which plays
 * those as an alarm so that they are heard in the phone's quiet modes). The audio attributes
 * here say "sonification": a sound of the user interface, which Android plays on its system
 * stream, at the volume it gives a phone's own screen sounds. **A phone whose ringer is on
 * silent or on vibrate stays silent:** Android mutes that stream with the ringer. Nothing here
 * reads the ringer's mode or changes a volume, and no place to play them is asked for: they
 * come out where the phone's own screen sounds do.
 *
 * TODO(debt): that a ringer on silent or on vibrate mutes them is Android's doing, seen in the
 *  audio log of an emulator with stock Android 16. Whether HyperOS on Shawn's phone does the
 *  same is not known (device check RS-4). If it does not, the ringer's mode has to be asked
 *  here before a run's sounds are made ready. See docs/FINDINGS_LOG.md, 2026-10-11.
 *
 * Whether they are played at all, and when, is not decided here (`feature/sentrun`): each is
 * started by the animation in the drawing of the screen its frame is first shown in.
 *
 * **Made ready first, started on the frame.** A sound that is only made when its frame comes
 * starts late: the first player a MilO process makes took a third of a second to begin on an
 * emulator (2026-10-11), and by then he had been on the spot for eight frames. So both are made
 * ready when a run is asked for ([prepare]), most of a second before the first is due, and
 * [play] only starts what is waiting. One that is asked for before it is ready starts as soon
 * as it is.
 *
 * [stop] ends whatever plays and lets go of whatever waits: the run was cut short. A sound that
 * plays to its end lets go of itself.
 *
 * Call it on the main thread, which is also where the players report back.
 *
 * @param onFailure told, for the event log, of a sound that could not be played.
 */
class SentRunSounds(private val context: Context, private val onFailure: (String) -> Unit) {
    /**
     * A sound that was made for a run.
     *
     * @property prepared whether Android has it ready to start.
     * @property wanted whether its frame has come.
     */
    private class Made(
        val player: MediaPlayer,
        var prepared: Boolean = false,
        var wanted: Boolean = false,
    )

    /** The sounds made for the run that is asked for or playing. */
    private val made = mutableMapOf<RunSound, Made>()

    /** Makes both sounds ready to start. What an earlier run left is let go of first. */
    fun prepare() {
        stop()
        for (sound in RunSound.entries) make(sound)
    }

    /** Its frame has come: the sound starts now, or as soon as it is ready. */
    fun play(sound: RunSound) {
        val waiting = made[sound] ?: return
        waiting.wanted = true
        if (waiting.prepared) start(sound, waiting)
    }

    /** Ends the sound that is playing, if one is, and lets go of any that waits. */
    fun stop() {
        made.values.forEach { it.player.release() }
        made.clear()
    }

    private fun make(sound: RunSound) {
        val player = MediaPlayer()
        val waiting = Made(player)
        made[sound] = waiting
        try {
            player.setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            context.resources.openRawResourceFd(sound.file).use { file ->
                player.setDataSource(file.fileDescriptor, file.startOffset, file.length)
            }
            player.setOnPreparedListener {
                waiting.prepared = true
                if (waiting.wanted) start(sound, waiting)
            }
            player.setOnCompletionListener { finished(sound, waiting) }
            player.setOnErrorListener { _, what, extra ->
                finished(sound, waiting)
                onFailure("${sound.inLog} failed while playing (error $what/$extra)")
                true
            }
            // Asynchronous, as every sound of MilO's is prepared: this is the main thread.
            player.prepareAsync()
        } catch (unreadable: IOException) {
            abandon(sound, waiting, unreadable)
        } catch (invalid: IllegalArgumentException) {
            abandon(sound, waiting, invalid)
        } catch (wrongState: IllegalStateException) {
            abandon(sound, waiting, wrongState)
        }
    }

    private fun start(sound: RunSound, waiting: Made) {
        try {
            waiting.player.start()
        } catch (wrongState: IllegalStateException) {
            abandon(sound, waiting, wrongState)
        }
    }

    /** Hands the player back to Android, and forgets it unless a newer run has made another. */
    private fun finished(sound: RunSound, waiting: Made) {
        waiting.player.release()
        if (made[sound] === waiting) made.remove(sound)
    }

    private fun abandon(sound: RunSound, waiting: Made, failure: Exception) {
        finished(sound, waiting)
        onFailure("${sound.inLog} could not be played: $failure")
    }
}
