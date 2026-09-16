package com.example.jobsearch.ui.jobdetail.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.jobsearch.R
import com.example.jobsearch.data.Job
import com.example.jobsearch.ui.jobdetail.JobDetailViewModel
import com.example.jobsearch.util.DateFormatter

@Composable
fun JobHeader(
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
