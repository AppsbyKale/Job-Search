package com.example.jobsearch.ui.settings.sections

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.jobsearch.R
import com.example.jobsearch.ui.components.AppCard
import com.example.jobsearch.ui.components.SectionHeader
import com.example.jobsearch.ui.settings.SettingsViewModel

@Composable
fun DataPrivacySection(
    state: SettingsViewModel.UiState,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onClearDatabase: () -> Unit
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    AppCard(modifier = Modifier.fillMaxWidth()) {
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
            Button(
                onClick = onBackup,
                enabled = !state.busy,
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.backup_data_button)) }

            OutlinedButton(
                onClick = onRestore,
                enabled = !state.busy,
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.restore_data_button)) }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { showClearConfirm = true },
            enabled = !state.busy,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Clear Database Jobs")
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear All Jobs?") },
            text = { Text("This will permanently delete all jobs in the app database so you can re-sync from your Chrome addon. Continue?") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirm = false
                        onClearDatabase()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete All") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
