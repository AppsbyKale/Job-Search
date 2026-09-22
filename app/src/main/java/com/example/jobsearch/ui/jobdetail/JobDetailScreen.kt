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
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jobsearch.R
import com.example.jobsearch.ai.GenerationRepository
import com.example.jobsearch.data.Job
import com.example.jobsearch.data.JobStatus
import com.example.jobsearch.data.getDisplayPreview
import com.example.jobsearch.ui.components.AppCard
import com.example.jobsearch.ui.components.ErrorCard
import com.example.jobsearch.ui.components.SectionHeader
import com.example.jobsearch.ui.components.StatusBadge
import com.example.jobsearch.ui.interview.InterviewDialog
import com.example.jobsearch.ui.jobdetail.dialogs.ResumeSteeringDialog
import com.example.jobsearch.ui.jobdetail.components.CompanyInfoDialog
import com.example.jobsearch.ui.jobdetail.components.CheatSheetOptionsDialog
import com.example.jobsearch.util.DateFormatter

/**
 * Screen displaying detailed information about a single job, including document generation controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailScreen(
    onBack: () -> Unit,
    onViewDocument: (jobId: Long, type: String, edit: Boolean) -> Unit,
    onEditJob: (jobId: Long) -> Unit = {},
    generate: String = "",
    viewModel: JobDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val job = state.job
    var menuExpanded by remember { mutableStateOf(false) }
    var showTagEditor by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val savedMsg = stringResource(R.string.saved_to_file_message)
    val failedMsg = stringResource(R.string.save_failed_message)
    var exportTargetText by remember { mutableStateOf("") }
    var exportIsCheat by remember { mutableStateOf(false) }

    val pdfExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            val ok = viewModel.exportPdf(uri, exportTargetText, exportIsCheat)
            Toast.makeText(context, if (ok) savedMsg else failedMsg, Toast.LENGTH_SHORT).show()
        }
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
                        if (job != null) {
                            DropdownMenuItem(
                                text = { Text("Edit Job") },
                                onClick = {
                                    menuExpanded = false
                                    onEditJob(job.id)
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                            )
                            HorizontalDivider()
                        }
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
                            text = { Text(stringResource(R.string.interview_cheat_sheet_label)) },
                            onClick = {
                                menuExpanded = false
                                if (job?.hasCheatSheet == true) {
                                    onViewDocument(job.id, "cheat", false)
                                } else {
                                    viewModel.generateCheatSheet()
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.follow_up_email_label)) },
                            onClick = {
                                menuExpanded = false
                                viewModel.showFollowUp(true)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Post-Interview Thank You Email") },
                            onClick = {
                                menuExpanded = false
                                viewModel.showThankYou(true)
                            },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Upload External Doc") },
                            onClick = {
                                menuExpanded = false
                                viewModel.showExternalUpload(true)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Ask AI Question") },
                            onClick = {
                                menuExpanded = false
                                viewModel.showAskAiQuestion(true)
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
                    onViewDescription = { viewModel.showJobDescription(true) },
                    onShowCompanyInfo = { viewModel.showCompanyInfo(true) }
                )

                if (job.tags.isNotBlank()) {
                    OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().clickable { showTagEditor = true },
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        maxLines = 2
                    ) {
                        job.tagList.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    text = tag,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                } else {
                    TextButton(onClick = { showTagEditor = true }) {
                        Text("+ Add Tags", style = MaterialTheme.typography.labelSmall)
                    }
                }
                
                LinkAndStatusSection(state, viewModel)

                if (!state.modelReady) {
                    Text(
                        stringResource(R.string.ai_model_not_downloaded_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                val isFollow = state.generationType == GenerationRepository.Type.FOLLOW_UP
                if (state.generating != null && !isFollow) {
                    GenerationProgress(state, viewModel)
                }
                
                SectionHeader(stringResource(R.string.saved_documents_header))
                if (job.hasResume) {
                    DocumentItemCard(
                        title = stringResource(R.string.resume_label),
                        text = getDisplayPreview(job.resumeText, isResume = true),
                        onView = { onViewDocument(job.id, "resume", false) },
                        onEdit = { onViewDocument(job.id, "resume", true) },
                        onExport = { exportTargetText = job.resumeText; exportIsCheat = false; pdfExportLauncher.launch("Resume.pdf") },
                        onDelete = { viewModel.deleteDocument("resume") }
                    )
                }
                if (job.hasCoverLetter) {
                    DocumentItemCard(
                        title = stringResource(R.string.cover_letter_label),
                        text = getDisplayPreview(job.coverLetterText, isResume = false),
                        onView = { onViewDocument(job.id, "cover", false) },
                        onEdit = { onViewDocument(job.id, "cover", true) },
                        onExport = { exportTargetText = job.coverLetterText; exportIsCheat = false; pdfExportLauncher.launch("CoverLetter.pdf") },
                        onDelete = { viewModel.deleteDocument("cover") }
                    )
                }
                if (job.hasCheatSheet) {
                    DocumentItemCard(
                        title = stringResource(R.string.interview_cheat_sheet_label),
                        text = getDisplayPreview(job.cheatSheetText, isResume = false),
                        onView = { onViewDocument(job.id, "cheat", false) },
                        onEdit = { onViewDocument(job.id, "cheat", true) },
                        onExport = { exportTargetText = job.cheatSheetText; exportIsCheat = true; pdfExportLauncher.launch("CheatSheet.pdf") },
                        onDelete = { viewModel.deleteDocument("cheat") }
                    )
                }
                if (job.hasFollowUpEmail) {
                    DocumentItemCard(
                        title = stringResource(R.string.follow_up_email_label),
                        text = getDisplayPreview(job.followUpEmailText, isResume = false),
                        onView = { onViewDocument(job.id, "followup", false) },
                        onEdit = { onViewDocument(job.id, "followup", true) },
                        onExport = { exportTargetText = job.followUpEmailText; exportIsCheat = false; pdfExportLauncher.launch("FollowUpEmail.pdf") },
                        onDelete = { viewModel.deleteDocument("followup") }
                    )
                }
                if (job.hasThankYouEmail) {
                    DocumentItemCard(
                        title = "Post-Interview Thank You Email",
                        text = getDisplayPreview(job.thankYouEmailText, isResume = false),
                        onView = { onViewDocument(job.id, "thankyou", false) },
                        onEdit = { onViewDocument(job.id, "thankyou", true) },
                        onExport = { exportTargetText = job.thankYouEmailText; exportIsCheat = false; pdfExportLauncher.launch("ThankYouEmail.pdf") },
                        onDelete = { viewModel.deleteDocument("thankyou") }
                    )
                }
                if (job.hasInitialEmail) {
                    DocumentItemCard(
                        title = "Initial Application Email",
                        text = getDisplayPreview(job.initialEmailText, isResume = false),
                        onView = { onViewDocument(job.id, "initial", false) },
                        onEdit = { onViewDocument(job.id, "initial", true) },
                        onExport = { exportTargetText = job.initialEmailText; exportIsCheat = false; pdfExportLauncher.launch("InitialEmail.pdf") },
                        onDelete = { viewModel.deleteDocument("initial") }
                    )
                }
                if (job.hasNotes) {
                    DocumentItemCard(
                        title = "Research Notes",
                        text = job.notes,
                        onView = { viewModel.showNotes(true) },
                        onEdit = { viewModel.showNotes(true) },
                        onExport = { exportTargetText = job.notes; exportIsCheat = false; pdfExportLauncher.launch("ResearchNotes.pdf") },
                        onDelete = { viewModel.updateNotes("") }
                    )
                }

                if (job.hasExternalResume || job.hasExternalCoverLetter) {
                    SectionHeader("External Documents")
                    if (job.hasExternalResume) {
                        DocumentItemCard(
                            title = "External Resume",
                            text = getDisplayPreview(job.externalResumeText, isResume = true),
                            onView = { onViewDocument(job.id, "external_resume", false) },
                            onEdit = { onViewDocument(job.id, "external_resume", true) },
                            onExport = { exportTargetText = job.externalResumeText; exportIsCheat = false; pdfExportLauncher.launch("ExternalResume.pdf") },
                            onDelete = { viewModel.saveExternalDocument("resume", "") }
                        )
                    }
                    if (job.hasExternalCoverLetter) {
                        DocumentItemCard(
                            title = "External Cover Letter",
                            text = getDisplayPreview(job.externalCoverLetterText, isResume = false),
                            onView = { onViewDocument(job.id, "external_cover", false) },
                            onEdit = { onViewDocument(job.id, "external_cover", true) },
                            onExport = { exportTargetText = job.externalCoverLetterText; exportIsCheat = false; pdfExportLauncher.launch("ExternalCoverLetter.pdf") },
                            onDelete = { viewModel.saveExternalDocument("cover", "") }
                        )
                    }
                }

                SectionHeader("Timeline & Scheduling")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Date Added: ${DateFormatter.formatDate(job.dateAdded)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        job.dateApplied?.let {
                            Text(
                                text = "Date Applied: ${DateFormatter.formatDate(it)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        job.dateFollowedUp?.let {
                            Text(
                                text = "Last Followed Up: ${DateFormatter.formatDate(it)} (Count: ${job.followupCount})",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        var showScheduleDialog by remember { mutableStateOf(false) }
                        val globalIntervals by viewModel.settingsRepository.followupIntervals.collectAsStateWithLifecycle(initialValue = "7, 14, 30")

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Follow-up Schedule",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = if (job.customFollowupIntervals.isNullOrBlank())
                                        "Using global schedule ($globalIntervals days)"
                                    else
                                        "Custom schedule (${job.customFollowupIntervals} days)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { showScheduleDialog = true }) {
                                Text("Customize")
                            }
                        }

                        if (showScheduleDialog) {
                            var customText by remember { mutableStateOf(job.customFollowupIntervals ?: "") }
                            AlertDialog(
                                onDismissRequest = { showScheduleDialog = false },
                                title = { Text("Custom Follow-up Intervals") },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Enter comma-separated days (e.g., 3, 7, 14). Leave blank to use global settings.")
                                        OutlinedTextField(
                                            value = customText,
                                            onValueChange = { customText = it },
                                            label = { Text("Intervals (days)") },
                                            singleLine = true
                                        )
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        viewModel.updateCustomFollowupIntervals(customText)
                                        showScheduleDialog = false
                                    }) {
                                        Text("Save")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showScheduleDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }
                    }
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
            onCopy = { text -> scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, text))) } },
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



    if (state.showFollowUpDialog) {
        FollowUpDialog(
            job = job,
            running = state.generationType == com.example.jobsearch.ai.GenerationRepository.Type.FOLLOW_UP,
            progress = state.generationProgress,
            progressText = state.generationProgressText,
            onGenerate = viewModel::generateFollowUpEmail,
            onCopy = { text -> scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, text))) } },
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

    if (state.showThankYouDialog) {
        ThankYouDialog(
            job = job,
            running = state.generationType == GenerationRepository.Type.THANK_YOU,
            onGenerate = { notes ->
                viewModel.generateThankYouEmail(notes)
            },
            onDismiss = { viewModel.showThankYou(false) }
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

    if (state.showCompanyInfoDialog && job != null) {
        CompanyInfoDialog(
            companyName = job.company,
            companyInfo = job.companyInfo,
            isSearching = state.notice?.contains("Searching", ignoreCase = true) == true,
            onFetchCompany = viewModel::fetchCompanyInfo,
            onDismiss = { viewModel.showCompanyInfo(false) }
        )
    }

    if (state.showCheatSheetOptionsDialog) {
        CheatSheetOptionsDialog(
            onGenerate = { overview, challenges, dayToDay, highlights, customQ, strengths, weaknesses ->
                viewModel.generateCheatSheet(
                    includeOverview = overview,
                    includeChallenges = challenges,
                    includeDayToDay = dayToDay,
                    includeHighlights = highlights,
                    customQuestions = customQ,
                    strengths = strengths,
                    weaknesses = weaknesses
                )
            },
            onDismiss = { viewModel.showCheatSheetOptions(false) }
        )
    }

    if (state.showInterviewDialog) {
        InterviewDialog(
            onDismiss = { viewModel.showInterview(false) }
        )
    }

    if (state.showAskAiQuestionDialog) {
        AskAiQuestionDialog(
            job = job ?: Job(title = "", company = ""),
            settingsRepository = viewModel.settingsRepository,
            modelManager = viewModel.modelManager,
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

    if (state.showExternalUploadDialog) {
        ExternalUploadDialog(
            onUpload = viewModel::saveExternalDocument,
            onDismiss = { viewModel.showExternalUpload(false) }
        )
    }

    if (state.showResumeSteeringDialog) {
        ResumeSteeringDialog(
            targetType = state.steeringTargetType,
            onConfirm = viewModel::confirmSteering,
            onDismiss = viewModel::dismissSteering
        )
    }

    if (showTagEditor) {
        TagEditorDialog(
            initialTags = job?.tags ?: "",
            onSave = {
                viewModel.updateTags(it)
                showTagEditor = false
            },
            onDismiss = { showTagEditor = false }
        )
    }
}

@Composable
fun TagEditorDialog(
    initialTags: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialTags) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Tags") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter tags separated by commas.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Tags") },
                    placeholder = { Text("Android, AI, Remote") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(text) }) {
                Text("Save")
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
fun ExternalUploadDialog(
    onUpload: (type: String, text: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedType by remember { mutableStateOf("resume") }
    var uploading by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            uploading = true
            scope.launch {
                try {
                    val resolver = context.contentResolver
                    val fileName = resolver.query(uri, null, null, null, null)?.use { cursor ->
                        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index != -1 && cursor.moveToFirst()) cursor.getString(index) else "document"
                    } ?: "document"
                    
                    val text = resolver.openInputStream(uri)?.use { stream ->
                        com.example.jobsearch.resume.ResumeImporter().parse(context, fileName, stream)
                    } ?: ""
                    
                    if (text.isNotBlank()) {
                        onUpload(selectedType, text)
                        Toast.makeText(context, "Uploaded $selectedType", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    uploading = false
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Upload External Document") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Select the type of document you want to attach to this job tracking record.")
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedType == "resume", onClick = { selectedType = "resume" })
                    Text("Resume", modifier = Modifier.clickable { selectedType = "resume" })
                    Spacer(Modifier.width(16.dp))
                    RadioButton(selected = selectedType == "cover", onClick = { selectedType = "cover" })
                    Text("Cover Letter", modifier = Modifier.clickable { selectedType = "cover" })
                }

                if (uploading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    picker.launch(arrayOf("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/plain"))
                },
                enabled = !uploading
            ) {
                Text("Choose File")
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
    onViewDescription: () -> Unit,
    onShowCompanyInfo: () -> Unit
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
            Text(
                job.company, 
                style = MaterialTheme.typography.titleMedium, 
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.clickable { onShowCompanyInfo() }
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

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
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, job.url)))
                            }
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(
                status = JobStatus.fromName(job.status),
                onSelectWithDate = viewModel::setStatusWithDate
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = state.statusHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (job.status == JobStatus.APPLIED.name && job.dateApplied != null) {
            Text(
                text = "Applied on ${DateFormatter.formatDate(job.dateApplied)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (job.dateFollowedUp != null) {
            Text(
                text = "Followed up on ${DateFormatter.formatDate(job.dateFollowedUp)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
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
    onExport: () -> Unit,
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
                TextButton(onClick = onExport) { Text("Export") }
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
