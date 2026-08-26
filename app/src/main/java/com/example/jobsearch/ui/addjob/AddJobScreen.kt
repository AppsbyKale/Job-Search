package com.example.jobsearch.ui.addjob

import android.content.Intent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jobsearch.R
import com.example.jobsearch.ui.components.AppCard
import com.example.jobsearch.ui.components.ErrorCard
import com.example.jobsearch.ui.components.SectionHeader
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Screen for adding a new job, featuring URL parsing and AI-driven match analysis.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddJobScreen(
    sharedUrl: String? = null,
    jobId: Long? = null,
    onBack: () -> Unit,
    onJobSaved: (Long, String?) -> Unit,
    viewModel: AddJobViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(sharedUrl, jobId) {
        if (jobId != null) {
            viewModel.loadJob(jobId)
        } else if (sharedUrl != null && state.url != sharedUrl) {
            viewModel.onUrlChange(sharedUrl)
            viewModel.parse()
        }
    }

    LaunchedEffect(state.savedJobId) {
        state.savedJobId?.let { id ->
            onJobSaved(id, state.generateChoice)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_job_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_button))
                    }
                },
                actions = {
                    if (state.description.isNotBlank()) {
                        IconButton(
                            onClick = viewModel::smartCleanDescription,
                            enabled = !state.smartCleaning
                        ) {
                            if (state.smartCleaning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = stringResource(R.string.smart_clean_desc)
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.add_job_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = state.url,
                onValueChange = viewModel::onUrlChange,
                label = { Text(stringResource(R.string.job_url_label)) },
                singleLine = true,
                enabled = !state.parsing,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            state.parseError?.let { error ->
                ErrorCard(
                    title = "Parse Error",
                    error = error,
                    onDismiss = { /* Auto-dismissed when re-fetching */ }
                )
                if (state.url.isNotBlank()) {
                    val context = LocalContext.current
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            openInExternalBrowser(context, state.url.trim())
                        }) {
                            Text(stringResource(R.string.open_in_browser_button))
                        }
                        TextButton(onClick = viewModel::openWebView) {
                            Text(stringResource(R.string.fetch_builtin_browser_button))
                        }
                    }
                }
            }

            if (state.webViewOpen) {
                JobWebViewDialog(
                    url = state.url.trim(),
                    onCancel = viewModel::closeWebView,
                    onUsePage = viewModel::onWebViewHtml
                )
            }

            Button(
                onClick = viewModel::parse,
                enabled = !state.parsing,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.parsing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp).padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                    Text(stringResource(R.string.fetching_label))
                } else {
                    Text(if (state.parsed) stringResource(R.string.refetch_button) else stringResource(R.string.fetch_details_button))
                }
            }

            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text(stringResource(R.string.job_title_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.company,
                onValueChange = viewModel::onCompanyChange,
                label = { Text(stringResource(R.string.company_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text(stringResource(R.string.job_description_label)) },
                minLines = 8,
                modifier = Modifier.fillMaxWidth()
            )

            if (state.smartCleaning) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("AI is cleaning job description...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            state.saveError?.let { error ->
                ErrorCard(
                    title = "Save Error",
                    error = error,
                    onDismiss = { /* Auto-dismissed on retry */ }
                )
            }

            SectionHeader(stringResource(R.string.generate_after_saving_label))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GenerateChoiceChip("resume", stringResource(R.string.resume_label), state, viewModel)
                GenerateChoiceChip("cover", stringResource(R.string.cover_letter_label), state, viewModel)
                GenerateChoiceChip("both", stringResource(R.string.both_label), state, viewModel)
            }

            Button(
                onClick = { viewModel.save(state.generateChoice) },
                enabled = !state.saving && !state.parsing,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp).padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                }
                val labelRes = if (state.jobId != null) R.string.finalize_generate_button else R.string.save_generate_button
                Text(if (state.saving) stringResource(R.string.saving_label) else stringResource(labelRes))
            }

            OutlinedButton(
                onClick = { viewModel.save(null) },
                enabled = !state.saving && !state.parsing,
                modifier = Modifier.fillMaxWidth()
            ) {
                val labelRes = if (state.jobId != null) R.string.finalize_without_generating_button else R.string.save_without_generating_button
                Text(stringResource(labelRes))
            }

            if (state.parsed) {
                Text(
                    stringResource(R.string.generation_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenerateChoiceChip(
    value: String,
    label: String,
    state: AddJobViewModel.UiState,
    viewModel: AddJobViewModel
) {
    FilterChip(
        selected = state.generateChoice == value,
        onClick = {
            viewModel.setGenerateChoice(if (state.generateChoice == value) null else value)
        },
        label = { Text(label) }
    )
}

// Open in an actual browser app, never the site's own app (e.g. Indeed), so the
// user lands on the page instead of being bounced into the native app.
private fun openInExternalBrowser(context: android.content.Context, url: String) {
    val uri = runCatching { android.net.Uri.parse(url) }.getOrNull() ?: return
    val browsers = listOf(
        "com.android.chrome",
        "org.mozilla.firefox",
        "com.sec.android.app.sbrowser",
        "com.android.browser"
    )
    for (pkg in browsers) {
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .setPackage(pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            return
        }
    }
    context.startActivity(
        Intent.createChooser(Intent(Intent.ACTION_VIEW, uri), "Open with")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

@Composable
private fun JobWebViewDialog(
    url: String,
    onCancel: () -> Unit,
    onUsePage: (String?) -> Unit
) {
    val webView = remember { mutableStateOf<WebView?>(null) }
    val hint = remember { mutableStateOf<String?>(null) }
    val challengeMarkers = listOf(
        "just a moment",
        "additional verification required",
        "attention required",
        "checking your browser",
        "verify you are human",
        "cf-chl"
    )

    val webviewDoneHint = stringResource(R.string.webview_done_hint)
    val webviewVerificationHint = stringResource(R.string.webview_verification_hint)

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel_button)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        hint.value = null
                        webView.value?.reload()
                    }) { Text(stringResource(R.string.refresh_button)) }
                    TextButton(onClick = {
                        val wv = webView.value ?: return@TextButton
                        hint.value = null
                        readWebViewHtml(wv) { title, html ->
                            val lowerTitle = title.lowercase()
                            when {
                                title.isBlank() || html.isBlank() ->
                                    hint.value = webviewDoneHint
                                challengeMarkers.any { lowerTitle.contains(it) || html.contains(it, ignoreCase = true) } ->
                                    hint.value = webviewVerificationHint
                                else -> onUsePage(html)
                            }
                        }
                    }) { Text(stringResource(R.string.done_button)) }
                }
            }
            Text(
                stringResource(R.string.webview_verification_note),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            hint.value?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        webViewClient = object : WebViewClient() {}
                        webView.value = this
                        loadUrl(if (url.isBlank()) "about:blank" else url)
                    }
                },
                onRelease = { it.destroy() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Reads the current page's title + HTML from a WebView, retrying a few times
 * while the page is still responding. Handles evaluateJavascript's JSON
 * encoding (and the extra string-wrapping layer if JS returns a string).
 */
private fun readWebViewHtml(
    webView: WebView,
    attemptsLeft: Int = 5,
    onResult: (title: String, html: String) -> Unit
) {
    val js = "(function(){return {t:document.title||'',h:document.documentElement?document.documentElement.outerHTML:''};})()"
    webView.evaluateJavascript(js) { raw ->
        val obj = runCatching { JSONTokener(raw).nextValue() }.getOrNull()
        val json = when (obj) {
            is JSONObject -> obj
            is String -> runCatching { JSONTokener(obj).nextValue() as? JSONObject }.getOrNull()
            else -> null
        }
        val title = json?.optString("t").orEmpty()
        val html = json?.optString("h").orEmpty()
        if ((title.isBlank() || html.isBlank()) && attemptsLeft > 1) {
            webView.postDelayed({ readWebViewHtml(webView, attemptsLeft - 1, onResult) }, 600)
        } else {
            onResult(title, html)
        }
    }
}
