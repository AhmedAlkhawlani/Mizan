package com.maknoon.mizan.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.maknoon.mizan.core.domain.network.NetworkTransport
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkProfileDao {

    @Query("SELECT * FROM network_profiles ORDER BY createdAt, id")
    fun observeAll(): Flow<List<NetworkProfileEntity>>

    @Query("SELECT * FROM network_profiles ORDER BY createdAt, id")
    suspend fun all(): List<NetworkProfileEntity>

    /** The profile bound to one kind of connection; the unique index allows at most one. */
    @Query("SELECT * FROM network_profiles WHERE transport = :transport AND systemMetered = :systemMetered LIMIT 1")
    suspend fun boundTo(transport: NetworkTransport, systemMetered: Boolean): NetworkProfileEntity?

    /**
     * Inserts a new profile or replaces the one for the same connection kind. `REPLACE` also
     * keeps the unique index from rejecting a re-bind.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: NetworkProfileEntity): Long

    @Query("DELETE FROM network_profiles WHERE id = :id")
    suspend fun delete(id: Long)
}
