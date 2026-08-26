package com.example.jobsearch.parsing

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Loads a page in a real (hidden) WebView so JavaScript anti-bot challenges
 * (Cloudflare etc.) can resolve, then returns the final rendered HTML.
 * Returns null if the page couldn't be loaded or the challenge never cleared.
 */
class HtmlRenderer(context: Context) : HtmlSource {

    private val appContext = context.applicationContext

    private val challengeMarkers = listOf(
        "just a moment",
        "additional verification required",
        "attention required",
        "checking your browser",
        "verify you are human",
        "cf-chl"
    )

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun fetch(url: String): String? = withContext(Dispatchers.Main.immediate) {
        if (Looper.myLooper() == null) return@withContext null

        val deferred = CompletableDeferred<String?>()
        var webView: WebView? = null
        var alive = true
        try {
            val view = WebView(appContext)
            webView = view
            view.settings.javaScriptEnabled = true
            view.settings.domStorageEnabled = true
            view.settings.javaScriptCanOpenWindowsAutomatically = true
            view.webViewClient = object : WebViewClient() {}
            view.loadUrl(url)
            // Poll independently of onPageFinished (which fires early during
            // redirects with an empty intermediate document). Only complete
            // once the page has real content and is not a challenge wall.
            poll(view, deferred, alive, attempts = 62)
            withTimeoutOrNull(50_000) { deferred.await() }
        } catch (e: Exception) {
            null
        } finally {
            alive = false
            webView?.stopLoading()
            webView?.destroy()
        }
    }

    private fun poll(view: WebView, deferred: CompletableDeferred<String?>, alive: Boolean, attempts: Int) {
        if (!alive || deferred.isCompleted) return
        if (attempts <= 0) {
            deferred.complete(null)
            return
        }
        val js = "(function(){var b=document.body;" +
            "return {t:document.title||''," +
            "b:b?b.innerText:''," +
            "h:document.documentElement?document.documentElement.outerHTML:''};})()"
        runCatching {
            view.evaluateJavascript(js) { raw ->
                if (!alive || deferred.isCompleted) return@evaluateJavascript
                val page = parsePageState(raw)
                if (page == null || isChallenged(page) || !hasContent(page)) {
                    view.postDelayed({ poll(view, deferred, alive, attempts - 1) }, 800)
                } else {
                    deferred.complete(page.html)
                }
            }
        }
    }

    private fun isChallenged(page: PageState): Boolean {
        val title = page.title.lowercase()
        val body = page.bodyText.lowercase()
        return challengeMarkers.any { title.contains(it) || body.contains(it) }
    }

    private fun hasContent(page: PageState): Boolean =
        page.bodyText.isNotBlank() || page.html.length > 1_000

    private fun parsePageState(raw: String?): PageState? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return runCatching {
            val decoded = JSONTokener(raw).nextValue()
            val obj = when (decoded) {
                is JSONObject -> decoded
                is String -> JSONTokener(decoded).nextValue() as? JSONObject
                else -> null
            } ?: return null
            PageState(
                title = obj.optString("t"),
                bodyText = obj.optString("b"),
                html = obj.optString("h")
            )
        }.getOrNull()
    }

    private data class PageState(val title: String, val bodyText: String, val html: String)
}
