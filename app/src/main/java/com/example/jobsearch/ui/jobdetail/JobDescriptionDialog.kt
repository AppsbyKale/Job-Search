package com.example.jobsearch.ui.jobdetail

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.jobsearch.R
import com.example.jobsearch.ui.components.AppDialog

@Composable
fun JobDescriptionDialog(
    description: String,
    onDismiss: () -> Unit
) {
    AppDialog(
        title = stringResource(R.string.job_description_header),
        onDismissRequest = onDismiss,
        fullScreen = true,
        onSave = onDismiss,
        saveLabel = stringResource(R.string.done_button)
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
