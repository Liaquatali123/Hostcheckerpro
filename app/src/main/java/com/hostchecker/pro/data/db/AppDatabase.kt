package com.hostchecker.pro.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hostchecker.pro.data.entity.AsnCacheEntity
import com.hostchecker.pro.data.entity.ResultEntity
import com.hostchecker.pro.data.entity.SessionEntity

@Database(
    entities = [
        SessionEntity::class,
        ResultEntity::class,
        AsnCacheEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun resultDao(): ResultDao
    abstract fun asnCacheDao(): AsnCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scan_results ADD COLUMN scheme TEXT NOT NULL DEFAULT 'HTTPS'")
                db.execSQL("ALTER TABLE scan_results ADD COLUMN requestedUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE scan_results ADD COLUMN finalUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE scan_results ADD COLUMN originalCode INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE scan_results ADD COLUMN finalCode INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE scan_results ADD COLUMN redirectCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE scan_results ADD COLUMN redirectChain TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE scan_results ADD COLUMN httpMethod TEXT NOT NULL DEFAULT 'GET'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_scan_results_sessionId_host ON scan_results(sessionId, host)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "host_checker.db"
                ).addMigrations(MIGRATION_1_2)
                 .fallbackToDestructiveMigration()
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

