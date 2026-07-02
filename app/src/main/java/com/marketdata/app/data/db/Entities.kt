package com.marketdata.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "instruments")
data class InstrumentEntity(
    @PrimaryKey val instrumentToken: Long,
    val exchangeToken: Long = 0L,
    val tradingSymbol: String,
    val name: String = "",
    val instrumentType: String = "",
    val segment: String = "",
    val exchange: String = "",
    val lotSize: Int = 1,
    val tickSize: Double = 0.05,
    val expiry: String = "",
    val strike: Double = 0.0,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloaded_files")
data class DownloadedFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val filePath: String,
    val symbols: String,
    val interval: String,
    val fromDate: String,
    val toDate: String,
    val rowCount: Int = 0,
    val downloadedAt: Long = System.currentTimeMillis(),
    val fileSize: Long = 0L
)

/** Tracks the date range we already have on-device for a given symbol+interval,
 *  so re-running a download only fetches what's missing instead of refetching
 *  the whole range every time. earliestDate lets us detect "you asked for an
 *  earlier start date than what's covered" and safely fall back to a full
 *  fetch in that case, rather than silently leaving a gap. */
@Entity(tableName = "symbol_sync", primaryKeys = ["symbol", "interval"])
data class SymbolSyncEntity(
    val symbol: String,
    val interval: String,
    val earliestDate: String,
    val latestDate: String
)

/** One growing CSV per interval that holds every symbol ever synced at that
 *  interval. SymbolSyncEntity says how far each symbol is caught up to;
 *  this says which physical file that data lives in. */
@Entity(tableName = "sync_file", primaryKeys = ["interval"])
data class SyncFileEntity(
    val interval: String,
    val filePath: String
)
