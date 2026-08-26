package com.example.jobsearch.ui.jobdetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.jobsearch.R
import com.example.jobsearch.data.InterviewAnswer
import com.example.jobsearch.data.InterviewQuestion
import com.example.jobsearch.ui.components.AppDialog

@Composable
fun SupplementalQuestionsDialog(
    questions: List<InterviewQuestion>,
    answers: List<InterviewAnswer>,
    running: Boolean,
    onGenerate: () -> Unit,
    onAnswer: (Long, String) -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        title = stringResource(R.string.supplemental_questions_label),
        onDismissRequest = onDismiss,
        fullScreen = true,
        onSave = onDismiss, // Since answers are updated live, Save just dismisses
        saveLabel = stringResource(R.string.done_button),
        footer = {
            if (questions.isNotEmpty() && !running) {
                OutlinedButton(
                    onClick = onGenerate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.regenerate_questions_button))
                }
            }
        }
    ) {
        Text(
            stringResource(R.string.supplemental_questions_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (questions.isEmpty()) {
            if (running) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                Button(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.generate_questions_button))
                }
            }
        } else {
            questions.forEach { q ->
                QuestionItem(
                    question = q,
                    initialAnswer = answers.find { it.questionId == q.id }?.text ?: "",
                    onAnswer = onAnswer
                )
            }
            if (running) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun QuestionItem(
    question: InterviewQuestion,
    initialAnswer: String,
    onAnswer: (Long, String) -> Unit
) {
    var text by remember(question.id) { mutableStateOf(initialAnswer) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(question.question, style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onAnswer(question.id, it)
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.your_answer_placeholder)) }
        )
    }
}
