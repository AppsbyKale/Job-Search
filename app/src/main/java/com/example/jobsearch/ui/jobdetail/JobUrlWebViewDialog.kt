package com.example.jobsearch.ui.jobdetail

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.jobsearch.ui.components.AppDialog

@Composable
fun JobUrlWebViewDialog(
    url: String,
    onDismiss: () -> Unit
) {
    AppDialog(
        title = url,
        onDismissRequest = onDismiss,
        fullScreen = true,
        scrollable = false
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
