package com.example.jobsearch.ui.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.jobsearch.R
import com.example.jobsearch.ui.components.AppCard
import com.example.jobsearch.ui.components.SectionHeader
import com.example.jobsearch.ui.settings.SettingsViewModel

@Composable
fun ResumeSection(
    state: SettingsViewModel.UiState,
    onImport: () -> Unit,
    onEditClick: () -> Unit
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.resume_section_title))
        Text(
            stringResource(R.string.resume_section_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.resumeFileName.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.on_file_label, state.resumeFileName),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onImport,
                enabled = !state.busy,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (state.resumeFileName.isNotBlank()) stringResource(R.string.replace_button) else stringResource(R.string.import_button))
            }
            OutlinedButton(
                onClick = onEditClick,
                enabled = !state.busy,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.edit_text_button))
            }
        }

        if (state.resumeText.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            val snippet = state.resumeText.take(200).let {
                if (it.length < state.resumeText.length) "$it..." else it
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = snippet,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
