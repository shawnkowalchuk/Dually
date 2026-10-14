package com.shawnkowalchuk.milo.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The app was called MilO until 2026-10-13 and is Dually Trip Tracker since (ADR-010). Google
 * Play had refused the old name as too close to another app's, so the old name must not come
 * back in anything a user reads or hears: a screen, a notification, the widget, the car's
 * display, a line of the event log, a file the app writes or the name it suggests for one, the
 * email it prepares, the list of changes. The old addresses went with it (`milotriplog.top`,
 * and the repository's old address).
 *
 * Inside the code the old name stays: packages, classes, resource names and the names of what
 * is stored. A user sees none of those, and renaming what is stored would lose it. The few
 * texts in the code that hold such a name are listed below, each with its reason.
 */
class BrandNameTest {
    // Gradle runs the unit tests from the module's folder, app/.
    private val values = File("src/main/res/values")
    private val sources = File("src/main/kotlin")
    private val changelog = File("src/main/assets/changelog.json")

    /** The old name in any spelling, which also finds the old website's and repository's. */
    private val oldName = Regex("milo", RegexOption.IGNORE_CASE)

    /**
     * The texts in the code that may hold the old name: each is a name of the code's own or of
     * something stored, and none is put in front of a user as words.
     */
    private val namesOfTheCode =
        listOf(
            // The code's own package, in the names of its intents' actions and extras. Every
            // such intent is aimed at one of the app's own classes by name.
            Regex("""^com\.shawnkowalchuk\.milo\.(action|extra)\.[A-Z_]+$"""),
            // The main database's file. A new name would be a new, empty database.
            Regex("""^milo\.db$"""),
            // What the export file calls itself. A file written before the rename says this,
            // and has to be read.
            Regex("""^milo-export$"""),
            // What a safety copy was called before the rename: one that is on a phone under
            // this name is still found. New ones are named after Dually.
            Regex("""^MilO-before-import-$"""),
            // The name of a thread, which only a stack trace shows.
            Regex("""^milo-clock-anchor$"""),
            // A programmer's mistake, said to the programmer: it names a class.
            Regex("""^No MiloTheme found\. Wrap the screen or preview in MiloTheme \{ \}\.$"""),
        )

    @Test
    fun `no string resource holds the old name`() {
        val found = mutableListOf<String>()
        val files = values.listFiles { file -> file.name.startsWith("strings") }.orEmpty()
        assertTrue("No string resources in ${values.absolutePath}", files.isNotEmpty())
        var read = 0
        for (file in files.sortedBy { it.name }) {
            for ((name, text) in textsOf(file)) {
                read++
                if (oldName.containsMatchIn(text)) found += "${file.name}: $name: $text"
            }
        }
        // Proof that the reader reads: the app's name is the first string of all.
        assertTrue("Only $read strings were read", read > 500)
        assertEquals("These strings still say the old name", emptyList<String>(), found)
    }

    @Test
    fun `the app is called Dually, and Dually Trip Tracker in full`() {
        val strings = values.listFiles().orEmpty().flatMap { textsOf(it) }.toMap()

        assertEquals("Dually", strings["app_name"])
        // Where the app names itself in full: the first start, the Version tile, the support
        // email, and the foot of every page of the report.
        for (name in listOf(
            "onboarding_hero",
            "whats_new_version_value",
            "settings_support_subject",
            "settings_support_about",
            "report_pdf_footer",
        )) {
            assertTrue(name, strings.getValue(name).contains("Dually Trip Tracker"))
        }
    }

    @Test
    fun `the list of changes does not hold the old name`() {
        val lines = changelog.readLines().filter { oldName.containsMatchIn(it) }

        assertEquals(
            "These lines of changelog.json still say the old name",
            emptyList<String>(),
            lines,
        )
    }

    @Test
    fun `no text the code writes holds the old name`() {
        val found = mutableListOf<String>()
        var read = 0
        for (file in sources.walkTopDown().filter { it.extension == "kt" }.sortedBy { it.path }) {
            for ((line, text) in writtenTextsOf(file)) {
                read++
                if (!oldName.containsMatchIn(text)) continue
                if (namesOfTheCode.none { it.matches(text) }) {
                    found += "${file.relativeTo(sources)}:$line: \"$text\""
                }
            }
        }
        assertTrue("Only $read texts were read", read > 1_000)
        assertEquals(
            "These texts in the code still say the old name. If one is a name of the code's " +
                "own and no user reads it, add it to the list in this test with its reason",
            emptyList<String>(),
            found,
        )
    }

    @Test
    fun `the support address is at the brand's domain`() {
        val tile = File(sources, "com/shawnkowalchuk/milo/feature/settings/SupportTile.kt")

        assertTrue(tile.readText().contains("""SUPPORT_ADDRESS = "support@dually.top""""))
    }

    /**
     * Every text of a values file that a user can be shown, by its name: a string, and each
     * form of a plural and each entry of a list, named after the resource it belongs to. A
     * comment is not a text, and neither is the name of a resource.
     */
    private fun textsOf(file: File): List<Pair<String, String>> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val texts = mutableListOf<Pair<String, String>>()
        for (tag in listOf("string", "item")) {
            val nodes = root.getElementsByTagName(tag)
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as Element
                val owner = if (tag == "item") element.parentNode as Element else element
                // An item of a style names a colour or a switch of Android's; no words.
                if (owner.tagName == "style") continue
                texts += owner.getAttribute("name") to element.textContent
            }
        }
        return texts
    }

    /**
     * Every text between quotation marks in a Kotlin file, with the number of its line, leaving
     * out what stands in a comment. Rough, and enough: a text over several lines is read line
     * by line.
     */
    private fun writtenTextsOf(file: File): List<Pair<Int, String>> {
        val quoted = Regex(""""((?:[^"\\]|\\.)*)"""")
        val texts = mutableListOf<Pair<Int, String>>()
        var inComment = false
        file.readLines().forEachIndexed { index, line ->
            val trimmed = line.trim()
            when {
                inComment -> if (trimmed.contains("*/")) inComment = false
                trimmed.startsWith("/*") -> inComment = !trimmed.contains("*/")
                trimmed.startsWith("//") || trimmed.startsWith("*") -> Unit
                else -> quoted.findAll(line).forEach { texts += index + 1 to it.groupValues[1] }
            }
        }
        return texts
    }
}
