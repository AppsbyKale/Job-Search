package com.example.jobsearch.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.jobsearch.data.JobStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusBadge(
    status: JobStatus,
    onSelect: ((JobStatus) -> Unit)? = null,
    onSelectWithDate: ((JobStatus, Long?) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    
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
            onClick = { if (onSelect != null || onSelectWithDate != null) expanded = true },
            label = { Text(status.label) },
            colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = containerColor,
                labelColor = labelColor
            ),
            border = null
        )
        if (onSelect != null || onSelectWithDate != null) {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                JobStatus.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.label) },
                        onClick = {
                            expanded = false
                            if (s == JobStatus.APPLIED) {
                                showDatePicker = true
                            } else {
                                onSelectWithDate?.invoke(s, null)
                                onSelect?.invoke(s)
                            }
                        }
                    )
                }
            }
        }

        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDatePicker = false
                            val selectedMillis = datePickerState.selectedDateMillis
                            onSelectWithDate?.invoke(JobStatus.APPLIED, selectedMillis)
                            onSelect?.invoke(JobStatus.APPLIED)
                        }
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text("Cancel")
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}
