package com.hostchecker.pro.data.repo

import com.hostchecker.pro.data.db.ResultDao
import com.hostchecker.pro.data.db.SessionDao
import com.hostchecker.pro.data.entity.SessionEntity
import com.hostchecker.pro.domain.model.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SessionRepository(
    private val sessionDao: SessionDao,
    private val resultDao: ResultDao
) {
    val allSessions: Flow<List<Session>> = sessionDao.getAllSessions()
        .map { list -> list.map { it.toDomain() } }

    fun getSession(id: Long): Flow<Session?> = sessionDao.getSessionById(id)
        .map { it?.toDomain() }

    suspend fun getSessionOnce(id: Long): Session? = withContext(Dispatchers.IO) {
        sessionDao.getSessionByIdOnce(id)?.toDomain()
    }

    suspend fun createSession(session: Session): Long = withContext(Dispatchers.IO) {
        sessionDao.insertSession(SessionEntity.fromDomain(session))
    }

    suspend fun updateSession(session: Session) = withContext(Dispatchers.IO) {
        sessionDao.updateSession(SessionEntity.fromDomain(session))
    }

    suspend fun updateProgress(id: Long, scanned: Int, responded: Int, lastIndex: Int, status: String) = withContext(Dispatchers.IO) {
        sessionDao.updateProgress(id, scanned, responded, lastIndex, status)
    }

    suspend fun finishSession(id: Long, status: String = "COMPLETED") = withContext(Dispatchers.IO) {
        sessionDao.markFinished(id, System.currentTimeMillis(), status)
    }

    suspend fun deleteSession(id: Long) = withContext(Dispatchers.IO) {
        resultDao.deleteResultsForSession(id)
        sessionDao.deleteSessionById(id)
    }
}
