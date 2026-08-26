package com.example.jobsearch.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jobsearch.R
import com.example.jobsearch.ui.components.AppCard
import com.example.jobsearch.ui.components.ErrorCard
import com.example.jobsearch.ui.components.SectionHeader
import java.util.Locale

/**
 * Screen for application settings, including resume management and AI model configuration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showEditDialog by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.checkPermissionState()
    }

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

    DisposableEffect(state.downloading) {
        val activity = context.findActivity()
        if (state.downloading) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val resumeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = queryDisplayName(context, uri) ?: "resume.txt"
            viewModel.importResume(context, uri, name)
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) viewModel.exportCsv(uri)
    }

    val trainingLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/jsonl")
    ) { uri ->
        if (uri != null) viewModel.exportTrainingData(uri)
    }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importBackup(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back_button))
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

            ModelSection(
                state = state,
                onUrlChange = viewModel::onModelUrlChange,
                onSaveUrl = viewModel::saveModelUrl,
                onDownload = viewModel::downloadModel,
                onDelete = viewModel::deleteModel
            )

            AppCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeader("Sync Server")
                Text(
                    "Configure how your phone receives jobs from the Chrome browser extension.",
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
                            if (state.isServerRunning) "Running (Listening on port ${state.desktopSyncPort})" else "Offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.isServerRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    OutlinedButton(onClick = { showServerDialog = true }) {
                        Text("Configure")
                    }
                }
            }

            AppCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeader(stringResource(R.string.cloud_ai_section_title))
                Text(
                    stringResource(R.string.cloud_ai_section_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.geminiApiKey,
                    onValueChange = viewModel::onGeminiApiKeyChange,
                    label = { Text(stringResource(R.string.gemini_api_key_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = viewModel::saveGeminiApiKey,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.save_key_button))
                }
            }

            AppCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeader(stringResource(R.string.training_logging_title))
                TrainingSection(
                    state = state, 
                    onToggle = viewModel::toggleTrainingLogging,
                    onExport = { trainingLauncher.launch("jobsearch_training.jsonl") }
                )

                SectionHeader(stringResource(R.string.data_privacy_section_title))
                Text(
                    stringResource(R.string.data_privacy_section_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { backupLauncher.launch("jobsearch.jsbackup") },
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.backup_data_button))
                    }
                    OutlinedButton(
                        onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.restore_data_button))
                    }
                }
            }

            AppCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeader(stringResource(R.string.export_section_title))
                Text(
                    stringResource(R.string.export_csv_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { csvLauncher.launch("jobsearch.csv") },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.export_csv_button))
                }
            }

            AppCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader("System Logs")
                    TextButton(onClick = viewModel::clearLogs) {
                        Text("Clear", color = MaterialTheme.colorScheme.error)
                    }
                }
                Text(
                    "Real-time activity of the AI models and system services.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
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
                        if (state.systemLogs.isEmpty()) {
                            Text("No logs yet.", style = MaterialTheme.typography.labelSmall)
                        } else {
                            state.systemLogs.forEach { log ->
                                Text(
                                    log,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrainingSection(
    state: SettingsViewModel.UiState,
    onToggle: (Boolean) -> Unit,
    onExport: () -> Unit
) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.training_logging_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.training_logging_toggle),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = state.trainingLoggingEnabled,
                    onCheckedChange = onToggle
                )
            }
            if (state.trainingLoggingEnabled) {
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onExport,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Export Training Examples (JSONL)")
                }
            }
        }
    }
}

@Composable
private fun ResumeSection(
    state: SettingsViewModel.UiState,
    onImport: () -> Unit,
    onEditClick: () -> Unit
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.resume_section_title))
        Text(
            stringResource(R.string.resume_section_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.resumeFileName.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.on_file_label, state.resumeFileName),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onImport,
                enabled = !state.busy,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (state.resumeFileName.isNotBlank()) stringResource(R.string.replace_button) else stringResource(R.string.import_button))
            }
            OutlinedButton(
                onClick = onEditClick,
                enabled = !state.busy,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.edit_text_button))
            }
        }

        if (state.resumeText.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            val snippet = state.resumeText.take(200).let {
                if (it.length < state.resumeText.length) "$it..." else it
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = snippet,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ModelSection(
    state: SettingsViewModel.UiState,
    onUrlChange: (String) -> Unit,
    onSaveUrl: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    AppCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.model_section_title))
        
        if (!state.allFilesAccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.all_files_permission_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.all_files_permission_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.grant_permission_button))
                    }
                }
            }
        }

        Text(
            stringResource(R.string.model_section_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        when {
            state.modelDownloaded -> {
                Text(
                    stringResource(R.string.model_ready_label, formatBytes(state.modelFileSize)),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDelete,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.delete_model_button)) }
            }

            state.downloading -> {
                Text(stringResource(R.string.downloading_model_label), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                if (state.progressTotal > 0) {
                    val fraction = (state.progressBytes.toFloat() / state.progressTotal.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        stringResource(
                            R.string.download_progress_label,
                            formatBytes(state.progressBytes),
                            formatBytes(state.progressTotal),
                            (fraction * 100).toInt()
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.downloaded_so_far_label, formatBytes(state.progressBytes)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.download_warning_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> {
                if (state.partialBytes > 0) {
                    Text(
                        stringResource(R.string.download_paused_label, formatBytes(state.partialBytes)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onDownload,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.resume_download_button)) }
                } else {
                    Button(
                        onClick = onDownload,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.download_model_button)) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = state.modelUrl,
            onValueChange = onUrlChange,
            label = { Text(stringResource(R.string.model_url_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onSaveUrl,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.save_url_button)) }
    }
}


@Composable
fun ServerSettingsDialog(
    state: SettingsViewModel.UiState,
    onToggleStartup: (Boolean) -> Unit,
    onToggleManual: () -> Unit,
    onDismiss: () -> Unit
) {
    com.example.jobsearch.ui.components.AppDialog(
        title = "Sync Server Settings",
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "The server allows your computer to send job postings directly to this app. Ensure both devices are on the same WiFi network.",
                style = MaterialTheme.typography.bodySmall
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (state.isServerRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (state.isServerRunning) "Server is ACTIVE" else "Server is OFF",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (state.isServerRunning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (state.isServerRunning && state.localIp != null) {
                        Text(
                            "IP Address: ${state.localIp}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Port: ${state.desktopSyncPort}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else if (state.isServerRunning && state.localIp == null) {
                        Text(
                            "WiFi not detected. Check your connection.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Run on Startup", style = MaterialTheme.typography.bodyLarge)
                    Text("Automatically start server when phone boots", style = MaterialTheme.typography.bodySmall)
                }
                androidx.compose.material3.Checkbox(
                    checked = state.runSyncOnStartup,
                    onCheckedChange = onToggleStartup
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Server Control", style = MaterialTheme.typography.bodyLarge)
                    Text(if (state.isServerRunning) "Turn off manually" else "Turn on manually", style = MaterialTheme.typography.bodySmall)
                }
                androidx.compose.material3.Switch(
                    checked = state.isServerRunning,
                    onCheckedChange = { onToggleManual() }
                )
            }
            
            if (state.recentSyncs.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Recent Syncs", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                state.recentSyncs.forEach { 
                    Text("• $it", style = MaterialTheme.typography.bodySmall)
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Close")
            }
        }
    }
}

private fun queryDisplayName(context: android.content.Context, uri: android.net.Uri): String? {
    return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}
