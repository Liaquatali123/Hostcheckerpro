package com.hostchecker.pro.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hostchecker.pro.data.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    fun getSessionById(id: Long): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionByIdOnce(id: Long): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity): Long

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("UPDATE sessions SET scanned = :scanned, responded = :responded, lastIndex = :lastIndex, status = :status WHERE id = :id")
    suspend fun updateProgress(id: Long, scanned: Int, responded: Int, lastIndex: Int, status: String)

    @Query("UPDATE sessions SET status = :status, finishedAt = :finishedAt WHERE id = :id")
    suspend fun markFinished(id: Long, finishedAt: Long, status: String)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Long)
}
