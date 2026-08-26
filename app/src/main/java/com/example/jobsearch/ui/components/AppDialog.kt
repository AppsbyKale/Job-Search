package com.example.jobsearch.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.jobsearch.R

@Composable
fun AppDialog(
    title: String,
    onDismissRequest: () -> Unit,
    fullScreen: Boolean = false,
    scrollable: Boolean = true,
    onSave: (() -> Unit)? = null,
    saveLabel: String = stringResource(R.string.save_button),
    actions: @Composable RowScope.() -> Unit = {},
    footer: @Composable (ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    fun handleDismiss() {
        visible = false
        onDismissRequest()
    }

    Dialog(
        onDismissRequest = ::handleDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = !fullScreen,
            decorFitsSystemWindows = false
        )
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.9f, animationSpec = tween(200)),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.9f, animationSpec = tween(150))
        ) {
            Surface(
                shape = if (fullScreen) RoundedCornerShape(0.dp) else RoundedCornerShape(28.dp),
                modifier = if (fullScreen) {
                    Modifier
                        .fillMaxSize()
                        .imePadding() // Ensure keyboard doesn't hide content
                        .systemBarsPadding()
                } else {
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .heightIn(max = 600.dp)
                        .navigationBarsPadding()
                        .imePadding()
                },
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = ::handleDismiss) {
                            Text(stringResource(R.string.cancel_button))
                        }
                        
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            actions()
                            if (onSave != null) {
                                Button(
                                    onClick = onSave,
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text(saveLabel)
                                }
                            } else {
                                // Empty spacer to balance the Cancel button if no Save is present
                                Spacer(modifier = Modifier.width(64.dp))
                            }
                        }
                    }

                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // Content
                    val contentModifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .let { 
                            if (scrollable) it.verticalScroll(rememberScrollState()) else it 
                        }
                        .padding(24.dp)

                    Column(
                        modifier = contentModifier,
                        verticalArrangement = if (scrollable) Arrangement.spacedBy(16.dp) else Arrangement.Top,
                        content = content
                    )

                    // Footer
                    if (footer != null) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            content = footer
                        )
                    }
                }
            }
        }
    }
}
