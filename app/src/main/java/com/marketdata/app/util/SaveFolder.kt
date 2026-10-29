package com.marketdata.app.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.marketdata.app.data.prefs.SecurePrefs

/**
 * Where CSV files are saved.
 *
 * Default (Android 10+): the public Downloads folder, in a "MarketData"
 * sub-folder (Download/MarketData). Written through MediaStore, so no folder
 * picker and no storage permission are needed.
 *
 * Optional: the user can pick any folder with the system folder picker. That
 * choice is remembered across app restarts.
 *
 * Android 9 and older cannot use the MediaStore route, so there the user is
 * asked to pick a folder (current() returns null and callers launch the picker).
 */
object SaveFolder {

    const val DEFAULT_LABEL = "Download/MarketData"

    /** Marker URI meaning "use Download/MarketData". It is never opened directly. */
    val DEFAULT_URI: Uri = Uri.parse("marketdata://downloads/MarketData")

    fun isDefault(uri: Uri?): Boolean = uri == DEFAULT_URI

    /** The folder the user picked earlier, if we still hold permission for it. */
    private fun saved(context: Context): Uri? {
        val raw = SecurePrefs.getInstance(context).saveFolderUri
        if (raw.isBlank()) return null
        val uri = Uri.parse(raw)
        val stillGranted = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isWritePermission
        }
        return if (stillGranted) uri else null
    }

    /** Folder to save into right now, or null if the user must pick one first. */
    fun current(context: Context): Uri? =
        saved(context) ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) DEFAULT_URI else null

    /** Remember a folder the user picked. */
    fun remember(context: Context, uri: Uri) {
        SecurePrefs.getInstance(context).saveFolderUri = uri.toString()
    }

    /** Go back to Download/MarketData. */
    fun resetToDefault(context: Context) {
        SecurePrefs.getInstance(context).saveFolderUri = ""
    }

    fun label(uri: Uri?): String = when {
        uri == null -> "Not selected"
        isDefault(uri) -> DEFAULT_LABEL
        else -> uri.lastPathSegment ?: "Selected folder"
    }

    /**
     * Creates a new, empty CSV file and returns its Uri (or null on failure).
     * [folderUri] is either a picked folder tree, or [DEFAULT_URI].
     */
    fun createCsv(context: Context, folderUri: Uri, fileName: String): Uri? {
        if (!isDefault(folderUri)) {
            return DocumentFile.fromTreeUri(context, folderUri)
                ?.createFile("text/csv", fileName)
                ?.uri
        }
        // Lint-friendly guard: the MediaStore.Downloads API exists from Android 10.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/MarketData")
        }
        return context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    }
}
