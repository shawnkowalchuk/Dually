package com.shawnkowalchuk.milo.core.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceBookTest {
    private val oil = ServiceItem(0, null, "Oil change", 8_000, 6, 1_000L, 120_000)
    private val tires = ServiceItem(0, null, "Tire rotation", 10_000, null, 1_000L, 120_000)

    @Test
    fun `a new item gets the next number, and a saved one replaces its own`() {
        val book = ServiceBook().saved(oil).saved(tires)
        assertEquals(listOf(1L, 2L), book.items.map { it.id })

        val changed = book.saved(book.items[0].copy(everyKm = 5_000))
        assertEquals(2, changed.items.size)
        assertEquals(5_000, changed.items.first { it.id == 1L }.everyKm)
    }

    @Test
    fun `Done keeps the record and counts the next one from it`() {
        val book =
            ServiceBook(remindedFor = mapOf(1L to 1_000L))
                .saved(oil)
                .done(ServiceRecord(1, 5_000L, 128_000, "Synthetic", 8_999))
        val item = book.items.single()

        assertEquals(5_000L, item.lastDoneAtMs)
        assertEquals(128_000L, item.lastDoneKm)
        assertEquals(1, book.historyOf(1).size)
        // A new due point is told again.
        assertTrue(book.remindedFor.isEmpty())
    }

    @Test
    fun `Done without an odometer keeps the last one`() {
        val book = ServiceBook().saved(oil).done(ServiceRecord(1, 5_000L, null))
        assertEquals(120_000L, book.items.single().lastDoneKm)
    }

    @Test
    fun `the history is newest first, and goes with its item`() {
        val book =
            ServiceBook()
                .saved(oil)
                .saved(tires)
                .done(ServiceRecord(1, 2_000L, null))
                .done(ServiceRecord(1, 3_000L, null))
                .done(ServiceRecord(2, 4_000L, null))

        assertEquals(listOf(3_000L, 2_000L), book.historyOf(1).map { it.doneAtMs })

        val left = book.deleted(1)
        assertEquals(listOf(2L), left.items.map { it.id })
        assertEquals(listOf(2L), left.records.map { it.itemId })
    }
}
