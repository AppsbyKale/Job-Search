package com.example.jobsearch.ui.jobdetail.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jobsearch.ai.GenerationRepository

@Composable
fun ResumeSteeringDialog(
    targetType: GenerationRepository.Type?,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var resumeText by remember { mutableStateOf("") }
    var coverText by remember { mutableStateOf("") }

    val showResumeField = targetType == GenerationRepository.Type.RESUME || targetType == GenerationRepository.Type.BOTH || targetType == null
    val showCoverField = targetType == GenerationRepository.Type.COVER || targetType == GenerationRepository.Type.BOTH || targetType == null

    val titleText = when (targetType) {
        GenerationRepository.Type.RESUME -> "Resume Steering"
        GenerationRepository.Type.COVER -> "Cover Letter Steering"
        else -> "Document Steering"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Optional: Add specific instructions to guide the AI tailoring.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (showResumeField) {
                    OutlinedTextField(
                        value = resumeText,
                        onValueChange = { resumeText = it },
                        label = { Text("Resume Steering Instructions") },
                        placeholder = { Text("e.g. 'Highlight project management'") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
                if (showCoverField) {
                    OutlinedTextField(
                        value = coverText,
                        onValueChange = { coverText = it },
                        label = { Text("Cover Letter Steering Instructions") },
                        placeholder = { Text("e.g. 'Focus on culture fit and passion'") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(resumeText, coverText) }) {
                Text("Generate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
