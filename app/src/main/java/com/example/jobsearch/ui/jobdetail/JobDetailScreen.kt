package com.example.jobsearch.ui.jobdetail

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jobsearch.R
import com.example.jobsearch.data.Job
import com.example.jobsearch.data.JobStatus
import com.example.jobsearch.data.getDisplayPreview
import com.example.jobsearch.ui.components.AppCard
import com.example.jobsearch.ui.components.ErrorCard
import com.example.jobsearch.ui.components.SectionHeader
import com.example.jobsearch.ui.components.StatusBadge
import com.example.jobsearch.ui.interview.InterviewDialog
import com.example.jobsearch.util.DateFormatter

/**
 * Screen displaying detailed information about a single job, including document generation controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailScreen(
    onBack: () -> Unit,
    onViewDocument: (jobId: Long, type: String, edit: Boolean) -> Unit,
    generate: String = "",
    viewModel: JobDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val job = state.job
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var pendingPdfText by remember { mutableStateOf<String?>(null) }
    var pendingPdfType by remember { mutableStateOf<String?>(null) }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null && pendingPdfText != null) {
            val isCheat = pendingPdfType == "cheat"
            val ok = viewModel.exportPdf(uri, pendingPdfText!!, isCheat)
            Toast.makeText(context, if (ok) "Saved to file" else "Save failed", Toast.LENGTH_SHORT).show()
        }
        pendingPdfText = null
        pendingPdfType = null
    }

    LaunchedEffect(generate) {
        viewModel.onGenerateFromArgs(generate)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.job_details_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_button))
                    }
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.more_content_description))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Notes") },
                            onClick = {
                                menuExpanded = false
                                viewModel.showNotes(true)
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.generate_resume_label)) },
                            onClick = {
                                menuExpanded = false
                                viewModel.generateResume()
                            },
                            enabled = state.modelReady && state.resumeLoaded
                        )
                        DropdownMenuItem(
                            text = { Text("↳ " + stringResource(R.string.include_qa_answers_label)) },
                            onClick = {
                                viewModel.toggleQaAnswers(!state.useQaAnswers)
                            },
                            trailingIcon = {
                                if (state.useQaAnswers) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.generate_cover_letter_label)) },
                            onClick = {
                                menuExpanded = false
                                viewModel.generateCoverLetter()
                            },
                            enabled = state.modelReady && state.resumeLoaded
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.generate_both_label)) },
                            onClick = {
                                menuExpanded = false
                                viewModel.generateBoth()
                            },
                            enabled = state.modelReady && state.resumeLoaded
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.match_analysis_label)) },
                            onClick = {
                                menuExpanded = false
                                viewModel.showMatchAnalysis(true)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.supplemental_questions_label)) },
                            onClick = {
                                menuExpanded = false
                                viewModel.showSupplemental(true)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Initial Application Email") },
                            onClick = {
                                menuExpanded = false
                                viewModel.showInitialEmail(true)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.practice_interview_label)) },
                            onClick = {
                                menuExpanded = false
                                viewModel.showInterview(true)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Ask AI Question") },
                            onClick = {
                                menuExpanded = false
                                viewModel.showAskAiQuestion(true)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.interview_cheat_sheet_label)) },
                            onClick = {
                                menuExpanded = false
                                viewModel.showCheatSheet(true)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.follow_up_email_label)) },
                            onClick = {
                                menuExpanded = false
                                viewModel.showFollowUp(true)
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.loading || job == null) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                state.notice?.let { notice ->
                    ErrorCard(
                        title = stringResource(R.string.generation_failed_title),
                        error = notice,
                        onRetry = null,
                        onDismiss = viewModel::dismissNotice
                    )
                }
                state.generationError?.let { error ->
                    ErrorCard(
                        title = stringResource(R.string.generation_failed_title),
                        error = error,
                        onRetry = viewModel::retry,
                        onDismiss = viewModel::dismissNotice
                    )
                }

                JobHeader(
                    job = job,
                    matchResult = state.matchResult,
                    onShowMatch = { viewModel.showMatchAnalysis(true) },
                    onViewDescription = { viewModel.showJobDescription(true) }
                )
                
                LinkAndStatusSection(state, viewModel)

                if (!state.modelReady) {
                    Text(
                        stringResource(R.string.ai_model_not_downloaded_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                val isCheat = state.generationType == com.example.jobsearch.ai.GenerationRepository.Type.CHEAT_SHEET
                val isFollow = state.generationType == com.example.jobsearch.ai.GenerationRepository.Type.FOLLOW_UP
                if (state.generating != null && !isCheat && !isFollow) {
                    GenerationProgress(state, viewModel)
                }
                
                SectionHeader(stringResource(R.string.saved_documents_header))
                if (job.hasResume) {
                    DocumentItemCard(
                        title = stringResource(R.string.resume_label),
                        text = getDisplayPreview(job.resumeText, isResume = true),
                        onView = { onViewDocument(job.id, "resume", false) },
                        onEdit = { onViewDocument(job.id, "resume", true) },
                        onDelete = { viewModel.deleteDocument("resume") }
                    )
                }
                if (job.hasCoverLetter) {
                    DocumentItemCard(
                        title = stringResource(R.string.cover_letter_label),
                        text = getDisplayPreview(job.coverLetterText, isResume = false),
                        onView = { onViewDocument(job.id, "cover", false) },
                        onEdit = { onViewDocument(job.id, "cover", true) },
                        onDelete = { viewModel.deleteDocument("cover") }
                    )
                }
                if (job.hasCheatSheet) {
                    DocumentItemCard(
                        title = stringResource(R.string.interview_cheat_sheet_label),
                        text = getDisplayPreview(job.cheatSheetText, isResume = false),
                        onView = { onViewDocument(job.id, "cheat", false) },
                        onEdit = { onViewDocument(job.id, "cheat", true) },
                        onDelete = { viewModel.deleteDocument("cheat") }
                    )
                }
                if (job.hasFollowUpEmail) {
                    DocumentItemCard(
                        title = stringResource(R.string.follow_up_email_label),
                        text = getDisplayPreview(job.followUpEmailText, isResume = false),
                        onView = { onViewDocument(job.id, "followup", false) },
                        onEdit = { onViewDocument(job.id, "followup", true) },
                        onDelete = { viewModel.deleteDocument("followup") }
                    )
                }
                if (job.hasInitialEmail) {
                    DocumentItemCard(
                        title = "Initial Application Email",
                        text = getDisplayPreview(job.initialEmailText, isResume = false),
                        onView = { onViewDocument(job.id, "initial", false) },
                        onEdit = { onViewDocument(job.id, "initial", true) },
                        onDelete = { viewModel.deleteDocument("initial") }
                    )
                }
                if (job.hasNotes) {
                    DocumentItemCard(
                        title = "Research Notes",
                        text = job.notes,
                        onView = { viewModel.showNotes(true) },
                        onEdit = { viewModel.showNotes(true) },
                        onDelete = { viewModel.updateNotes("") }
                    )
                }
            }
        }
    }

    if (state.showSupplementalDialog) {
        SupplementalQuestionsDialog(
            questions = state.questions,
            answers = state.answers,
            running = state.questionsRunning,
            onGenerate = viewModel::generateQuestions,
            onAnswer = viewModel::setAnswer,
            onDismiss = { viewModel.showSupplemental(false) }
        )
    }

    if (state.showInitialEmailDialog) {
        InitialEmailDialog(
            job = job,
            running = state.generationType == com.example.jobsearch.ai.GenerationRepository.Type.INITIAL_EMAIL,
            progress = state.generationProgress,
            progressText = state.generationProgressText,
            onGenerate = viewModel::generateInitialEmail,
            onCopy = { text -> clipboardManager.setText(AnnotatedString(text)) },
            onOpenGmail = { text ->
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:")
                    putExtra(Intent.EXTRA_SUBJECT, "Application: ${job?.title} at ${job?.company}")
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(intent, "Open in Gmail"))
            },
            onDismiss = { viewModel.showInitialEmail(false) }
        )
    }

    if (state.showCheatSheetDialog) {
        CheatSheetDialog(
            job = job,
            running = state.generationType == com.example.jobsearch.ai.GenerationRepository.Type.CHEAT_SHEET,
            progress = state.generationProgress,
            progressText = state.generationProgressText,
            onGenerate = viewModel::generateCheatSheet,
            onCopy = { text -> clipboardManager.setText(AnnotatedString(text)) },
            onExportPdf = { text ->
                pendingPdfText = text
                pendingPdfType = "cheat"
                pdfLauncher.launch("CheatSheet_${job?.company ?: "Job"}.pdf")
            },
            onDismiss = { viewModel.showCheatSheet(false) }
        )
    }

    if (state.showFollowUpDialog) {
        FollowUpDialog(
            job = job,
            running = state.generationType == com.example.jobsearch.ai.GenerationRepository.Type.FOLLOW_UP,
            progress = state.generationProgress,
            progressText = state.generationProgressText,
            onGenerate = viewModel::generateFollowUpEmail,
            onCopy = { text -> clipboardManager.setText(AnnotatedString(text)) },
            onOpenGmail = { text ->
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:")
                    putExtra(Intent.EXTRA_SUBJECT, "Follow-up: ${job?.title} at ${job?.company}")
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(intent, "Open in Gmail"))
            },
            onDismiss = { viewModel.showFollowUp(false) }
        )
    }

    if (state.showMatchAnalysisDialog) {
        MatchAnalysisDialog(
            matchResult = state.matchResult,
            matchRunning = state.matchRunning,
            onAnalyze = viewModel::checkMatch,
            onGenerateSupplemental = viewModel::generateQuestions,
            onDismiss = { viewModel.showMatchAnalysis(false) }
        )
    }

    if (state.showJobDescriptionDialog && job != null) {
        JobDescriptionDialog(
            description = job.description,
            onDismiss = { viewModel.showJobDescription(false) }
        )
    }

    if (state.showInterviewDialog) {
        InterviewDialog(
            onDismiss = { viewModel.showInterview(false) }
        )
    }

    if (state.showAskAiQuestionDialog) {
        AskAiQuestionDialog(
            onAsk = viewModel::askAiQuestion,
            onDismiss = { viewModel.showAskAiQuestion(false) }
        )
    }

    if (state.showNotesDialog) {
        NotesDialog(
            initialNotes = job?.notes ?: "",
            onSave = viewModel::updateNotes,
            onDismiss = { viewModel.showNotes(false) }
        )
    }

    if (state.showResumeSteeringDialog) {
        ResumeSteeringDialog(
            onConfirm = viewModel::confirmSteering,
            onDismiss = viewModel::dismissSteering
        )
    }
}

@Composable
fun ResumeSteeringDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resume Steering") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Optional: Add specific instructions to guide the AI tailoring (e.g., 'Highlight my project management experience' or 'Focus on Python skills').",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Instructions") },
                    placeholder = { Text("Leave blank for standard tailoring") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }) {
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

@Composable
private fun JobHeader(
    job: Job,
    matchResult: JobDetailViewModel.MatchResult?,
    onShowMatch: () -> Unit,
    onViewDescription: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                job.title.ifBlank { stringResource(R.string.untitled_job) },
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            val matchText = matchResult?.let { "${it.score}%" } ?: ""
            if (matchText.isNotBlank()) {
                Text(
                    text = matchText,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clickable { onShowMatch() }
                )
            }
        }
        if (job.company.isNotBlank()) {
            Text(job.company, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.added_label, DateFormatter.formatDate(job.dateAdded)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = onViewDescription,
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    stringResource(R.string.view_job_description),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun LinkAndStatusSection(state: JobDetailViewModel.UiState, viewModel: JobDetailViewModel) {
    val job = state.job ?: return
    var urlMenuExpanded by remember { mutableStateOf(false) }
    var showWebViewDialog by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (job.url.isNotBlank()) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    job.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { urlMenuExpanded = true }
                        .padding(vertical = 4.dp)
                )
                DropdownMenu(
                    expanded = urlMenuExpanded,
                    onDismissRequest = { urlMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.open_in_app)) },
                        onClick = {
                            urlMenuExpanded = false
                            showWebViewDialog = true
                        },
                        leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.open_in_browser)) },
                        onClick = {
                            urlMenuExpanded = false
                            uriHandler.openUri(job.url)
                        },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.copy_link)) },
                        onClick = {
                            urlMenuExpanded = false
                            clipboardManager.setText(AnnotatedString(job.url))
                        },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                    )
                }
            }
        }
        StatusRow(state, viewModel)
    }

    if (showWebViewDialog) {
        JobUrlWebViewDialog(
            url = job.url,
            onDismiss = { showWebViewDialog = false }
        )
    }
}

@Composable
private fun StatusRow(state: JobDetailViewModel.UiState, viewModel: JobDetailViewModel) {
    val job = state.job ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusBadge(
            status = JobStatus.fromName(job.status),
            onSelect = viewModel::setStatus
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = state.statusHint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GenerationProgress(state: JobDetailViewModel.UiState, viewModel: JobDetailViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.generationIndeterminate) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(
                progress = { state.generationProgress },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.generationProgressText ?: stringResource(R.string.generating_label),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = viewModel::cancelGeneration) {
                Text(stringResource(R.string.cancel_button))
            }
        }
    }
}

@Composable
private fun DocumentItemCard(
    title: String,
    text: String,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    AppCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand"
            )
        }
        
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 10,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick = onView) { Text(stringResource(R.string.view_button)) }
                TextButton(onClick = onEdit) { Text(stringResource(R.string.edit_button)) }
                TextButton(onClick = { showDeleteConfirm = true }) { Text(stringResource(R.string.delete_button)) }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_document_confirm_title, title)) },
            text = { Text(stringResource(R.string.delete_document_confirm_message, title)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) { Text(stringResource(R.string.delete_button)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel_button)) }
            }
        )
    }
}
