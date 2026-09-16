package com.example.jobsearch.ui.jobdetail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jobsearch.ui.components.AppDialog

@Composable
fun CheatSheetOptionsDialog(
    onGenerate: (
        includeOverview: Boolean,
        includeChallenges: Boolean,
        includeDayToDay: Boolean,
        includeHighlights: Boolean,
        customQuestions: String,
        strengths: String,
        weaknesses: String
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var includeOverview by remember { mutableStateOf(true) }
    var includeChallenges by remember { mutableStateOf(true) }
    var includeDayToDay by remember { mutableStateOf(true) }
    var includeHighlights by remember { mutableStateOf(true) }
    var customQuestions by remember { mutableStateOf("") }
    var strengths by remember { mutableStateOf("") }
    var weaknesses by remember { mutableStateOf("") }

    AppDialog(
        title = "Interview Cheat Sheet Options",
        onDismissRequest = onDismiss,
        onSave = {
            onGenerate(
                includeOverview,
                includeChallenges,
                includeDayToDay,
                includeHighlights,
                customQuestions,
                strengths,
                weaknesses
            )
        },
        saveLabel = "Generate"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 300.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Select Cheat Sheet Topics:", style = MaterialTheme.typography.titleSmall)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = includeOverview, onCheckedChange = { includeOverview = it })
                Spacer(Modifier.width(8.dp))
                Text("Company Overview & Culture", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = includeChallenges, onCheckedChange = { includeChallenges = it })
                Spacer(Modifier.width(8.dp))
                Text("Challenges of the Role", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = includeDayToDay, onCheckedChange = { includeDayToDay = it })
                Spacer(Modifier.width(8.dp))
                Text("Day-to-Day Responsibilities", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = includeHighlights, onCheckedChange = { includeHighlights = it })
                Spacer(Modifier.width(8.dp))
                Text("Key Qualifications & Selling Points", style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = strengths,
                onValueChange = { strengths = it },
                label = { Text("Core Strengths to Highlight (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = weaknesses,
                onValueChange = { weaknesses = it },
                label = { Text("Weaknesses / Gaps to Address (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = customQuestions,
                onValueChange = { customQuestions = it },
                label = { Text("Custom Interview Questions (comma or newline separated)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
