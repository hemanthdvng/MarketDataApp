package com.marketdata.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface InstrumentDao {
    @Query("SELECT * FROM instruments WHERE exchange = :exchange ORDER BY tradingSymbol ASC")
    suspend fun getByExchange(exchange: String): List<InstrumentEntity>

    @Query("SELECT * FROM instruments WHERE tradingSymbol = :symbol AND exchange = :exchange LIMIT 1")
    suspend fun getBySymbol(symbol: String, exchange: String = "NSE"): InstrumentEntity?

    @Query("SELECT * FROM instruments WHERE tradingSymbol IN (:symbols) AND exchange = :exchange")
    suspend fun getBySymbols(symbols: List<String>, exchange: String = "NSE"): List<InstrumentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(instruments: List<InstrumentEntity>)

    @Query("DELETE FROM instruments WHERE exchange = :exchange")
    suspend fun deleteByExchange(exchange: String)

    @Query("SELECT COUNT(*) FROM instruments WHERE exchange = :exchange")
    suspend fun countByExchange(exchange: String): Int

    @Query("SELECT * FROM instruments WHERE tradingSymbol LIKE :query AND exchange = 'NSE' LIMIT 20")
    suspend fun search(query: String): List<InstrumentEntity>

    // Index option underlyings (BANKNIFTY, FINNIFTY, etc.) don't reliably have
    // `name` == the code you select - Kite's dump uses the official index name
    // there instead (e.g. name="NIFTY BANK" for BANKNIFTY options), which is
    // exactly why the spot-price lookup already needed its own mapping. The
    // tradingSymbol, on the other hand, ALWAYS starts with the exact underlying
    // code ("BANKNIFTY25DEC48000CE", "RELIANCE25DEC1400CE") regardless of what
    // `name` says, so matching on that prefix is what's actually reliable here.
    @Query("SELECT DISTINCT expiry FROM instruments WHERE tradingSymbol LIKE (:underlying || '%') AND exchange = 'NFO' AND (instrumentType = 'CE' OR instrumentType = 'PE') AND expiry != '' ORDER BY expiry ASC")
    suspend fun getExpiriesForUnderlying(underlying: String): List<String>

    @Query("SELECT * FROM instruments WHERE tradingSymbol LIKE (:underlying || '%') AND exchange = 'NFO' AND expiry = :expiry AND (instrumentType = 'CE' OR instrumentType = 'PE') ORDER BY strike ASC")
    suspend fun getOptionsForExpiry(underlying: String, expiry: String): List<InstrumentEntity>

    @Query("SELECT COUNT(*) FROM instruments WHERE exchange = 'NFO'")
    suspend fun countNfo(): Int

    // `name` is the most reliable underlying identifier available in Kite's dump:
    // for stock F&O it's identical to the equity tradingsymbol used for matching
    // (see getExpiriesForUnderlying's comment), and for indices it's the display
    // name (e.g. "NIFTY BANK") which the caller maps back to its tradingsymbol
    // code separately. Either way, this gives the full live set of underlyings
    // that currently have listed options - used to power "All F&O" bulk selection
    // and the option-chain search-as-you-type suggestions.
    @Query("SELECT DISTINCT name FROM instruments WHERE exchange = 'NFO' AND (instrumentType = 'CE' OR instrumentType = 'PE') AND name != '' ORDER BY name ASC")
    suspend fun getDistinctFnoNames(): List<String>

    // segment = 'NSE' (as opposed to 'INDICES') is what actually isolates plain
    // tradeable equities/ETFs in Kite's dump - exchange alone isn't enough since
    // NIFTY/BANKNIFTY etc. index quotes also live under exchange='NSE'.
    @Query("SELECT DISTINCT tradingSymbol FROM instruments WHERE exchange = 'NSE' AND segment = 'NSE' ORDER BY tradingSymbol ASC")
    suspend fun getAllNseEquitySymbols(): List<String>
}

@Dao
interface DownloadedFileDao {
    @Query("SELECT * FROM downloaded_files ORDER BY downloadedAt DESC")
    fun getAllFiles(): Flow<List<DownloadedFileEntity>>

    @Insert
    suspend fun insert(file: DownloadedFileEntity): Long

    @Delete
    suspend fun delete(file: DownloadedFileEntity)

    @Query("DELETE FROM downloaded_files WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("UPDATE downloaded_files SET rowCount = :rowCount, toDate = :toDate, downloadedAt = :downloadedAt WHERE filePath = :filePath")
    suspend fun updateStatsByPath(filePath: String, rowCount: Int, toDate: String, downloadedAt: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM downloaded_files WHERE filePath = :filePath")
    suspend fun countByPath(filePath: String): Int
}

@Dao
interface SyncDao {
    @Query("SELECT * FROM symbol_sync WHERE interval = :interval")
    suspend fun getCoverage(interval: String): List<SymbolSyncEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCoverage(rows: List<SymbolSyncEntity>)

    @Query("SELECT * FROM sync_file WHERE interval = :interval LIMIT 1")
    suspend fun getSyncFile(interval: String): SyncFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSyncFile(entity: SyncFileEntity)
}

@Dao
interface VolumeSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<VolumeSnapshotEntity>)

    @Query("SELECT * FROM volume_snapshot WHERE avgVolume >= :minVolume ORDER BY avgVolume DESC")
    suspend fun getAboveThreshold(minVolume: Long): List<VolumeSnapshotEntity>

    @Query("SELECT * FROM volume_snapshot ORDER BY avgVolume DESC LIMIT :n")
    suspend fun getTopN(n: Int): List<VolumeSnapshotEntity>

    @Query("SELECT MAX(lastUpdatedEpochMillis) FROM volume_snapshot")
    suspend fun getLastUpdated(): Long?

    @Query("SELECT COUNT(*) FROM volume_snapshot")
    suspend fun count(): Int
}
