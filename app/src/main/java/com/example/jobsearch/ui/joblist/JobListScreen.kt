package com.example.jobsearch.ui.joblist

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.jobsearch.R
import com.example.jobsearch.data.Job
import com.example.jobsearch.data.JobStatus
import com.example.jobsearch.ui.joblist.FollowUpDigestDialog
import com.example.jobsearch.ui.components.AppCard
import com.example.jobsearch.ui.components.StatusBadge
import com.example.jobsearch.ui.settings.SettingsSection
import com.example.jobsearch.util.DateFormatter
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow

/**
 * Screen displaying the list of saved jobs with filtering and sorting dropdowns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobListScreen(
    onAddJob: () -> Unit,
    onOpenJob: (Long) -> Unit,
    onOpenSettings: (String) -> Unit = {},
    onAddJobWithId: (Long) -> Unit,
    viewModel: JobListViewModel = hiltViewModel()
) {
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val syncedJobs by viewModel.syncedJobs.collectAsStateWithLifecycle()
    val pendingFollowUps by viewModel.pendingFollowUps.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    var jobToDelete by remember { mutableStateOf<Job?>(null) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showSyncedJobsDialog by remember { mutableStateOf(false) }
    var showFollowUpDigestDialog by remember { mutableStateOf(false) }
    var settingsMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchExpanded) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = viewModel::onSearchQueryChange,
                            placeholder = { Text(stringResource(R.string.search_jobs_placeholder)) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            trailingIcon = {
                                IconButton(onClick = {
                                    viewModel.onSearchQueryChange("")
                                    isSearchExpanded = false
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_search_desc))
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
                        Box {
                            IconButton(onClick = { settingsMenuExpanded = true }) {
                                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_button))
                            }
                            DropdownMenu(
                                expanded = settingsMenuExpanded,
                                onDismissRequest = { settingsMenuExpanded = false }
                            ) {
                                SettingsSection.entries.forEach { section ->
                                    DropdownMenuItem(
                                        modifier = if (section == SettingsSection.RESUME) Modifier.testTag("menu_item_resume") else Modifier,
                                        text = {
                                            Text(
                                                section.title,
                                                style = if (section == SettingsSection.RESUME) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium
                                            )
                                        },
                                        onClick = {
                                            settingsMenuExpanded = false
                                            onOpenSettings(section.name)
                                        }
                                    )
                                }
                            }
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
            if (pendingFollowUps.isNotEmpty()) {
                Card(
                    onClick = { showFollowUpDigestDialog = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Follow-Up Needed", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text("${pendingFollowUps.size} application(s) ready for follow-up", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                        TextButton(onClick = { showFollowUpDigestDialog = true }) {
                            Text("View", color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
            }

            SortingDropdownRow(
                selectedFilter = filter,
                onSelectFilter = viewModel::setFilter,
                selectedSort = sortOrder,
                onSelectSort = viewModel::setSortOrder
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
                            onStatusChangeWithDate = { status, date ->
                                viewModel.updateStatusWithDate(uiModel.job.id, status, date)
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

    if (showFollowUpDigestDialog) {
        FollowUpDigestDialog(
            jobs = pendingFollowUps,
            onSelectJob = { id ->
                showFollowUpDigestDialog = false
                onOpenJob(id)
            },
            onMarkFollowedUp = { id ->
                viewModel.markFollowedUp(id)
            },
            onDismiss = { showFollowUpDigestDialog = false }
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

@Composable
private fun SortingDropdownRow(
    selectedFilter: JobStatus?,
    onSelectFilter: (JobStatus?) -> Unit,
    selectedSort: SortOrder,
    onSelectSort: (SortOrder) -> Unit
) {
    var statusExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Status Dropdown
        Box(modifier = Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { statusExpanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = selectedFilter?.label ?: "All Statuses",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            DropdownMenu(expanded = statusExpanded, onDismissRequest = { statusExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("All Statuses") },
                    onClick = {
                        statusExpanded = false
                        onSelectFilter(null)
                    }
                )
                JobStatus.entries.forEach { status ->
                    DropdownMenuItem(
                        text = { Text(status.label) },
                        onClick = {
                            statusExpanded = false
                            onSelectFilter(status)
                        }
                    )
                }
            }
        }

        // Sort Dropdown
        Box(modifier = Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { sortExpanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = selectedSort.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                SortOrder.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(sort.label) },
                        onClick = {
                            sortExpanded = false
                            onSelectSort(sort)
                        }
                    )
                }
            }
        }
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
    var isAscending by remember { mutableStateOf(false) }

    val displayList = remember(syncedJobs, isAscending) {
        if (isAscending) syncedJobs.sortedBy { it.dateAdded }
        else syncedJobs.sortedByDescending { it.dateAdded }
    }

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
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    stringResource(R.string.synced_jobs_description),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${syncedJobs.size} job(s) pending review",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    FilterChip(
                                        selected = true,
                                        onClick = { isAscending = !isAscending },
                                        label = { Text(if (!isAscending) "Newest First" else "Oldest First") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (!isAscending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                                contentDescription = "Sort Order",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                        items(displayList, key = { "dialog_synced_${it.id}" }) { job ->
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
                    overflow = TextOverflow.Ellipsis
                )
                if (job.company.isNotBlank()) {
                    Text(
                        text = job.company,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "Synced ${DateFormatter.formatDate(job.dateAdded)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )
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

@Composable
private fun JobItemCard(
    uiModel: JobListViewModel.JobUiModel,
    onClick: () -> Unit,
    onStatusChangeWithDate: (JobStatus, Long?) -> Unit,
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
            text = "Added: ${DateFormatter.formatDate(job.dateAdded)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (job.status == JobStatus.APPLIED.name && job.dateApplied != null) {
            Text(
                text = "Applied on ${DateFormatter.formatDate(job.dateApplied)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(
                status = JobStatus.fromName(job.status),
                onSelectWithDate = onStatusChangeWithDate
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
