package com.shawnkowalchuk.milo.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.BuiltInSound
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.TripSound
import com.shawnkowalchuk.milo.data.settings.setSoundEnabled
import com.shawnkowalchuk.milo.data.settings.sound
import com.shawnkowalchuk.milo.data.sound.MAX_OWN_SOUND_BYTES
import com.shawnkowalchuk.milo.data.sound.OwnSoundStore
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import com.shawnkowalchuk.milo.platform.trip.OwnTripSound
import com.shawnkowalchuk.milo.platform.trip.PickedAudio
import com.shawnkowalchuk.milo.platform.trip.UnreadableSettingsFile
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The screen of one of the two sounds, as its ViewModel: a press on a row chooses that sound at
 * once, Play plays the row it was pressed on, a picked file is added or refused, a sound of his
 * own is removed for both, and leaving the screen stops what Play started. On a settings file
 * held in memory, a real folder for the copies, and stand-ins for the picked file and for
 * Android's player.
 */
// A ViewModel's coroutines run on the main dispatcher, which a plain JVM test has to supply
// (setMain), and runCurrent() lets them run. Both are marked experimental by the coroutines
// library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class SoundListViewModelTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val log = FakeEventLogDao()

    /** Each press of a Play button: which sound's screen, the file of his own, the built-in. */
    private val played = mutableListOf<Triple<TripSound, String?, BuiltInSound>>()
    private var stops = 0

    /** The files "on the phone", by the address the picker hands over. */
    private val files = mutableMapOf<String, Pair<String?, ByteArray>>()

    /** Set to make Android's player refuse every file, as it does for one that is not audio. */
    private var playerSays: String? = null

    private val picked =
        object : PickedAudio {
            override fun open(uri: String): InputStream {
                val (_, bytes) = files[uri] ?: throw FileNotFoundException("No file at $uri")
                return ByteArrayInputStream(bytes)
            }

            override fun nameOf(uri: String): String? = files[uri]?.first
        }

    private fun TestScope.viewModel(
        which: TripSound,
        file: DataStore<Preferences>,
    ): SoundListViewModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val settings = SettingsStore(file)
        val eventLog = EventLogRepository(log)
        return SoundListViewModel(
            which = which,
            settings = settings,
            ownSound =
                OwnTripSound(
                    picked = picked,
                    playbackProblem = { playerSays },
                    store = OwnSoundStore(File(temporaryFolder.root, "trip_sound")),
                    settings = settings,
                    eventLog = eventLog,
                    clock = { 0L },
                    io = dispatcher,
                ),
            playSound = { sound, ownUri, builtIn -> played += Triple(sound, ownUri, builtIn) },
            stopSound = { stops++ },
            eventLog = eventLog,
            clock = { 0L },
        )
    }

    /** What the screen shows now, once everything that was asked for has run. */
    private fun TestScope.shown(viewModel: SoundListViewModel): SoundListUiState {
        val states = mutableListOf<SoundListUiState>()
        val watching = launch { viewModel.state.collect { states += it } }
        runCurrent()
        watching.cancel()
        return states.last()
    }

    private fun logged(category: EventCategory): List<String> =
        log.entries.filter { it.category == category }.map { it.message }

    @After
    fun giveTheMainDispatcherBack() {
        Dispatchers.resetMain()
    }

    // ---- Choosing ---------------------------------------------------------------------------------

    @Test
    fun `a press on a built-in row makes it the sound in use at once, for this sound only`() =
        runTest {
            val file = FakeSettingsFile()
            val viewModel = viewModel(TripSound.CONNECT, file)

            viewModel.onChooseBuiltIn(BuiltInSound.SEATBELT_CHIME)
            runCurrent()

            val now = SettingsStore(file).current()
            assertEquals(BuiltInSound.SEATBELT_CHIME, now.sound(TripSound.CONNECT).builtIn)
            assertEquals(BuiltInSound.CHIME, now.sound(TripSound.DRIVING_OFF).builtIn)
            // The row is marked, and the Log says which sound it is by name.
            val ready = shown(viewModel) as SoundListUiState.Ready
            assertEquals(
                listOf(BuiltInSound.SEATBELT_CHIME),
                ready.builtIn.filter { it.inUse }.map { it.sound },
            )
            assertEquals(
                listOf("Connect sound: the bundled seatbelt chime is now used"),
                logged(EventCategory.SERVICE),
            )
        }

    @Test
    fun `choosing does not touch the switch, and plays nothing`() = runTest {
        val file = FakeSettingsFile()
        SettingsStore(file).setSoundEnabled(TripSound.DRIVING_OFF, false)
        val viewModel = viewModel(TripSound.DRIVING_OFF, file)

        viewModel.onChooseBuiltIn(BuiltInSound.RACE_START)
        runCurrent()

        val tripStart = SettingsStore(file).current().sound(TripSound.DRIVING_OFF)
        assertFalse(tripStart.enabled)
        assertEquals(BuiltInSound.RACE_START, tripStart.builtIn)
        assertTrue(played.isEmpty())
    }

    // ---- Play ---------------------------------------------------------------------------------------

    @Test
    fun `Play plays the row it was pressed on, whether or not that row is in use`() = runTest {
        val file = FakeSettingsFile()
        val viewModel = viewModel(TripSound.CONNECT, file)

        viewModel.onPlayBuiltIn(BuiltInSound.BURNOUT)
        viewModel.onPlayBuiltIn(BuiltInSound.CHIRP)
        viewModel.onPlayOwn("file:/data/trip_sound/own_1")

        assertEquals(
            listOf(
                Triple(TripSound.CONNECT, null, BuiltInSound.BURNOUT),
                Triple(TripSound.CONNECT, null, BuiltInSound.CHIRP),
                // His own, and if its copy cannot be played, what a trip would play then.
                Triple(TripSound.CONNECT, "file:/data/trip_sound/own_1", BuiltInSound.CHIRP),
            ),
            played,
        )
        // Listening chooses nothing.
        assertEquals(MiloSettings(), SettingsStore(file).current())
    }

    @Test
    fun `Play on the trip-start sound's screen falls back on the chime`() = runTest {
        val viewModel = viewModel(TripSound.DRIVING_OFF, FakeSettingsFile())

        viewModel.onPlayOwn("file:/data/trip_sound/own_1")

        val expected =
            Triple(TripSound.DRIVING_OFF, "file:/data/trip_sound/own_1", BuiltInSound.CHIME)
        assertEquals(listOf(expected), played)
    }

    @Test
    fun `leaving the screen stops what Play started`() = runTest {
        val viewModel = viewModel(TripSound.CONNECT, FakeSettingsFile())
        viewModel.onPlayBuiltIn(BuiltInSound.ENGINE_REV)

        viewModel.onLeft()

        assertEquals(1, stops)
    }

    // ---- A sound of his own -------------------------------------------------------------------------

    @Test
    fun `a picked file joins the list, is in use here, and the other sound is left alone`() =
        runTest {
            files["content://music/1"] = "r2d2.mp3" to "beep boop".toByteArray()
            val file = FakeSettingsFile()
            SettingsStore(file).current()
            val viewModel = viewModel(TripSound.DRIVING_OFF, file)

            viewModel.onOwnSoundPicked("content://music/1")
            runCurrent()

            val ready = shown(viewModel) as SoundListUiState.Ready
            assertEquals(listOf("r2d2.mp3" to true), ready.own.map { it.name to it.inUse })
            assertTrue(ready.builtIn.none { it.inUse })
            assertFalse(ready.copying)
            assertNull(ready.problem)
            assertNull(SettingsStore(file).current().sound(TripSound.CONNECT).ownUri)
        }

    @Test
    fun `a refused file is said on the screen with its reason, and nothing has changed`() =
        runTest {
            val file = FakeSettingsFile()
            val viewModel = viewModel(TripSound.CONNECT, file)
            viewModel.onChooseBuiltIn(BuiltInSound.COIN)
            runCurrent()
            val before = SettingsStore(file).current()

            files["content://music/big"] = "long.wav" to ByteArray(MAX_OWN_SOUND_BYTES.toInt() + 1)
            viewModel.onOwnSoundPicked("content://music/big")
            runCurrent()
            assertEquals(
                SoundListProblem.TOO_LARGE,
                (shown(viewModel) as SoundListUiState.Ready).problem,
            )

            files["content://docs/1"] = "notes.txt" to "not audio".toByteArray()
            playerSays = "java.io.IOException: Prepare failed.: status=0x1"
            viewModel.onOwnSoundPicked("content://docs/1")
            runCurrent()
            val notPlayable = shown(viewModel) as SoundListUiState.Ready
            assertEquals(SoundListProblem.NOT_PLAYABLE, notPlayable.problem)

            viewModel.onOwnSoundPicked("content://music/gone")
            runCurrent()
            val notCopied = shown(viewModel) as SoundListUiState.Ready
            assertEquals(SoundListProblem.COULD_NOT_COPY, notCopied.problem)

            // The coin is still the sound, and no row of his own was added.
            assertEquals(before, SettingsStore(file).current())
            assertTrue(notCopied.own.isEmpty())
            assertEquals(3, logged(EventCategory.ERROR).size)
        }

    @Test
    fun `the next press clears what the last one said`() = runTest {
        val viewModel = viewModel(TripSound.CONNECT, FakeSettingsFile())
        viewModel.onNoFilePicker()
        assertEquals(
            SoundListProblem.NO_FILE_PICKER,
            (shown(viewModel) as SoundListUiState.Ready).problem,
        )

        viewModel.onChooseBuiltIn(BuiltInSound.KEY_FOB)
        runCurrent()

        assertNull((shown(viewModel) as SoundListUiState.Ready).problem)
    }

    @Test
    fun `removing a sound takes it from both sounds, stops it, and each plays its first sound`() =
        runTest {
            files["content://music/1"] = "mario-1-up.mp3" to "ding".toByteArray()
            val file = FakeSettingsFile()
            val connect = viewModel(TripSound.CONNECT, file)
            connect.onChooseBuiltIn(BuiltInSound.COIN)
            connect.onOwnSoundPicked("content://music/1")
            runCurrent()
            val added = SettingsStore(file).current().ownSounds.single().uri
            val tripStart = viewModel(TripSound.DRIVING_OFF, file)
            tripStart.onChooseOwn(added)
            runCurrent()
            assertEquals(added, SettingsStore(file).current().sound(TripSound.DRIVING_OFF).ownUri)

            tripStart.onRemove(added)
            runCurrent()

            val now = SettingsStore(file).current()
            assertTrue(now.ownSounds.isEmpty())
            // Not the coin he had before his own: the first of the list, on both.
            assertEquals(MiloSettings().sound(TripSound.CONNECT), now.sound(TripSound.CONNECT))
            assertEquals(
                MiloSettings().sound(TripSound.DRIVING_OFF),
                now.sound(TripSound.DRIVING_OFF),
            )
            assertEquals(1, stops)
            assertTrue(File(temporaryFolder.root, "trip_sound").listFiles().orEmpty().isEmpty())
        }

    // ---- A settings file that fails -----------------------------------------------------------------

    @Test
    fun `settings that cannot be read are said, and a press on them ends nothing`() = runTest {
        val viewModel = viewModel(TripSound.CONNECT, UnreadableSettingsFile)

        assertEquals(SoundListUiState.Unreadable, shown(viewModel))

        // Left alone, the exception would end the process, and the trip service runs in it.
        viewModel.onChooseBuiltIn(BuiltInSound.COIN)
        runCurrent()

        val errors = logged(EventCategory.ERROR)
        assertTrue(errors.toString(), "The Settings screen could not store a change" in errors)
        assertTrue(errors.toString(), "The Settings screen could not read the settings" in errors)
    }
}
