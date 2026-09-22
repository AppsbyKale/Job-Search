package com.example.jobsearch.ui.settings.sections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.jobsearch.ui.components.AppCard
import com.example.jobsearch.ui.components.SectionHeader
import com.example.jobsearch.ui.settings.SettingsViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchedulingSection(
    state: SettingsViewModel.UiState,
    onAddTime: (String) -> Unit,
    onRemoveTime: (String) -> Unit,
    onTimeChange: (String) -> Unit
) {
    var newIntervalInput by remember { mutableStateOf("") }
    var timeText by remember(state.followupTime) { mutableStateOf(state.followupTime) }

    AppCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader("Follow-Up Scheduling")
        Text(
            "Configure multiple follow-up intervals after applying (e.g. 7 days, 14 days, 30 days).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        Text("Follow-Up Intervals (Days)", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))

        val intervalsList = state.followupIntervals.split(",").map { it.trim() }.filter { it.isNotBlank() }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            intervalsList.forEach { days ->
                InputChip(
                    selected = false,
                    onClick = { },
                    label = { Text("$days days") },
                    trailingIcon = {
                        IconButton(onClick = { onRemoveTime(days) }, modifier = Modifier.size(16.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Interval")
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newIntervalInput,
                onValueChange = { newIntervalInput = it },
                label = { Text("Add Days (e.g. 45)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    if (newIntervalInput.isNotBlank()) {
                        onAddTime(newIntervalInput)
                        newIntervalInput = ""
                    }
                },
                enabled = newIntervalInput.isNotBlank()
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = timeText,
            onValueChange = {
                timeText = it
                onTimeChange(it)
            },
            label = { Text("Reminder Time (e.g. 10:00)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
