package com.hostchecker.pro.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hostchecker.pro.domain.model.AsnInfo

@Entity(tableName = "asn_cache")
data class AsnCacheEntity(
    @PrimaryKey
    val ip: String,
    val asn: String,
    val org: String,
    val cachedAt: Long
) {
    fun toDomain(): AsnInfo = AsnInfo(
        ip = ip,
        asn = asn,
        org = org,
        cachedAt = cachedAt
    )

    companion object {
        fun fromDomain(info: AsnInfo): AsnCacheEntity = AsnCacheEntity(
            ip = info.ip,
            asn = info.asn,
            org = info.org,
            cachedAt = info.cachedAt
        )
    }
}
