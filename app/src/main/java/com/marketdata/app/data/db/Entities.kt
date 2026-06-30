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
