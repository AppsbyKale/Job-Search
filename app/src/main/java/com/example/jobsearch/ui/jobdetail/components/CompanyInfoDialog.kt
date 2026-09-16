package com.example.jobsearch.ui.jobdetail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
    isSearching: Boolean,
    onFetchCompany: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        title = companyName.ifBlank { "Company Insights" },
        onDismissRequest = onDismiss,
        onSave = onDismiss,
        saveLabel = "Close",
        footer = {
            Button(
                onClick = onFetchCompany,
                enabled = !isSearching,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Re-search Company (LangSearch)")
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 150.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isSearching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(
                    "Searching company background and culture via LangSearch...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (companyInfo.isBlank()) {
                Text(
                    "No company background information available yet. Tap the button below to search and fetch company insights via LangSearch.",
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
