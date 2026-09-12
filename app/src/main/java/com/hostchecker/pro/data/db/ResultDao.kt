package com.hostchecker.pro.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hostchecker.pro.data.entity.ResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ResultDao {
    @Query("SELECT * FROM scan_results WHERE sessionId = :sessionId ORDER BY id DESC")
    fun getResultsForSession(sessionId: Long): Flow<List<ResultEntity>>

    @Query("SELECT * FROM scan_results WHERE sessionId = :sessionId AND failed = 0 ORDER BY id DESC")
    fun getLiveResultsForSession(sessionId: Long): Flow<List<ResultEntity>>

    @Query("SELECT * FROM scan_results WHERE sessionId = :sessionId ORDER BY id DESC LIMIT 2000")
    fun getRecentResultsForSession(sessionId: Long): Flow<List<ResultEntity>>

    @Query("SELECT * FROM scan_results WHERE sessionId = :sessionId ORDER BY id DESC")
    suspend fun getAllResultsForSessionOnce(sessionId: Long): List<ResultEntity>

    @Query("SELECT * FROM scan_results WHERE sessionId = :sessionId AND failed = 0 ORDER BY id DESC")
    suspend fun getLiveResultsForSessionOnce(sessionId: Long): List<ResultEntity>

    @Query("SELECT * FROM scan_results WHERE id IN (:ids)")
    suspend fun getResultsByIdsOnce(ids: List<Long>): List<ResultEntity>

    @Query("SELECT * FROM scan_results WHERE id = :id LIMIT 1")
    fun getResultByIdFlow(id: Long): Flow<ResultEntity?>

    @Query("SELECT * FROM scan_results WHERE id = :id LIMIT 1")
    suspend fun getResultByIdOnce(id: Long): ResultEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: ResultEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResults(results: List<ResultEntity>)

    @Query("DELETE FROM scan_results WHERE sessionId = :sessionId")
    suspend fun deleteResultsForSession(sessionId: Long)

    @Query("DELETE FROM scan_results WHERE id = :id")
    suspend fun deleteResultById(id: Long)

    @Query("DELETE FROM scan_results WHERE id IN (:ids)")
    suspend fun deleteResultsByIds(ids: List<Long>)
}
