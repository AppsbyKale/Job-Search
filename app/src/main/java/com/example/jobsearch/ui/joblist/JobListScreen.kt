package com.example.jobsearch.ui.joblist

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.jobsearch.R
import com.example.jobsearch.data.Job
import com.example.jobsearch.data.JobStatus
import com.example.jobsearch.ui.components.AppCard
import com.example.jobsearch.ui.components.SectionHeader
import com.example.jobsearch.ui.components.StatusBadge
import com.example.jobsearch.util.DateFormatter

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction

/**
 * Screen displaying the list of saved jobs with filtering and status badges.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobListScreen(
    onAddJob: () -> Unit,
    onOpenJob: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onAddJobWithId: (Long) -> Unit,
    viewModel: JobListViewModel = hiltViewModel()
) {
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val syncedJobs by viewModel.syncedJobs.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    var jobToDelete by remember { mutableStateOf<Job?>(null) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showSyncedJobsDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchExpanded) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = viewModel::onSearchQueryChange,
                            placeholder = { Text("Search jobs...") },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            trailingIcon = {
                                IconButton(onClick = {
                                    viewModel.onSearchQueryChange("")
                                    isSearchExpanded = false
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                        )
                    } else {
                        Text(stringResource(R.string.job_search_title), style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    if (!isSearchExpanded) {
                        if (syncedJobs.isNotEmpty()) {
                            IconButton(onClick = { showSyncedJobsDialog = true }) {
                                Icon(
                                    Icons.Default.Sync,
                                    contentDescription = stringResource(R.string.review_required_label),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_button))
                        }
                        IconButton(onClick = onAddJob) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_button))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            StatusFilterRow(
                selected = filter,
                onSelect = viewModel::setFilter
            )
            if (jobs.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 8.dp,
                        end = 16.dp,
                        bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(jobs, key = { it.job.id }) { uiModel ->
                        JobItemCard(
                            uiModel = uiModel,
                            onClick = { onOpenJob(uiModel.job.id) },
                            onStatusChange = { status ->
                                viewModel.updateStatus(uiModel.job.id, status)
                            },
                            onDelete = { jobToDelete = uiModel.job }
                        )
                    }
                }
            }
        }
    }

    if (showSyncedJobsDialog) {
        SyncedJobsDialog(
            syncedJobs = syncedJobs,
            onReviewJob = { id ->
                showSyncedJobsDialog = false
                onAddJobWithId(id)
            },
            onDeleteJob = { id ->
                viewModel.deleteJob(id)
            },
            onDismiss = { showSyncedJobsDialog = false }
        )
    }

    jobToDelete?.let { job ->
        AlertDialog(
            onDismissRequest = { jobToDelete = null },
            title = { Text(stringResource(R.string.delete_job_confirm_title)) },
            text = { Text(stringResource(R.string.delete_job_confirm_message, job.title.ifBlank { job.company })) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteJob(job.id)
                    jobToDelete = null
                }) { Text(stringResource(R.string.delete_button)) }
            },
            dismissButton = {
                TextButton(onClick = { jobToDelete = null }) { Text(stringResource(R.string.cancel_button)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncedJobsDialog(
    syncedJobs: List<Job>,
    onReviewJob: (Long) -> Unit,
    onDeleteJob: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.synced_jobs_dialog_title)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_button))
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (syncedJobs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.no_synced_jobs_message),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                stringResource(R.string.synced_jobs_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        items(syncedJobs, key = { "dialog_synced_${it.id}" }) { job ->
                            SyncedJobCard(
                                job = job,
                                onClick = { onReviewJob(job.id) },
                                onDelete = { onDeleteJob(job.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncedJobCard(
    job: Job,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    AppCard(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_button),
                    tint = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = job.title.ifBlank { "Synced Job" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (job.company.isNotBlank()) {
                    Text(
                        text = job.company,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Default.Sync,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusFilterRow(selected: JobStatus?, onSelect: (JobStatus?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.filter_all)) }
        )
        JobStatus.entries.forEach { status ->
            FilterChip(
                selected = selected == status,
                onClick = { onSelect(status) },
                label = { Text(status.label) }
            )
        }
    }
}

@Composable
private fun JobItemCard(
    uiModel: JobListViewModel.JobUiModel,
    onClick: () -> Unit,
    onStatusChange: (JobStatus) -> Unit,
    onDelete: () -> Unit
) {
    val job = uiModel.job
    AppCard(onClick = onClick) {
        Text(
            text = job.title.ifBlank { stringResource(R.string.untitled_job) },
            style = MaterialTheme.typography.titleMedium
        )
        if (job.company.isNotBlank()) {
            Text(
                text = job.company,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = DateFormatter.formatDate(job.dateAdded),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(
                status = JobStatus.fromName(job.status),
                onSelect = onStatusChange
            )
            Spacer(Modifier.width(8.dp))
            if (job.hasResume) {
                AssistChip(onClick = {}, label = { Text(stringResource(R.string.resume_chip)) })
            }
            if (job.hasCoverLetter) {
                Spacer(Modifier.width(6.dp))
                AssistChip(onClick = {}, label = { Text(stringResource(R.string.cover_chip)) })
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDelete) { Text(stringResource(R.string.delete_button)) }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.empty_jobs_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.empty_jobs_message),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
