package com.marketdata.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marketdata.app.data.db.DownloadedFileEntity
import com.marketdata.app.ui.theme.*
import com.marketdata.app.viewmodel.FilesViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FilesScreen(viewModel: FilesViewModel) {
    val files by viewModel.files.collectAsState()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().background(DarkBackground).padding(16.dp)) {
        Text("DOWNLOADED FILES", style = MaterialTheme.typography.headlineMedium, color = AccentBlue)
        Spacer(Modifier.height(4.dp))
        Text("${files.size} files", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))

        if (files.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("No files downloaded yet", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(files) { file ->
                    FileCard(file, onOpen = {
                        try {
                            val uri = android.net.Uri.parse(file.filePath)
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "text/csv")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Cannot open: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }, onShare = {
                        try {
                            val uri = android.net.Uri.parse(file.filePath)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share CSV"))
                        } catch (e: Exception) {}
                    }, onDelete = { viewModel.deleteFile(file) })
                }
            }
        }
    }
}

@Composable
fun FileCard(
    file: DownloadedFileEntity,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = DarkCard), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(file.fileName, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("${file.interval} • ${file.fromDate} to ${file.toDate}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Text("${file.rowCount} rows", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Text(
                SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date(file.downloadedAt)),
                color = TextMuted, style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpen, colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Open", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = onShare, colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen)) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = onDelete, colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed)) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
