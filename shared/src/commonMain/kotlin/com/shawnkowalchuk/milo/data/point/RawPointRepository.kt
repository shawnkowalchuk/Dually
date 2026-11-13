package com.shawnkowalchuk.milo.data.point

/** The only way the rest of the app reads or writes raw GPS points. */
class RawPointRepository(private val dao: RawPointDao) {
    /** Stores one fix. The id on [point] is ignored; the database assigns it. */
    suspend fun add(point: RawPoint) {
        dao.insert(point)
    }

    /** Every stored fix of a trip, in the order recorded. */
    suspend fun pointsForTrip(tripId: Long): List<RawPoint> = dao.findForTrip(tripId)

    /**
     * Removes every fix of a trip that was removed for good. The points are in a database file
     * of their own, so nothing removes them with the trip's row.
     */
    suspend fun removeForTrip(tripId: Long) {
        dao.deleteForTrip(tripId)
    }

    /** How many fixes are stored, of every trip. */
    suspend fun countAll(): Long = dao.countAll()

    /** How many fixes [tripIds] have stored. */
    suspend fun countFor(tripIds: List<Long>): Long =
        tripIds.chunked(IDS_PER_QUERY).sumOf { dao.countForTrips(it) }

    /**
     * Removes every fix of [tripIds], trips that stay in the list: their distance, times and
     * positions are stored on the trip, and no screen and no report reads a fix of a closed trip
     * again. Never pass the trip being recorded.
     *
     * @return how many fixes were removed.
     */
    suspend fun removeFor(tripIds: List<Long>): Long =
        tripIds.chunked(IDS_PER_QUERY).sumOf { dao.deleteForTrips(it).toLong() }
}

/**
 * Trip ids per query, well under the most values SQLite takes in one statement. A month has a
 * few hundred trips at most, so this is nearly always one query.
 */
private const val IDS_PER_QUERY = 500
