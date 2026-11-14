package com.shawnkowalchuk.milo.core.report

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import org.w3c.dom.Element

/**
 * The iPhone's report words (EnglishReportWords.kt) are Android's string resources word for
 * word: this reads app/src/main/res/values/ and fails the day a resource and its copy differ.
 */
class EnglishReportWordsTest {
    private val values = File("../app/src/main/res/values")

    private val resources: List<Element> by lazy {
        val factory = DocumentBuilderFactory.newInstance()
        values.listFiles { file -> file.name.endsWith(".xml") }.orEmpty().flatMap { file ->
            val root = factory.newDocumentBuilder().parse(file).documentElement
            val children = root.childNodes
            (0 until children.length).map { children.item(it) }.filterIsInstance<Element>()
        }
    }

    @Test
    fun `every string is the resource of the same name`() {
        val strings =
            resources.filter { it.tagName == "string" }.associate {
                it.getAttribute("name") to
                    it.textContent
            }
        for ((name, english) in ENGLISH_REPORT_STRINGS) {
            assertEquals(strings[name], english, "the string $name")
        }
    }

    @Test
    fun `every plural is the resource of the same name`() {
        for ((name, forms) in ENGLISH_REPORT_PLURALS) {
            val plural = resources.single {
                it.tagName == "plurals" &&
                    it.getAttribute("name") == name
            }
            val items = plural.getElementsByTagName("item")
            val byQuantity =
                (0 until items.length).associate {
                    val item = items.item(it) as Element
                    item.getAttribute("quantity") to item.textContent
                }
            assertEquals(byQuantity["one"], forms.first, "the plural $name, one")
            assertEquals(byQuantity["other"], forms.second, "the plural $name, other")
        }
    }
}
