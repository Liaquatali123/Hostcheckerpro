package com.hostchecker.pro.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hostchecker.pro.domain.model.Session

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val fileName: String,
    val total: Int,
    val scanned: Int,
    val responded: Int,
    val threads: Int,
    val startedAt: Long,
    val finishedAt: Long?,
    val status: String,
    val lastIndex: Int
) {
    fun toDomain(): Session = Session(
        id = id,
        name = name,
        fileName = fileName,
        total = total,
        scanned = scanned,
        responded = responded,
        threads = threads,
        startedAt = startedAt,
        finishedAt = finishedAt,
        status = status,
        lastIndex = lastIndex
    )

    companion object {
        fun fromDomain(session: Session): SessionEntity = SessionEntity(
            id = session.id,
            name = session.name,
            fileName = session.fileName,
            total = session.total,
            scanned = session.scanned,
            responded = session.responded,
            threads = session.threads,
            startedAt = session.startedAt,
            finishedAt = session.finishedAt,
            status = session.status,
            lastIndex = session.lastIndex
        )
    }
}
