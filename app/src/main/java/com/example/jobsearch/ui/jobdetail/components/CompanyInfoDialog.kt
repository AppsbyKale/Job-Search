package com.example.jobsearch.ui.jobdetail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jobsearch.ui.components.AppDialog

@Composable
fun CompanyInfoDialog(
    companyName: String,
    companyInfo: String,
    onDismiss: () -> Unit
) {
    AppDialog(
        title = companyName.ifBlank { "Company Insights" },
        onDismissRequest = onDismiss,
        onSave = onDismiss,
        saveLabel = "Close"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 150.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (companyInfo.isBlank()) {
                Text(
                    "No company background information available yet. Generate a cheat sheet or view details to fetch company insights.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    companyInfo,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
