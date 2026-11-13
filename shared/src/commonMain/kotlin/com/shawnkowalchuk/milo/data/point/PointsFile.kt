package com.shawnkowalchuk.milo.data.point

import androidx.room3.Transactor
import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import com.shawnkowalchuk.milo.data.PointsDatabase

/**
 * The raw points' database file as a file: how much room it takes, and handing back the room of
 * points that were deleted. An interface so that the rules that use it run in unit tests.
 */
interface PointsFile {
    /** The bytes the file's pages hold, its free pages left out. */
    suspend fun usedBytes(): Long

    /**
     * Hands the room of deleted points back to the phone. SQLite keeps the pages of deleted rows
     * inside its file for the next rows, so a delete alone leaves the file, and what Android's
     * storage page shows for Dually, as large as it was.
     */
    suspend fun compact()
}

/** [PointsFile] for the real database, through SQLite's own statements. */
class RoomPointsFile(private val database: PointsDatabase) : PointsFile {
    override suspend fun usedBytes(): Long = database.useReaderConnection { connection ->
        val pages = connection.pragma("page_count") - connection.pragma("freelist_count")
        pages * connection.pragma("page_size")
    }

    override suspend fun compact() {
        database.useWriterConnection { connection ->
            // VACUUM writes the file again without its free pages. It cannot run inside a
            // transaction, and a writer connection is in none until one is begun. The row ids are
            // kept: the table's id is its INTEGER PRIMARY KEY, and an export reads in their order.
            connection.usePrepared("VACUUM") { it.step() }
            // The database runs in write-ahead mode: the rewritten file lies in the -wal file
            // until it is copied back. TRUNCATE copies it and empties the -wal file as well.
            connection.usePrepared("PRAGMA wal_checkpoint(TRUNCATE)") { it.step() }
        }
    }
}

private suspend fun Transactor.pragma(name: String): Long = usePrepared("PRAGMA $name") {
    it.step()
    it.getLong(0)
}
