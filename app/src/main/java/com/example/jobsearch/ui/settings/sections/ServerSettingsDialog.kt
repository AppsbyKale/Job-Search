package com.example.jobsearch.ui.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jobsearch.ui.components.AppDialog
import com.example.jobsearch.ui.settings.SettingsViewModel

@Composable
fun ServerSettingsDialog(
    state: SettingsViewModel.UiState,
    onToggleStartup: (Boolean) -> Unit,
    onToggleManual: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        title = "Sync Server Settings",
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "The server allows your computer to send job postings directly to this app. Ensure both devices are on the same WiFi network.",
                style = MaterialTheme.typography.bodySmall
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (state.isServerRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (state.isServerRunning) "Server is ACTIVE" else "Server is OFF",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (state.isServerRunning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (state.isServerRunning && state.localIp != null) {
                        Text(
                            "IP Address: ${state.localIp}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Port: ${state.desktopSyncPort}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else if (state.isServerRunning) {
                        Text(
                            "WiFi not detected. Check your connection.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Run on Startup", style = MaterialTheme.typography.bodyLarge)
                    Text("Automatically start server when phone boots", style = MaterialTheme.typography.bodySmall)
                }
                Checkbox(
                    checked = state.runSyncOnStartup,
                    onCheckedChange = onToggleStartup
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Server Control", style = MaterialTheme.typography.bodyLarge)
                    Text(if (state.isServerRunning) "Turn off manually" else "Turn on manually", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = state.isServerRunning,
                    onCheckedChange = { onToggleManual() }
                )
            }
            
            if (state.recentSyncs.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Recent Syncs", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                state.recentSyncs.forEach { 
                    Text("• $it", style = MaterialTheme.typography.bodySmall)
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Close")
            }
        }
    }
}
