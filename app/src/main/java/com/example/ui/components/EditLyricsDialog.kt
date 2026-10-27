package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AudioFileImporter
import com.example.data.Song
import com.example.ui.theme.AppleMusicRed
import kotlinx.coroutines.launch

@Composable
fun EditLyricsDialog(
    song: Song,
    onDismiss: () -> Unit,
    onSave: (songId: Long, lyrics: String?) -> Unit
) {
    var lyricsText by remember { mutableStateOf(song.lyrics ?: "") }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val importer = remember { AudioFileImporter(context) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val content = importer.readLyricsFromUri(uri)
                if (content != null) {
                    lyricsText = content
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit / Import Lyrics", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Supports synchronized LRC format ([00:12.30] lyrics) or plain text.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        filePicker.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import .LRC or .TXT File")
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = lyricsText,
                    onValueChange = { lyricsText = it },
                    label = { Text("Lyrics Content") },
                    placeholder = { Text("[00:04.50]Line 1\n[00:09.20]Line 2") },
                    minLines = 8,
                    maxLines = 14,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(song.id, lyricsText.trim().ifEmpty { null })
                },
                colors = ButtonDefaults.buttonColors(containerColor = AppleMusicRed)
            ) {
                Text("Save Lyrics")
            }
        },
        dismissButton = {
            Row {
                if (!song.lyrics.isNullOrBlank()) {
                    TextButton(
                        onClick = { onSave(song.id, null) },
                        colors = ButtonDefaults.textButtonColors(contentColor = AppleMusicRed)
                    ) {
                        Text("Remove")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
