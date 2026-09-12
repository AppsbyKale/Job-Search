package com.example.jobsearch.ui.jobdetail.components

import android.content.ClipData
import com.example.jobsearch.util.DateFormatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jobsearch.R
import com.example.jobsearch.data.JobStatus
import com.example.jobsearch.ui.components.StatusBadge
import com.example.jobsearch.ui.jobdetail.JobDetailViewModel
import com.example.jobsearch.ui.jobdetail.JobUrlWebViewDialog
import kotlinx.coroutines.launch

@Composable
fun LinkAndStatusSection(state: JobDetailViewModel.UiState, viewModel: JobDetailViewModel) {
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
fun StatusRow(state: JobDetailViewModel.UiState, viewModel: JobDetailViewModel) {
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
    }
}
