package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.data.settings.BuiltInSound
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The loudest sample a built-in sound may have: 55 % of full scale, as the scripts write it. */
private const val LOUDEST = 0.55 * Short.MAX_VALUE

/** And the least: under it a file is silence, or was written at the wrong level. */
private const val QUIETEST = 0.45 * Short.MAX_VALUE

/** The two rates the scripts write: the chirp's and the chime's, and the party's pop's. */
private val RATES = setOf(22_050, 44_100)

/**
 * The twelve built-in sounds as files: that each sound of the app's list has a file of its own
 * in `res/raw`, that the file is a sound the player can be handed, and that the script which
 * makes the ten of 2026-10-13 names the same ten. So a sound that is added, renamed or dropped
 * in one of the three places (the list, the files, the script) fails here until all agree.
 */
class BuiltInSoundFilesTest {
    /** What each sound's file is called in `res/raw`, without ".wav". */
    private fun fileNameOf(sound: BuiltInSound): String = when (sound) {
        // Older than the second sound, and kept: a clip on the owner's Mac replaces it by
        // this name in the builds made there.
        BuiltInSound.CHIRP -> "trip_start_chirp"

        BuiltInSound.CHIME -> "trip_go_chime"

        else -> "sound_${sound.storedName}"
    }

    /** One sound file, read from its 44 bytes of header and its samples. */
    private class Wav(file: File) {
        private val bytes = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        val isPcm = tag(0) == "RIFF" && tag(8) == "WAVE" && tag(12) == "fmt " && tag(36) == "data"
        val format = bytes.getShort(20).toInt()
        val channels = bytes.getShort(22).toInt()
        val rate = bytes.getInt(24)
        val bitsPerSample = bytes.getShort(34).toInt()
        val samples = List((bytes.capacity() - HEADER) / 2) { bytes.getShort(HEADER + it * 2) }
        val seconds = samples.size.toDouble() / rate

        private fun tag(at: Int) = String(bytes.array(), at, 4, Charsets.US_ASCII)

        private companion object {
            const val HEADER = 44
        }
    }

    @Test
    fun `every sound of the list has a file of its own, and the app names that file`() {
        val resources = R.raw::class.java.fields.associate { it.getInt(null) to it.name }

        for (sound in BuiltInSound.entries) {
            assertEquals("$sound", fileNameOf(sound), resources[sound.bundled])
            assertTrue("$sound", File("$RAW/${fileNameOf(sound)}.wav").isFile)
        }
        assertEquals(12, BuiltInSound.entries.map { it.bundled }.toSet().size)
    }

    @Test
    fun `each file is one channel of 16-bit sound, short, and no louder than the chirp`() {
        for (sound in BuiltInSound.entries) {
            val wav = Wav(File("$RAW/${fileNameOf(sound)}.wav"))

            assertTrue("$sound is a WAV file of plain samples", wav.isPcm && wav.format == 1)
            assertEquals("$sound", 1, wav.channels)
            assertEquals("$sound", 16, wav.bitsPerSample)
            assertTrue("$sound at ${wav.rate} Hz", wav.rate in RATES)
            // A signal for the road, not a tune: the longest is the burnout, two seconds.
            assertTrue("$sound lasts ${wav.seconds} s", wav.seconds in 0.3..2.1)
            val loudest = wav.samples.maxOf { abs(it.toInt()) }
            assertTrue("$sound peaks at $loudest", loudest <= LOUDEST + 1)
            // And it is a sound: a file of silence, or one written far too quietly, is not.
            // (The chirp's own loudest sample is at 49 %: its notes are scaled once more.)
            assertTrue("$sound peaks at $loudest", loudest >= QUIETEST)
        }
    }

    @Test
    fun `the script that makes the ten of 2026-10-13 names the same ten files`() {
        val script = File("../tools/make_more_sounds.py").readText()
        val listed =
            Regex("""^\s+\("(sound_[a-z_]+)", [a-z_]+, (WHOLE|HALVED)\),$""", RegexOption.MULTILINE)
                .findAll(script)
                .map { it.groupValues[1] }
                .toList()

        val theTen = BuiltInSound.entries - BuiltInSound.CHIRP - BuiltInSound.CHIME
        assertEquals(theTen.map(::fileNameOf).toSet(), listed.toSet())
        assertEquals(10, listed.size)
    }

    @Test
    fun `the Log calls the chirp and the chime what it always called them, and each by a name`() {
        assertEquals("the bundled chirp", BuiltInSound.CHIRP.inLog)
        assertEquals("the bundled chime", BuiltInSound.CHIME.inLog)
        for (sound in BuiltInSound.entries) {
            // "seatbelt_chime" is stored, "the bundled seatbelt chime" is written.
            assertEquals("the bundled ${sound.storedName.replace('_', ' ')}", sound.inLog)
        }
    }

    private companion object {
        const val RAW = "src/main/res/raw"
    }
}
