package com.marketdata.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [InstrumentEntity::class, DownloadedFileEntity::class, SymbolSyncEntity::class, SyncFileEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun instrumentDao(): InstrumentDao
    abstract fun downloadedFileDao(): DownloadedFileDao
    abstract fun syncDao(): SyncDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "market_data.db"
                )
                    // v1 -> v2 added symbol_sync/sync_file for incremental downloads.
                    // Both are rebuildable caches (instruments re-fetch in one call,
                    // downloaded_files just re-lists existing CSVs), so a destructive
                    // migration is safe and avoids hand-writing a Migration for a
                    // cache table.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
