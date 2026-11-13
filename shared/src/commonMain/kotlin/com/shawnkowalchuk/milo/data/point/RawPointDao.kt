package com.shawnkowalchuk.milo.data.point

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query

/** The SQL for the raw points table. Only [RawPointRepository] calls it. */
@Dao
interface RawPointDao {
    @Insert
    suspend fun insert(point: RawPoint): Long

    @Query("SELECT * FROM raw_points WHERE tripId = :tripId ORDER BY id")
    suspend fun findForTrip(tripId: Long): List<RawPoint>

    /** Removes every point of a trip whose row was removed. */
    @Query("DELETE FROM raw_points WHERE tripId = :tripId")
    suspend fun deleteForTrip(tripId: Long): Int

    @Query("SELECT COUNT(*) FROM raw_points")
    suspend fun countAll(): Long

    @Query("SELECT COUNT(*) FROM raw_points WHERE tripId IN (:tripIds)")
    suspend fun countForTrips(tripIds: List<Long>): Long

    /** Removes every point of [tripIds]: trips that are kept, once their month was sent. */
    @Query("DELETE FROM raw_points WHERE tripId IN (:tripIds)")
    suspend fun deleteForTrips(tripIds: List<Long>): Int
}
