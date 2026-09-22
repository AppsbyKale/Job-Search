package com.example.jobsearch.ui.settings.sections

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.jobsearch.R
import com.example.jobsearch.ui.components.AppCard
import com.example.jobsearch.ui.components.SectionHeader
import com.example.jobsearch.ui.settings.FirstFourMaskVisualTransformation
import com.example.jobsearch.ui.settings.SettingsViewModel

@Composable
fun CloudAiSection(
    state: SettingsViewModel.UiState,
    onApiKeyChange: (String) -> Unit,
    onSaveKey: () -> Unit,
    onToggleUseCustom: (Boolean) -> Unit,
    onLangSearchKeyChange: (String) -> Unit,
    onSaveLangSearchKey: () -> Unit
) {
    var geminiVisible by remember { mutableStateOf(false) }
    var langVisible by remember { mutableStateOf(false) }

    AppCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.cloud_ai_section_title))
        Text(
            stringResource(R.string.cloud_ai_section_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Use Custom API Key", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = state.useCustomApiKey,
                onCheckedChange = onToggleUseCustom
            )
        }

        if (state.useCustomApiKey) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.geminiApiKey,
                onValueChange = onApiKeyChange,
                label = { Text(stringResource(R.string.gemini_api_key_label)) },
                singleLine = true,
                visualTransformation = FirstFourMaskVisualTransformation(geminiVisible),
                trailingIcon = {
                    IconButton(onClick = { geminiVisible = !geminiVisible }) {
                        Icon(
                            imageVector = if (geminiVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (geminiVisible) "Hide API Key" else "Show API Key"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onSaveKey,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.save_key_button)) }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.langSearchApiKey,
                onValueChange = onLangSearchKeyChange,
                label = { Text("LangSearch API Key") },
                singleLine = true,
                visualTransformation = FirstFourMaskVisualTransformation(langVisible),
                trailingIcon = {
                    IconButton(onClick = { langVisible = !langVisible }) {
                        Icon(
                            imageVector = if (langVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (langVisible) "Hide API Key" else "Show API Key"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onSaveLangSearchKey,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save LangSearch API Key") }
        }
    }
}
