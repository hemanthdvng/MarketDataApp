package com.marketdata.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.marketdata.app.data.db.AppDatabase
import com.marketdata.app.data.db.DownloadedFileEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FilesViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)

    val files = db.downloadedFileDao().getAllFiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteFile(file: DownloadedFileEntity) {
        viewModelScope.launch {
            db.downloadedFileDao().deleteById(file.id)
        }
    }
}
