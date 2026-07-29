package com.hemanth.vaani.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaaniDao {

    // --- Call log ---
    @Insert
    suspend fun insertCallLog(entry: CallLogEntity)

    @Query("SELECT * FROM call_log ORDER BY timestampMillis DESC")
    fun observeCallLog(): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_log ORDER BY timestampMillis DESC LIMIT 1")
    suspend fun getLastCall(): CallLogEntity?

    @Query("SELECT * FROM call_log ORDER BY timestampMillis DESC LIMIT :limit")
    suspend fun getRecentCalls(limit: Int): List<CallLogEntity>

    // --- Spam numbers ---
    @Query("SELECT * FROM spam_numbers WHERE phoneNumber = :number LIMIT 1")
    suspend fun findSpamEntry(number: String): SpamNumberEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSpamEntry(entry: SpamNumberEntity)

    @Query("SELECT * FROM spam_numbers")
    fun observeSpamNumbers(): Flow<List<SpamNumberEntity>>

    // --- Whitelist ---
    @Query("SELECT * FROM whitelist WHERE phoneNumber = :number LIMIT 1")
    suspend fun findWhitelistEntry(number: String): WhitelistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWhitelistEntry(entry: WhitelistEntity)

    @Query("SELECT * FROM whitelist ORDER BY label ASC")
    fun observeWhitelist(): Flow<List<WhitelistEntity>>

    @Query("DELETE FROM whitelist WHERE phoneNumber = :number")
    suspend fun removeFromWhitelist(number: String)
}
