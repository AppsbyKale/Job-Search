package com.example.jobsearch.ui.jobdetail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.jobsearch.ai.IModelManager
import com.example.jobsearch.ai.PromptBuilder
import com.example.jobsearch.data.Job
import com.example.jobsearch.data.SettingsRepository
import com.example.jobsearch.ui.components.AppDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun AskAiQuestionDialog(
    job: Job,
    settingsRepository: SettingsRepository,
    modelManager: IModelManager,
    onDismiss: () -> Unit
) {
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    AppDialog(
        title = "Ask AI Interview Question",
        onDismissRequest = onDismiss,
        fullScreen = true,
        onSave = onDismiss,
        saveLabel = "Done",
        footer = {
            if (answer != null) {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("AI Interview Answer", answer))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Copy Response")
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Type a specific interview question you're worried about. The AI will generate a strategy and example answer tailored to your resume right here.",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                label = { Text("Your Question") },
                placeholder = { Text("e.g. How do you handle conflict in the workplace?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                enabled = !isGenerating
            )

            if (!isGenerating && answer == null) {
                Button(
                    onClick = {
                        if (question.isNotBlank()) {
                            isGenerating = true
                            scope.launch {
                                try {
                                    val resume = settingsRepository.resumeText.first()
                                    val prompt = PromptBuilder.manualQuestionPrompt(job, resume, question)
                                    val result = modelManager.generate(prompt, source = "Ask AI Question").trim()
                                    answer = result
                                } catch (e: Exception) {
                                    answer = "Failed to generate answer: ${e.message}"
                                } finally {
                                    isGenerating = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = question.isNotBlank()
                ) {
                    Text("Generate Answer")
                }
            }

            if (isGenerating) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("AI is thinking...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            answer?.let { ans ->
                Spacer(Modifier.height(8.dp))
                Text("AI Response:", style = MaterialTheme.typography.titleSmall)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = ans,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}
