package com.hostchecker.pro.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hostchecker.pro.data.entity.AsnCacheEntity

@Dao
interface AsnCacheDao {
    @Query("SELECT * FROM asn_cache WHERE ip = :ip LIMIT 1")
    suspend fun getAsn(ip: String): AsnCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsn(entity: AsnCacheEntity)

    @Query("DELETE FROM asn_cache WHERE cachedAt < :olderThan")
    suspend fun cleanExpired(olderThan: Long)
}
