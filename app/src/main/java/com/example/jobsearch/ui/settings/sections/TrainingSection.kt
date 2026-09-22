package com.example.jobsearch.ui.settings.sections

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.jobsearch.R
import com.example.jobsearch.ui.components.AppCard
import com.example.jobsearch.ui.components.SectionHeader
import com.example.jobsearch.ui.settings.SettingsViewModel

@Composable
fun TrainingSection(
    state: SettingsViewModel.UiState,
    onToggle: (Boolean) -> Unit,
    onExport: () -> Unit
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.training_logging_title))
        Text(
            stringResource(R.string.training_logging_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.training_logging_toggle), style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = state.trainingLoggingEnabled,
                onCheckedChange = onToggle
            )
        }
        if (state.trainingLoggingEnabled) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onExport,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.export_training_data_button)) }
        }
    }
}
