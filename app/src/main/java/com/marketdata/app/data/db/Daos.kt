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
}
