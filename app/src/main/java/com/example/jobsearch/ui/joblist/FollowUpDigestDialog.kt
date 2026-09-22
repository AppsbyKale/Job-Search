package com.example.jobsearch.ui.joblist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jobsearch.data.Job
import com.example.jobsearch.ui.components.AppDialog
import com.example.jobsearch.util.DateFormatter

@Composable
fun FollowUpDigestDialog(
    jobs: List<Job>,
    onSelectJob: (Long) -> Unit,
    onMarkFollowedUp: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        title = "Pending Follow-Ups",
        onDismissRequest = onDismiss,
        fullScreen = true,
        scrollable = false,
        onSave = onDismiss,
        saveLabel = "Done"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "These applications need a follow-up. Check the box when you've sent it, or tap a job to generate/view its follow-up email.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (jobs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No pending follow-ups right now!", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(jobs, key = { it.id }) { job ->
                        Card(
                            onClick = { onSelectJob(job.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = job.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${job.company} • Applied ${job.dateApplied?.let { DateFormatter.formatDate(it) } ?: ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                Checkbox(
                                    checked = false,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            onMarkFollowedUp(job.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
