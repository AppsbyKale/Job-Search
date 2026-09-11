package com.example.jobsearch.ui.settings.sections

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.jobsearch.R
import com.example.jobsearch.ui.components.AppCard
import com.example.jobsearch.ui.components.SectionHeader
import com.example.jobsearch.ui.settings.SettingsViewModel
import java.util.Locale

@Composable
fun ModelSection(
    state: SettingsViewModel.UiState,
    onUrlChange: (String) -> Unit,
    onSaveUrl: () -> Unit,
    onDownload: () -> Unit,
    onSelectFile: () -> Unit,
    onDelete: () -> Unit
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.model_section_title))
        
        Text(
            stringResource(R.string.model_section_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        when {
            state.modelDownloaded -> {
                Text(
                    stringResource(R.string.model_ready_label, formatBytes(state.modelFileSize)),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDelete,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.delete_model_button)) }
            }

            state.downloading -> {
                Text(stringResource(R.string.downloading_model_label), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                if (state.progressTotal > 0) {
                    val fraction = (state.progressBytes.toFloat() / state.progressTotal.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        stringResource(
                            R.string.download_progress_label,
                            formatBytes(state.progressBytes),
                            formatBytes(state.progressTotal),
                            (fraction * 100).toInt()
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.downloaded_so_far_label, formatBytes(state.progressBytes)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.download_warning_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> {
                if (state.partialBytes > 0) {
                    Text(
                        stringResource(R.string.download_paused_label, formatBytes(state.partialBytes)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onDownload,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.resume_download_button)) }
                } else {
                    Button(
                        onClick = onDownload,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.download_model_button)) }
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onSelectFile,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Select Local Model File (.litertlm)") }
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = state.modelUrl,
            onValueChange = onUrlChange,
            label = { Text(stringResource(R.string.model_url_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onSaveUrl,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.save_url_button)) }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}
