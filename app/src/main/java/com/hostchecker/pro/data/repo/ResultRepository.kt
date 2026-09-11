package com.hostchecker.pro.data.repo

import com.hostchecker.pro.data.db.AsnCacheDao
import com.hostchecker.pro.data.db.ResultDao
import com.hostchecker.pro.data.entity.AsnCacheEntity
import com.hostchecker.pro.data.entity.ResultEntity
import com.hostchecker.pro.domain.model.AsnInfo
import com.hostchecker.pro.domain.model.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ResultRepository(
    private val resultDao: ResultDao,
    private val asnCacheDao: AsnCacheDao
) {
    fun getResultsForSession(sessionId: Long): Flow<List<ScanResult>> =
        resultDao.getResultsForSession(sessionId).map { list -> list.map { it.toDomain() } }

    fun getLiveResultsForSession(sessionId: Long): Flow<List<ScanResult>> =
        resultDao.getLiveResultsForSession(sessionId).map { list -> list.map { it.toDomain() } }

    fun getRecentResultsForSession(sessionId: Long): Flow<List<ScanResult>> =
        resultDao.getRecentResultsForSession(sessionId).map { list -> list.map { it.toDomain() } }

    suspend fun getAllResultsOnce(sessionId: Long): List<ScanResult> = withContext(Dispatchers.IO) {
        resultDao.getAllResultsForSessionOnce(sessionId).map { it.toDomain() }
    }

    suspend fun getLiveResultsOnce(sessionId: Long): List<ScanResult> = withContext(Dispatchers.IO) {
        resultDao.getLiveResultsForSessionOnce(sessionId).map { it.toDomain() }
    }

    suspend fun getResultsByIdsOnce(ids: List<Long>): List<ScanResult> = withContext(Dispatchers.IO) {
        resultDao.getResultsByIdsOnce(ids).map { it.toDomain() }
    }

    fun getResultByIdFlow(id: Long): Flow<ScanResult?> =
        resultDao.getResultByIdFlow(id).map { it?.toDomain() }

    suspend fun getResultByIdOnce(id: Long): ScanResult? = withContext(Dispatchers.IO) {
        resultDao.getResultByIdOnce(id)?.toDomain()
    }

    suspend fun insertResult(result: ScanResult): Long = withContext(Dispatchers.IO) {
        resultDao.insertResult(ResultEntity.fromDomain(result))
    }

    suspend fun insertResults(results: List<ScanResult>) = withContext(Dispatchers.IO) {
        resultDao.insertResults(results.map { ResultEntity.fromDomain(it) })
    }

    suspend fun deleteResult(id: Long) = withContext(Dispatchers.IO) {
        resultDao.deleteResultById(id)
    }

    suspend fun deleteResults(ids: List<Long>) = withContext(Dispatchers.IO) {
        resultDao.deleteResultsByIds(ids)
    }

    suspend fun getCachedAsn(ip: String): AsnInfo? = withContext(Dispatchers.IO) {
        val cached = asnCacheDao.getAsn(ip)
        if (cached != null) {
            val ttl = 30L * 24 * 60 * 60 * 1000 // 30 days
            if (System.currentTimeMillis() - cached.cachedAt < ttl) {
                return@withContext cached.toDomain()
            }
        }
        null
    }

    suspend fun cacheAsn(info: AsnInfo) = withContext(Dispatchers.IO) {
        asnCacheDao.insertAsn(AsnCacheEntity.fromDomain(info))
    }
}
