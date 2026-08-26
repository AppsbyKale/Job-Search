package com.example.jobsearch.ui.jobdetail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.jobsearch.ui.components.AppDialog

@Composable
fun NotesDialog(
    initialNotes: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialNotes) }

    AppDialog(
        title = "Job Notes",
        onDismissRequest = onDismiss,
        fullScreen = true,
        onSave = {
            onSave(text)
            onDismiss()
        }
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxSize(),
            label = { Text("Notes from research") },
            placeholder = { Text("Enter notes here...") }
        )
    }
}
