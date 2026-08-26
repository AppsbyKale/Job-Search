package com.example.jobsearch.ui.interview

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jobsearch.data.InterviewAnswer
import com.example.jobsearch.data.InterviewQuestion
import com.example.jobsearch.ui.components.AppDialog
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Dialog for practicing interview questions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterviewDialog(
    onDismiss: () -> Unit,
    viewModel: InterviewViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val count by viewModel.questionCount.collectAsStateWithLifecycle()

    var pendingQuestionId by remember { mutableStateOf<Long?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val questionId = pendingQuestionId
        pendingQuestionId = null
        if (granted && questionId != null) {
            viewModel.startRecording(questionId)
        } else if (!granted) {
            viewModel.showNotice("Microphone permission is required to record answers.")
        }
    }

    AppDialog(
        title = "Interview Practice",
        onDismissRequest = onDismiss,
        fullScreen = true,
        onSave = onDismiss,
        saveLabel = "Done"
    ) {
        if (state.loading || state.job == null) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Header(state)
            state.notice?.let {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(it, color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = viewModel::dismissNotice) { Text("Dismiss") }
                    }
                }
            }
            val generatingError = state.generatingError
            if (generatingError != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(generatingError, color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = viewModel::dismissNotice) { Text("Dismiss") }
                    }
                }
            }
            if (!state.modelReady) {
                Text(
                    "The AI model is not downloaded yet. Open Settings to download it, then return here to generate questions and evaluations.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            QuestionsSection(
                state = state,
                count = count,
                onCountChange = viewModel::setQuestionCount,
                onGenerate = viewModel::generateQuestions,
                onRecord = { id ->
                    pendingQuestionId = id
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                onStopRecording = viewModel::stopRecording,
                onSaveTyped = viewModel::saveTypedAnswer
            )
            EvaluateSection(
                state = state,
                onEvaluate = viewModel::evaluate
            )
            ManualQuestionSection(
                state = state,
                onAsk = viewModel::askManualQuestion,
                onClear = viewModel::clearManualAnswer
            )
        }
    }
}

@Composable
private fun Header(state: InterviewViewModel.UiState) {
    val job = state.job ?: return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(job.title.ifBlank { "Untitled job" }, style = MaterialTheme.typography.titleLarge)
        if (job.company.isNotBlank()) {
            Text(job.company, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "Answer the questions out loud, then generate an evaluation. Everything runs on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


@Composable
private fun QuestionsSection(
    state: InterviewViewModel.UiState,
    count: Int,
    onCountChange: (Int) -> Unit,
    onGenerate: () -> Unit,
    onRecord: (Long) -> Unit,
    onStopRecording: (Long) -> Unit,
    onSaveTyped: (Long, String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Questions", style = MaterialTheme.typography.titleMedium)

            when {
                state.generating != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(state.generating, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                state.questions.isEmpty() -> {
                    Text(
                        "The AI will write realistic questions based on this job and your resume.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf(3, 5).forEach { option ->
                            FilterChip(
                                selected = count == option,
                                onClick = { onCountChange(option) },
                                label = { Text("$option") }
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("questions", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = onGenerate,
                        enabled = state.modelReady && state.resumeLoaded
                    ) { Text("Generate questions") }
                }

                else -> {
                    state.questions.forEachIndexed { index, question ->
                        QuestionCard(
                            number = index + 1,
                            question = question,
                            answer = state.answers.firstOrNull { it.questionId == question.id },
                            recording = state.recordingQuestionId == question.id,
                            partialTranscript = state.partialTranscript,
                            transcribing = state.transcribingQuestionId == question.id,
                            onRecord = { onRecord(question.id) },
                            onStopRecording = { onStopRecording(question.id) },
                            onSaveTyped = { onSaveTyped(question.id, it) }
                        )
                    }
                    Text(
                        "Regenerating clears current answers and reports.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = onGenerate, enabled = state.modelReady && state.resumeLoaded) {
                        Text("Regenerate questions")
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionCard(
    number: Int,
    question: InterviewQuestion,
    answer: InterviewAnswer?,
    recording: Boolean,
    partialTranscript: String,
    transcribing: Boolean,
    onRecord: () -> Unit,
    onStopRecording: () -> Unit,
    onSaveTyped: (String) -> Unit
) {
    var editing by remember(question.id) { mutableStateOf(false) }
    var typing by remember(question.id) { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "$number. ${question.question}",
                style = MaterialTheme.typography.bodyLarge
            )
            HorizontalDivider()
            when {
                transcribing -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Transcribing your answer...", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                recording -> {
                    RecordingRow(partialTranscript = partialTranscript, onStop = onStopRecording)
                }

                editing -> {
                    EditAnswerSection(
                        initialText = answer?.text ?: "",
                        onSave = { onSaveTyped(it); editing = false },
                        onCancel = { editing = false }
                    )
                }

                typing -> {
                    TypeAnswerSection(
                        onSave = { onSaveTyped(it); typing = false },
                        onCancel = { typing = false }
                    )
                }

                else -> {
                    val hasAnswer = answer?.hasAnswer == true
                    if (hasAnswer) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Your answer:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                answer?.let { it.text } ?: "",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (answer?.score != null) {
                                Text(
                                    "Score: ${answer.score}/10",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(onClick = onRecord, modifier = Modifier.weight(1f)) {
                                    Text("Re-record")
                                }
                                OutlinedButton(onClick = { editing = true }, modifier = Modifier.weight(1f)) {
                                    Text("Edit")
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(onClick = onRecord, modifier = Modifier.weight(1f)) {
                                Text("Record answer")
                            }
                            OutlinedButton(onClick = { typing = true }, modifier = Modifier.weight(1f)) {
                                Text("Type answer")
                            }
                        }
                    }
                    if (answer?.feedback?.isNotBlank() == true) {
                        Spacer(Modifier.height(4.dp))
                        Text("Feedback", style = MaterialTheme.typography.titleSmall)
                        Text(
                            answer.feedback,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (answer.modelAnswer.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text("Model answer", style = MaterialTheme.typography.titleSmall)
                            Text(
                                answer.modelAnswer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingRow(partialTranscript: String, onStop: () -> Unit) {
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            elapsed++
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("Recording… ${formatDuration(elapsed)}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Button(onClick = onStop) { Text("Stop") }
        }
        if (partialTranscript.isNotBlank()) {
            Text(
                partialTranscript,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun EditAnswerSection(initialText: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var draft by remember { mutableStateOf(initialText) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Your answer:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text("Edit answer") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { onSave(draft) }, modifier = Modifier.weight(1f)) { Text("Save") }
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
        }
    }
}

@Composable
private fun TypeAnswerSection(onSave: (String) -> Unit, onCancel: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Your answer:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text("Type your answer") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { onSave(draft) }, modifier = Modifier.weight(1f)) { Text("Save") }
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
        }
    }
}

@Composable
private fun EvaluateSection(state: InterviewViewModel.UiState, onEvaluate: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Evaluation", style = MaterialTheme.typography.titleMedium)

            when {
                state.generating != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(state.generating, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                state.report != null -> {
                    Text(
                        "Overall report (${dateLabel(state.report.updatedAt)})",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(state.report.overall, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onEvaluate) { Text("Regenerate evaluation") }
                }

                else -> {
                    val answered = state.answers.count { it.hasAnswer }
                    Text(
                        if (answered == 0) {
                            "Answer at least one question, then evaluate for scores, feedback and an overall report."
                        } else {
                            "$answered question(s) answered. This can take a few minutes — you can leave the screen."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onEvaluate,
                        enabled = answered > 0 && state.generating == null
                    ) { Text("Generate evaluation") }
                }
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.US, "%d:%02d", m, s)
}


@Composable
private fun ManualQuestionSection(
    state: InterviewViewModel.UiState,
    onAsk: (String) -> Unit,
    onClear: () -> Unit
) {
    var question by remember { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Manual Question", style = MaterialTheme.typography.titleMedium)
            Text(
                "Have a specific question about this job or how to highlight a skill? Ask the AI directly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                label = { Text("Ask anything...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            if (state.manualLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp).padding(2.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("AI is thinking...", style = MaterialTheme.typography.bodyMedium)
                }
            } else if (state.manualAnswer != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("AI Answer", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(state.manualAnswer, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = onClear) { Text("Clear") }
                    }
                }
            }

            Button(
                onClick = { onAsk(question); question = "" },
                enabled = question.isNotBlank() && !state.manualLoading && state.modelReady,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ask Question")
            }
        }
    }
}

private fun dateLabel(millis: Long): String =
    java.text.SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(java.util.Date(millis))
