package com.example.jobsearch.ui.interview

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Screen for practicing interview questions with voice recording and AI feedback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterviewScreen(
    onBack: () -> Unit,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Interview practice") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
            }
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

                else -> {
                    val hasAnswer = answer?.hasAnswer == true
                    if (hasAnswer) {
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onRecord) { Text("Re-record") }
                            EditAnswerToggle(questionId = question.id, text = answer?.text ?: "", onSave = onSaveTyped)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onRecord) { Text("Record answer") }
                            TypeAnswerToggle(questionId = question.id, onSave = onSaveTyped)
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
private fun EditAnswerToggle(questionId: Long, text: String, onSave: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(questionId) { mutableStateOf(text) }
    if (!editing) {
        OutlinedButton(onClick = { editing = true }) { Text("Edit") }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Edit answer") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSave(draft); editing = false }) { Text("Save") }
                TextButton(onClick = { editing = false; draft = text }) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun TypeAnswerToggle(questionId: Long, onSave: (String) -> Unit) {
    var typing by remember { mutableStateOf(false) }
    var draft by remember(questionId) { mutableStateOf("") }
    if (!typing) {
        OutlinedButton(onClick = { typing = true }) { Text("Type answer") }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Type your answer") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSave(draft); typing = false }) { Text("Save") }
                TextButton(onClick = { typing = false; draft = "" }) { Text("Cancel") }
            }
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


private fun dateLabel(millis: Long): String =
    java.text.SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(java.util.Date(millis))
