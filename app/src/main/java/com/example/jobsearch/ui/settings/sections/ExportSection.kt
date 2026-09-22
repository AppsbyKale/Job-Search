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

@Composable
fun ExportSection(
    onExportCsv: () -> Unit,
    onImportCsv: () -> Unit
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.export_section_title))
        Text(
            stringResource(R.string.export_csv_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onExportCsv,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.export_csv_button)) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onImportCsv,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Import CSV") }
    }
}
