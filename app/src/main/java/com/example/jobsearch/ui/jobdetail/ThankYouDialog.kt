package com.example.jobsearch.ui.jobdetail

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.jobsearch.data.CoverLetterData
import com.example.jobsearch.data.Job
import com.example.jobsearch.ui.components.AppDialog

@Composable
fun ThankYouDialog(
    job: Job?,
    running: Boolean,
    onGenerate: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var notes by remember { mutableStateOf("") }
    val hasEmail = job?.hasThankYouEmail == true
    val context = LocalContext.current

    AppDialog(
        title = "Post-Interview Thank You Email",
        onDismissRequest = onDismiss,
        fullScreen = true,
        onSave = { onGenerate(notes.ifBlank { null }) },
        saveLabel = if (hasEmail) "Regenerate" else "Generate Email",
        footer = {
            if (hasEmail) {
                OutlinedButton(
                    onClick = {
                        val emailText = CoverLetterData.fromJson(job?.thankYouEmailText ?: "")?.toHumanReadableText() 
                            ?: job?.thankYouEmailText ?: ""
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Thank You - ${job?.title} at ${job?.company}")
                            putExtra(Intent.EXTRA_TEXT, emailText)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Send Thank You Email"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Send / Share Email")
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Add any personalized notes, specific topics discussed, or highlights from your interview so the AI can tailor the thank-you email.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Interview Notes / Highlights (Optional)") },
                placeholder = { Text("e.g. Discussed team scaling, mentioned enthusiasm for Python microservices.") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4
            )

            if (running) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (hasEmail && !running) {
                Spacer(Modifier.height(8.dp))
                Text("Previously Generated Email:", style = MaterialTheme.typography.titleSmall)
                val preview = CoverLetterData.fromJson(job?.thankYouEmailText ?: "")?.toHumanReadableText() 
                    ?: job?.thankYouEmailText ?: ""
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        maxLines = 8
                    )
                }
            }
        }
    }
}
