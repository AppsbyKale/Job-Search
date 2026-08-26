package com.example.jobsearch.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.jobsearch.data.JobStatus

@Composable
fun StatusBadge(
    status: JobStatus,
    onSelect: ((JobStatus) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    
    val containerColor = when (status) {
        JobStatus.SYNCED -> MaterialTheme.colorScheme.secondaryContainer
        JobStatus.SAVED -> MaterialTheme.colorScheme.secondaryContainer
        JobStatus.APPLIED -> MaterialTheme.colorScheme.primaryContainer
        JobStatus.INTERVIEWING -> MaterialTheme.colorScheme.tertiaryContainer
        JobStatus.OFFER -> MaterialTheme.colorScheme.primaryContainer
        JobStatus.REJECTED, JobStatus.ARCHIVED -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    val labelColor = when (status) {
        JobStatus.SYNCED -> MaterialTheme.colorScheme.onSecondaryContainer
        JobStatus.SAVED -> MaterialTheme.colorScheme.onSecondaryContainer
        JobStatus.APPLIED -> MaterialTheme.colorScheme.onPrimaryContainer
        JobStatus.INTERVIEWING -> MaterialTheme.colorScheme.onTertiaryContainer
        JobStatus.OFFER -> MaterialTheme.colorScheme.onPrimaryContainer
        JobStatus.REJECTED, JobStatus.ARCHIVED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box {
        SuggestionChip(
            onClick = { if (onSelect != null) expanded = true },
            label = { Text(status.label) },
            colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = containerColor,
                labelColor = labelColor
            ),
            border = null
        )
        if (onSelect != null) {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                JobStatus.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.label) },
                        onClick = {
                            expanded = false
                            onSelect(s)
                        }
                    )
                }
            }
        }
    }
}
