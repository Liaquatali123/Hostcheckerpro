package com.hostchecker.pro.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hostchecker.pro.data.entity.AsnCacheEntity
import com.hostchecker.pro.data.entity.ResultEntity
import com.hostchecker.pro.data.entity.SessionEntity

@Database(
    entities = [
        SessionEntity::class,
        ResultEntity::class,
        AsnCacheEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun resultDao(): ResultDao
    abstract fun asnCacheDao(): AsnCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "host_checker.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
