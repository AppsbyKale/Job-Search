package com.example.jobsearch.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.unit.dp
import com.example.jobsearch.ui.components.AppDialog

@Composable
fun EditResumeDialog(
    initialText: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialText) }

    AppDialog(
        title = "Edit Master Resume",
        onDismissRequest = onDismiss,
        fullScreen = true,
        actions = {
            TextButton(
                onClick = {
                    onSave(text)
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        }
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Resume text") },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 400.dp)
        )
    }
}
