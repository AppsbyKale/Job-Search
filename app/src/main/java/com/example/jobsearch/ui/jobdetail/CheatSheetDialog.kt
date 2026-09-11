package com.example.jobsearch.ui.jobdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.jobsearch.R
import com.example.jobsearch.data.CheatSheetData
import com.example.jobsearch.data.Job
import com.example.jobsearch.ui.components.AppDialog

@Composable
fun CheatSheetDialog(
    job: Job?,
    running: Boolean,
    progress: Float,
    progressText: String?,
    onGenerate: () -> Unit,
    onCopy: (String) -> Unit,
    onExportPdf: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        title = stringResource(R.string.interview_cheat_sheet_label),
        onDismissRequest = onDismiss,
        fullScreen = true,
        onSave = onDismiss,
        saveLabel = stringResource(R.string.done_button),
        footer = {
            if (job?.hasCheatSheet == true && !running) {
                Button(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.regenerate_button))
                }
            }
        }
    ) {
        if (job?.hasCheatSheet == true) {
            val data = CheatSheetData.fromJson(job.cheatSheetText)
            val display = data?.toHumanReadableText() ?: job.cheatSheetText
            Text(display, style = MaterialTheme.typography.bodyMedium)
            
            if (!running) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onCopy(display) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.copy_button))
                    }
                    Button(
                        onClick = { onExportPdf(job.cheatSheetText) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Export PDF")
                    }
                }
            }
        } else if (!running) {
            Text(
                stringResource(R.string.no_cheat_sheet_message),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.generate_now_button))
            }
        }

        if (running) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = progressText ?: stringResource(R.string.generating_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
