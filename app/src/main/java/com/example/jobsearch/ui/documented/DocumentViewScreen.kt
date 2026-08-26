package com.example.jobsearch.ui.documented

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jobsearch.R
import com.example.jobsearch.ui.documented.editor.EditableEducationBlock
import com.example.jobsearch.ui.documented.editor.EditableExperienceBlock
import com.example.jobsearch.ui.documented.editor.EditableHeader
import com.example.jobsearch.ui.documented.editor.EditableParagraph
import com.example.jobsearch.ui.documented.editor.EditableProjectBlock
import com.example.jobsearch.ui.documented.editor.EditableSectionHeader
import com.example.jobsearch.ui.documented.editor.RichTextToolbar

/**
 * Screen for viewing and editing generated documents with live PDF-style preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewScreen(
    onBack: () -> Unit,
    initialEdit: Boolean = false,
    viewModel: DocumentViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editing by rememberSaveable { mutableStateOf(initialEdit) }

    val savedMsg = stringResource(R.string.saved_to_file_message)
    val failedMsg = stringResource(R.string.save_failed_message)
    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            val ok = viewModel.exportPdf(uri)
            Toast.makeText(
                context,
                if (ok) savedMsg else failedMsg,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    BackHandler(enabled = editing) { editing = false }

    val title = when (viewModel.type) {
        "resume" -> stringResource(R.string.resume_label)
        "cheat" -> stringResource(R.string.cheat_sheet_title)
        "followup" -> stringResource(R.string.follow_up_email_title)
        else -> stringResource(R.string.cover_letter_label)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { if (editing) editing = false else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_button))
                    }
                },
                actions = {
                    if (editing) {
                        IconButton(onClick = {
                            viewModel.save()
                            editing = false
                        }) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = stringResource(R.string.save_button)
                            )
                        }
                        IconButton(onClick = viewModel::copyToClipboard) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.copy_button)
                            )
                        }
                        TextButton(onClick = { editing = false }) { Text(stringResource(R.string.preview_button)) }
                    } else {
                        TextButton(onClick = { editing = true }) { Text(stringResource(R.string.edit_button)) }
                    }
                }
            )
        }
    ) { padding ->
        if (editing) {
            StudioPane(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.padding(padding)
            )
        } else {
            PreviewPane(
                state = state,
                viewModel = viewModel,
                pdfLauncher = pdfLauncher,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

private sealed interface PreviewState {
    data object Loading : PreviewState
    data class Ready(val pages: List<Bitmap>) : PreviewState
    data object Failed : PreviewState
}

@Composable
private fun PreviewPane(
    state: DocumentViewModel.UiState,
    viewModel: DocumentViewModel,
    pdfLauncher: ManagedActivityResultLauncher<String, Uri?>,
    modifier: Modifier = Modifier
) {
    var attempt by rememberSaveable { mutableStateOf(0) }
    val preview by produceState<PreviewState>(
        initialValue = PreviewState.Loading,
        state.text,
        attempt
    ) {
        value = viewModel.previewPages()?.let { PreviewState.Ready(it) } ?: PreviewState.Failed
    }

    Box(modifier.fillMaxSize()) {
        when (val current = preview) {
            PreviewState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

            PreviewState.Failed -> Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.pdf_preview_failed_message),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(onClick = { attempt++ }) { Text(stringResource(R.string.retry_button)) }
            }

            is PreviewState.Ready -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        stringResource(R.string.pdf_preview_tip),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                itemsIndexed(current.pages) { index, page ->
                    Image(
                        bitmap = page.asImageBitmap(),
                        contentDescription = stringResource(R.string.page_number_desc, index + 1),
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(page.width.toFloat() / page.height)
                            .clip(RoundedCornerShape(4.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(4.dp)
                            )
                            .background(MaterialTheme.colorScheme.surface)
                    )
                }
            }
        }

        if (preview is PreviewState.Ready) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = viewModel::share,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.share_button)) }
                OutlinedButton(
                    onClick = { pdfLauncher.launch(viewModel.pdfFileName()) },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.save_to_file_button)) }
            }
        }
    }
}

@Composable
private fun StudioPane(
    state: DocumentViewModel.UiState,
    viewModel: DocumentViewModel,
    modifier: Modifier = Modifier
) {
    val resume = state.resumeData

    Column(modifier = modifier.fillMaxSize()) {
        RichTextToolbar()

        Box(
            modifier = Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                color = Color.White,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (resume != null) {
                        EditableHeader(
                            name = resume.name,
                            onNameChange = viewModel::updateResumeName,
                            contact = resume.contact,
                            onContactChange = viewModel::updateResumeContact
                        )

                        if (resume.summary.isNotBlank()) {
                            EditableSectionHeader(title = "Summary")
                            EditableParagraph(
                                text = resume.summary,
                                onTextChange = viewModel::updateResumeSummary
                            )
                        }

                        if (resume.experience.isNotEmpty()) {
                            EditableSectionHeader(title = "Experience")
                            resume.experience.forEachIndexed { index, item ->
                                EditableExperienceBlock(
                                    item = item,
                                    onTitleChange = { viewModel.updateExperienceTitle(index, it) },
                                    onCompanyChange = { viewModel.updateExperienceCompany(index, it) },
                                    onBulletChange = { bIndex, text -> viewModel.updateJobBullet(index, bIndex, text) }
                                )
                            }
                        }

                        if (resume.education.isNotEmpty()) {
                            EditableSectionHeader(title = "Education")
                            resume.education.forEachIndexed { index, item ->
                                EditableEducationBlock(
                                    item = item,
                                    onDegreeChange = { viewModel.updateEducationDegree(index, it) },
                                    onSchoolChange = { viewModel.updateEducationSchool(index, it) },
                                    onDatesChange = { viewModel.updateEducationDates(index, it) }
                                )
                            }
                        }

                        if (resume.projects.isNotEmpty()) {
                            EditableSectionHeader(title = "Projects")
                            resume.projects.forEachIndexed { index, item ->
                                EditableProjectBlock(
                                    item = item,
                                    onNameChange = { viewModel.updateProjectName(index, it) },
                                    onBulletChange = { bIndex, text -> viewModel.updateProjectBullet(index, bIndex, text) }
                                )
                            }
                        }
                    } else {
                        EditableParagraph(
                            text = state.text,
                            onTextChange = viewModel::onTextChange
                        )
                    }
                }
            }
        }
    }
}
