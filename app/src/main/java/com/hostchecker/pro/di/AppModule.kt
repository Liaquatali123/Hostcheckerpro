package com.hostchecker.pro.di

import android.content.Context
import com.hostchecker.pro.data.db.AppDatabase
import com.hostchecker.pro.data.db.AsnCacheDao
import com.hostchecker.pro.data.db.ResultDao
import com.hostchecker.pro.data.db.SessionDao
import com.hostchecker.pro.data.prefs.SettingsDataStore
import com.hostchecker.pro.data.repo.ResultRepository
import com.hostchecker.pro.data.repo.SessionRepository
import com.hostchecker.pro.domain.scanner.AsnLookup
import com.hostchecker.pro.domain.scanner.CloudflareProbe
import com.hostchecker.pro.domain.scanner.HostScanner
import com.hostchecker.pro.util.NotificationHelper
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient

class AppModule(private val context: Context) {

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(64, 5, TimeUnit.MINUTES))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(context)
    }

    val sessionDao: SessionDao by lazy {
        database.sessionDao()
    }

    val resultDao: ResultDao by lazy {
        database.resultDao()
    }

    val asnCacheDao: AsnCacheDao by lazy {
        database.asnCacheDao()
    }

    val settingsDataStore: SettingsDataStore by lazy {
        SettingsDataStore(context)
    }

    val sessionRepository: SessionRepository by lazy {
        SessionRepository(sessionDao, resultDao)
    }

    val resultRepository: ResultRepository by lazy {
        ResultRepository(resultDao, asnCacheDao)
    }

    val asnLookup: AsnLookup by lazy {
        AsnLookup(okHttpClient, resultRepository)
    }

    val cloudflareProbe: CloudflareProbe by lazy {
        CloudflareProbe(okHttpClient)
    }

    val notificationHelper: NotificationHelper by lazy {
        NotificationHelper(context)
    }

    val hostScanner: HostScanner by lazy {
        HostScanner(
            baseOkHttpClient = okHttpClient,
            resultRepository = resultRepository,
            sessionRepository = sessionRepository,
            asnLookup = asnLookup,
            cloudflareProbe = cloudflareProbe
        )
    }
}
