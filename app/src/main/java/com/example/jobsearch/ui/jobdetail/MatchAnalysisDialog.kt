package com.example.jobsearch.ui.jobdetail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.jobsearch.R
import com.example.jobsearch.ui.components.SectionHeader
import com.example.jobsearch.ui.components.AppDialog

@Composable
fun MatchAnalysisDialog(
    matchResult: JobDetailViewModel.MatchResult?,
    matchRunning: Boolean,
    onAnalyze: () -> Unit,
    onGenerateSupplemental: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        title = stringResource(R.string.match_analysis_title),
        onDismissRequest = onDismiss,
        fullScreen = true,
        onSave = onDismiss,
        saveLabel = stringResource(R.string.done_button),
        footer = {
            if (matchResult != null && !matchRunning) {
                TextButton(
                    onClick = onAnalyze,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Re-analyze")
                }
            }
        }
    ) {
        if (matchRunning) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    stringResource(R.string.checking_match_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        } else if (matchResult == null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "No match analysis exists for this job yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onAnalyze,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Analyze Match")
                }
            }
        } else {
            MatchScoreHeader(matchResult.score)

            if (matchResult.foundKeywords.isNotEmpty()) {
                SectionHeader(stringResource(R.string.skills_found_label))
                KeywordFlow(matchResult.foundKeywords, isMissing = false)
            }

            if (matchResult.missingKeywords.isNotEmpty()) {
                SectionHeader(stringResource(R.string.skills_missing_label))
                KeywordFlow(matchResult.missingKeywords, isMissing = true)

                Button(
                    onClick = {
                        onGenerateSupplemental()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.generate_questions_for_missing_skills))
                }
            }
        }
    }
}

@Composable
private fun MatchScoreHeader(score: Int) {
    val color = when {
        score >= 90 -> MaterialTheme.colorScheme.primary
        score >= 60 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    Column {
        Text(
            text = "$score%",
            style = MaterialTheme.typography.displayMedium,
            color = color
        )
        Text(
            text = stringResource(R.string.overall_match_score),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun KeywordFlow(keywords: List<String>, isMissing: Boolean) {
    // Using a simple Row with scroll for now as standard FlowRow might not be available or needed
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        keywords.forEach { keyword ->
            SuggestionChip(
                onClick = { },
                label = { Text(keyword) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = if (isMissing) 
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f) 
                    else 
                        MaterialTheme.colorScheme.primaryContainer,
                    labelColor = if (isMissing) 
                        MaterialTheme.colorScheme.onErrorContainer 
                    else 
                        MaterialTheme.colorScheme.onPrimaryContainer
                ),
                border = null
            )
        }
    }
}
