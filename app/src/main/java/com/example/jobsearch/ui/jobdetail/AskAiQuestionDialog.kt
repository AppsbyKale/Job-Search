package com.example.jobsearch.ui.jobdetail

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jobsearch.ui.components.AppDialog

@Composable
fun AskAiQuestionDialog(
    onAsk: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var question by remember { mutableStateOf("") }

    AppDialog(
        title = "Ask AI Interview Question",
        onDismissRequest = onDismiss,
        fullScreen = true,
        onSave = {
            if (question.isNotBlank()) {
                onAsk(question)
                onDismiss()
            }
        },
        saveLabel = "Get Answer"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Type a specific interview question you're worried about. The AI will generate a strategy and example answer tailored to your resume.",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                label = { Text("Your Question") },
                placeholder = { Text("e.g. How do you handle conflict in the workplace?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        }
    }
}
