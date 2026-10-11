package com.shawnkowalchuk.milo.feature.greeting

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shawnkowalchuk.milo.core.designsystem.component.MascotGreetingKind
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.FirstRunStage
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.setFirstRunStage
import com.shawnkowalchuk.milo.data.settings.setGreetingsShown
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import com.shawnkowalchuk.milo.platform.trip.UnreadableSettingsFile
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A settings file past the first start that can be read and refuses every write. */
private object PastFirstStartReadOnlyFile : DataStore<Preferences> {
    override val data: Flow<Preferences> =
        flowOf(mutablePreferencesOf(stringPreferencesKey("first_run_stage") to "done"))

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = throw IOException("the disk is full")
}

/**
 * Which greeting a start gets and how the turn is passed on, on a settings file held in memory;
 * and what the Log is told when a picture of the mascot cannot be read.
 */
// A ViewModel's coroutines run on the main dispatcher, which a plain JVM test has to supply
// (setMain), and runCurrent() lets them run. Both are marked experimental by the coroutines
// library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class GreetingViewModelTest {
    private val log = FakeEventLogDao()

    /** A fresh start of MilO on [file]: its ViewModel, with the settings read. */
    private fun TestScope.freshStart(file: DataStore<Preferences>): GreetingViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel =
            GreetingViewModel(
                settings = SettingsStore(file),
                eventLog = EventLogRepository(log),
                clock = { 42L },
            )
        runCurrent()
        return viewModel
    }

    /** A phone that had MilO before the greetings took turns: past its first start, no count. */
    private suspend fun phoneFromBeforeTheTurns() =
        FakeSettingsFile().also { SettingsStore(it).setFirstRunStage(FirstRunStage.DONE) }

    private val errors: List<String>
        get() = log.entries.filter { it.category == EventCategory.ERROR }.map { it.message }

    @After
    fun giveTheMainDispatcherBack() {
        Dispatchers.resetMain()
    }

    @Test
    fun `nothing is due until the settings are read`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel =
            GreetingViewModel(SettingsStore(phoneFromBeforeTheTurns()), EventLogRepository(log)) {
                0L
            }
        assertNull(viewModel.due.value)

        runCurrent()

        assertEquals(MascotGreetingKind.MOONWALK, viewModel.due.value)
    }

    @Test
    fun `after the update the first greeting is the moonwalk, and nothing is stored for looking`() =
        runTest {
            val file = phoneFromBeforeTheTurns()

            val viewModel = freshStart(file)

            assertEquals(MascotGreetingKind.MOONWALK, viewModel.due.value)
            // A start that shows nothing (animations off, another screen on top) leaves the turn.
            assertNull(SettingsStore(file).current().greetingsShown)
            assertEquals(MascotGreetingKind.MOONWALK, freshStart(file).due.value)
        }

    @Test
    fun `a greeting that is shown passes the turn on, start after start`() = runTest {
        val file = phoneFromBeforeTheTurns()
        val seen = mutableListOf<MascotGreetingKind?>()

        repeat(6) {
            val start = freshStart(file)
            seen += start.due.value
            start.onShown()
            runCurrent()
        }

        assertEquals(
            listOf(
                MascotGreetingKind.MOONWALK,
                MascotGreetingKind.WAVE,
                MascotGreetingKind.MOONWALK,
                MascotGreetingKind.WAVE,
                MascotGreetingKind.MOONWALK,
                MascotGreetingKind.WAVE,
            ),
            seen,
        )
        assertEquals(7, SettingsStore(file).current().greetingsShown)
    }

    @Test
    fun `the greeting that is showing is not changed under itself when its turn is stored`() =
        runTest {
            val file = phoneFromBeforeTheTurns()
            val viewModel = freshStart(file)

            viewModel.onShown()
            runCurrent()

            assertEquals(2, SettingsStore(file).current().greetingsShown)
            assertEquals(MascotGreetingKind.MOONWALK, viewModel.due.value)
            // Told twice, the turn is still taken once.
            viewModel.onShown()
            runCurrent()
            assertEquals(2, SettingsStore(file).current().greetingsShown)
        }

    @Test
    fun `a new phone's count begins in its first start, and its first greeting is the wave`() =
        runTest {
            val file = FakeSettingsFile()

            val first = freshStart(file)

            assertEquals(MascotGreetingKind.WAVE, first.due.value)
            assertEquals(0, SettingsStore(file).current().greetingsShown)
            // The first start itself shows no greeting. Once it is over, the wave is still due.
            SettingsStore(file).setFirstRunStage(FirstRunStage.DONE)
            val second = freshStart(file)
            assertEquals(MascotGreetingKind.WAVE, second.due.value)
            second.onShown()
            runCurrent()
            assertEquals(MascotGreetingKind.MOONWALK, freshStart(file).due.value)
        }

    @Test
    fun `a count that is stored is followed, whatever the first start says`() = runTest {
        val file = FakeSettingsFile()
        SettingsStore(file).setGreetingsShown(4)

        assertEquals(MascotGreetingKind.WAVE, freshStart(file).due.value)
        assertEquals(4, SettingsStore(file).current().greetingsShown)
    }

    @Test
    fun `settings that cannot be read get the wave, and no line of their own`() = runTest {
        val viewModel = freshStart(UnreadableSettingsFile)

        assertEquals(MascotGreetingKind.WAVE, viewModel.due.value)
        assertEquals(emptyList<String>(), errors)
    }

    @Test
    fun `a turn that cannot be stored is one error line, and the greeting goes on`() = runTest {
        val viewModel = freshStart(PastFirstStartReadOnlyFile)
        assertEquals(MascotGreetingKind.MOONWALK, viewModel.due.value)

        viewModel.onShown()
        runCurrent()

        assertEquals(MascotGreetingKind.MOONWALK, viewModel.due.value)
        assertEquals(listOf("MilO could not store how many greetings it has shown (2)"), errors)
    }

    @Test
    fun `a wave that cannot be read is one error line, with what went wrong`() = runTest {
        val viewModel = freshStart(FakeSettingsFile())
        assertEquals(0, log.entries.size)

        val why = "mascot_stop_and_wave is not an animated picture"
        viewModel.onUnreadable(MascotGreetingKind.WAVE, IOException(why))
        runCurrent()

        val line = log.entries.single()
        assertEquals(42L, line.atMs)
        assertEquals(EventCategory.ERROR, line.category)
        assertEquals("MilO did not greet: a picture of its mascot could not be read", line.message)
        assertTrue(line.detail.orEmpty().contains(why))
    }

    @Test
    fun `a moonwalk that cannot be read is one error line, which says that the wave stands in`() =
        runTest {
            val viewModel = freshStart(FakeSettingsFile())

            val why = "mascot_stop_and_spin is not an animated picture"
            viewModel.onUnreadable(MascotGreetingKind.MOONWALK, IOException(why))
            runCurrent()

            val line = log.entries.single()
            assertEquals(EventCategory.ERROR, line.category)
            assertEquals(
                "MilO's mascot did not moonwalk: a picture of him could not be read. " +
                    "MilO shows the wave instead",
                line.message,
            )
            assertTrue(line.detail.orEmpty().contains(why))
        }
}
