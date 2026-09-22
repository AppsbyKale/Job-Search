package com.example.jobsearch.ui.settings

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jobsearch.R
import com.example.jobsearch.ui.components.AppCard
import com.example.jobsearch.ui.components.ErrorCard
import com.example.jobsearch.ui.components.SectionHeader
import com.example.jobsearch.ui.settings.sections.CloudAiSection
import com.example.jobsearch.ui.settings.sections.DataPrivacySection
import com.example.jobsearch.ui.settings.sections.ExportSection
import com.example.jobsearch.ui.settings.sections.ModelSection
import com.example.jobsearch.ui.settings.sections.ResumeSection
import com.example.jobsearch.ui.settings.sections.SchedulingSection
import com.example.jobsearch.ui.settings.sections.ServerSettingsDialog
import com.example.jobsearch.ui.settings.sections.TrainingSection
import kotlinx.coroutines.launch
import java.util.Locale

enum class SettingsSection(val title: String) {
    RESUME("Resume"),
    AI_MODELS("AI Models"),
    SERVER_INFO("Server Info"),
    LOGGING_DEBUG("Logging / Debug"),
    EXPORT_BACKUP("Export / Backup"),
    SCHEDULING("Scheduling")
}

/**
 * Screen for application settings, categorized into sections via a gear icon menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    initialSection: SettingsSection = SettingsSection.RESUME,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    var selectedSection by rememberSaveable { mutableStateOf(initialSection) }
    var menuExpanded by remember { mutableStateOf(false) }

    var showEditDialog by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }

    if (showEditDialog) {
        EditResumeDialog(
            initialText = state.resumeText,
            onSave = { newText ->
                viewModel.onResumeTextChange(newText)
                viewModel.saveResumeText()
            },
            onDismiss = { showEditDialog = false }
        )
    }

    if (showServerDialog) {
        ServerSettingsDialog(
            state = state,
            onToggleStartup = viewModel::toggleRunSyncOnStartup,
            onToggleManual = { viewModel.toggleServerManually(context) },
            onDismiss = { showServerDialog = false }
        )
    }

    val resumeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val name = queryDisplayName(context, it) ?: "Resume.txt"
            viewModel.importResume(context, it, name)
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportBackup(it) }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importBackup(it) }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportCsv(it) }
    }

    val csvImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importCsv(it) }
    }

    val trainingLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/jsonlines")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportTrainingData(it) }
    }

    val modelFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importCustomModel(context, it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_button))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings Categories")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            SettingsSection.entries.forEach { section ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            section.title,
                                            color = if (section == selectedSection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            style = if (section == selectedSection) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium
                                        )
                                    },
                                    onClick = {
                                        selectedSection = section
                                        menuExpanded = false
                                    }
                                )
                            }
                        }
                    }
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
            state.error?.let {
                ErrorCard(
                    title = "Error",
                    error = it,
                    onDismiss = viewModel::dismissMessage
                )
            }
            state.message?.let {
                AppCard {
                    Text(it, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    TextButton(onClick = viewModel::dismissMessage) { Text(stringResource(R.string.dismiss_button)) }
                }
            }

            when (selectedSection) {
                SettingsSection.RESUME -> {
                    ResumeSection(
                        state = state,
                        onImport = {
                            resumeLauncher.launch(
                                arrayOf(
                                    "text/plain",
                                    "application/pdf",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                )
                            )
                        },
                        onEditClick = { showEditDialog = true }
                    )
                }

                SettingsSection.AI_MODELS -> {
                    ModelSection(
                        state = state,
                        onUrlChange = viewModel::onModelUrlChange,
                        onSaveUrl = viewModel::saveModelUrl,
                        onDownload = viewModel::downloadModel,
                        onSelectFile = { modelFileLauncher.launch(arrayOf("*/*")) },
                        onDelete = viewModel::deleteModel
                    )

                    CloudAiSection(
                        state = state,
                        onApiKeyChange = viewModel::onGeminiApiKeyChange,
                        onSaveKey = viewModel::saveGeminiApiKey,
                        onToggleUseCustom = viewModel::toggleUseCustomApiKey,
                        onLangSearchKeyChange = viewModel::onLangSearchApiKeyChange,
                        onSaveLangSearchKey = viewModel::saveLangSearchApiKey
                    )
                }

                SettingsSection.SERVER_INFO -> {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        SectionHeader("Desktop Sync Server")
                        Text(
                            "Sync jobs from your Chrome extension directly to this app over Wi-Fi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Server Status", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    if (state.isServerRunning) "Running (Port ${state.desktopSyncPort}) • PIN: ${state.syncPin}" else "Offline",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (state.isServerRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                            OutlinedButton(onClick = { showServerDialog = true }) {
                                Text("Configure")
                            }
                        }
                    }
                }

                SettingsSection.LOGGING_DEBUG -> {
                    TrainingSection(
                        state = state,
                        onToggle = viewModel::toggleTrainingLogging,
                        onExport = { trainingLauncher.launch("jobsearch_training.jsonl") }
                    )

                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionHeader("System Logs")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (state.systemLogs.isNotEmpty()) {
                                            scope.launch {
                                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("System Logs", state.systemLogs.joinToString("\n"))))
                                                Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    enabled = state.systemLogs.isNotEmpty()
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy logs")
                                }
                                IconButton(
                                    onClick = {
                                        if (state.systemLogs.isNotEmpty()) {
                                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                putExtra(Intent.EXTRA_TEXT, state.systemLogs.joinToString("\n"))
                                                type = "text/plain"
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, "Share System Logs"))
                                        }
                                    },
                                    enabled = state.systemLogs.isNotEmpty()
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share logs")
                                }
                                IconButton(
                                    onClick = viewModel::clearLogs,
                                    enabled = state.systemLogs.isNotEmpty()
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Clear logs",
                                        tint = if (state.systemLogs.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (state.systemLogs.isEmpty()) {
                            Text("No logs yet.", style = MaterialTheme.typography.labelSmall)
                        } else {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    state.systemLogs.forEach { log ->
                                        Text(
                                            text = log,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                SettingsSection.EXPORT_BACKUP -> {
                    DataPrivacySection(
                        state = state,
                        onBackup = { backupLauncher.launch("jobsearch_backup.jsbackup") },
                        onRestore = {
                            restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                        },
                        onClearDatabase = viewModel::clearAllDatabaseJobs
                    )

                    ExportSection(
                        onExportCsv = { csvLauncher.launch("jobsearch_jobs.csv") },
                        onImportCsv = { csvImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "*/*")) }
                    )
                }

                SettingsSection.SCHEDULING -> {
                    SchedulingSection(
                        state = state,
                        onAddTime = viewModel::addFollowupInterval,
                        onRemoveTime = viewModel::removeFollowupInterval,
                        onTimeChange = viewModel::setFollowupTime
                    )
                }
            }
        }
    }
}

class FirstFourMaskVisualTransformation(private val visible: Boolean) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (visible || text.length <= 4) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val masked = text.text.take(4) + "*".repeat(text.length - 4)
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = offset
            override fun transformedToOriginal(offset: Int): Int = offset
        }
        return TransformedText(androidx.compose.ui.text.AnnotatedString(masked), offsetMapping)
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
    }
}
