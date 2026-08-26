package com.example.jobsearch.parsing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Entities
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

data class ParsedJob(
    val title: String,
    val company: String,
    val description: String,
    val url: String
)

/**
 * Provides fully-rendered HTML for pages that block plain HTTP fetches
 * (e.g. sites behind a JavaScript anti-bot challenge). Returns null if
 * rendering failed or timed out.
 */
fun interface HtmlSource {
    suspend fun fetch(url: String): String?
}

class BlockedException(statusCode: Int) : IOException(
    "The site blocked automated access (HTTP $statusCode)."
)

class JobParser(private val htmlSource: HtmlSource? = null) {

    suspend fun parse(rawUrl: String): ParsedJob = withContext(Dispatchers.IO) {
        val url = normalizeUrl(rawUrl)
            ?: throw IllegalArgumentException("That doesn't look like a URL. Make sure it starts with http:// or https://")

        val doc = try {
            fetch(url)
        } catch (e: BlockedException) {
            val html = htmlSource?.fetch(url)
            if (!html.isNullOrBlank()) {
                return@withContext parseFromHtml(url, html)
            }
            throw IOException(
                "This site blocks automated access. Try \"Fetch in built-in browser\" below — it opens the page in a real browser you can complete the verification in. Or open it in your phone's browser and paste the job details manually."
            )
        }

        if (isChallengePage(doc)) {
            val html = htmlSource?.fetch(url)
            if (!html.isNullOrBlank()) {
                return@withContext parseFromHtml(url, html)
            }
        }

        fromDoc(url, doc)
    }

    fun parseFromHtml(rawUrl: String, html: String): ParsedJob {
        val url = normalizeUrl(rawUrl)
            ?: throw IllegalArgumentException("That doesn't look like a URL.")
        val doc = Jsoup.parse(html, url)
        return fromDoc(url, doc)
    }

    private fun fromDoc(url: String, doc: Document): ParsedJob {
        val jsonLd = extractJsonLd(doc)

        val ogTitle = metaContent(doc, "property", "og:title")
            ?: metaContent(doc, "name", "twitter:title")

        val title = jsonLd.title
            ?: ogTitle
            ?: doc.selectFirst("h1")?.text()?.trim()
            ?: doc.title().trim()

        val company = jsonLd.company
            ?: metaContent(doc, "property", "og:site_name")
            ?: metaContent(doc, "name", "og:site_name")
            ?: companyFromTitle(ogTitle)
            ?: hostFromUrl(url)

        val description = cleanDescription(
            jsonLd.description
                ?: extractDescription(doc)
                ?: metaContent(doc, "property", "og:description")
                ?: metaContent(doc, "name", "twitter:description")
        )

        return ParsedJob(
            title = title.cleanText().takeIf { it.isNotBlank() } ?: "Untitled job",
            company = company?.cleanText().orEmpty(),
            description = description,
            url = url
        )
    }

    private fun isChallengePage(doc: Document): Boolean {
        val title = doc.title().lowercase()
        val body = doc.body().wholeText().lowercase()
        val titleMarkers = listOf(
            "just a moment", "additional verification required",
            "attention required", "challenge"
        )
        val bodyMarkers = listOf(
            "just a moment", "additional verification required",
            "cf-chl", "checking your browser", "verify you are human"
        )
        return titleMarkers.any { title.contains(it) } ||
                bodyMarkers.any { body.contains(it) } ||
                doc.location().contains("cf_chl", ignoreCase = true)
    }

    private fun normalizeUrl(raw: String): String? {
        var u = raw.trim()
        if (u.startsWith("http://")) {
            u = "https://" + u.removePrefix("http://")
        } else if (!u.startsWith("https://")) {
            u = "https://$u"
        }
        val uri = runCatching { java.net.URI(u) }.getOrNull() ?: return null
        if (uri.host.isNullOrBlank()) return null
        return stripTrackingParams(u)
    }

    // Tracking/analytics params carry no job info (e.g. Indeed app links append
    // "from=appshareandroid" vs "from=shareddesktop_copy"). Strip them so app
    // and browser links normalize to the same job URL.
    private val trackingParams = setOf(
        "from", "tk", "trk", "ref", "spm",
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "fbclid", "gclid", "mc_cid", "mc_eid", "igshid", "igsh"
    )

    private fun stripTrackingParams(url: String): String {
        val split = url.split('?', limit = 2)
        if (split.size < 2) return url
        val kept = split[1].split('&')
            .filter { param ->
                val key = param.substringBefore('=').trim()
                key.isNotBlank() && key !in trackingParams
            }
        return if (kept.isEmpty()) split[0] else "${split[0]}?${kept.joinToString("&")}"
    }

    private fun fetch(url: String): Document {
        val mobileUa = "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
        val desktopUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

        var lastError: Exception? = null
        for (ua in listOf(mobileUa, desktopUa)) {
            try {
                return connect(url, ua)
            } catch (e: HttpStatusException) {
                lastError = e
                if (e.statusCode == 403 || e.statusCode == 429 || e.statusCode == 999 ||
                    e.statusCode == 401 || e.statusCode == 503) {
                    continue
                }
                throw IOException("The site returned an error (HTTP ${e.statusCode}). It may be blocking automated requests, or the link may not point to a job posting.")
            } catch (e: UnknownHostException) {
                throw IOException("Could not reach that website. Check the URL or your internet connection.")
            } catch (e: ConnectException) {
                throw IOException("Could not connect to the site. Check the URL or try again.")
            } catch (e: SocketTimeoutException) {
                throw IOException("The site took too long to respond. Try again, ideally on Wi-Fi.")
            } catch (e: SSLException) {
                lastError = e
            } catch (e: IOException) {
                throw IOException(e.message ?: "Could not fetch the page. Check the URL.")
            }
        }
        val code = (lastError as? HttpStatusException)?.statusCode
        if (code != null) throw BlockedException(code)
        throw IOException(
            "The site blocked automated access. Try pasting the link again."
        )
    }

    private fun connect(url: String, ua: String): Document =
        Jsoup.connect(url)
            .userAgent(ua)
            .referrer("https://www.google.com/")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .timeout(25_000)
            .followRedirects(true)
            .maxBodySize(6 * 1024 * 1024)
            .get()

    private fun metaContent(doc: Document, key: String, value: String): String? =
        doc.selectFirst("meta[$key='$value']")?.attr("content")?.trim()?.takeIf { it.isNotBlank() }

    private fun companyFromTitle(ogTitle: String?): String? {
        if (ogTitle.isNullOrBlank()) return null
        val parts = ogTitle.split("|", " - ", " – ", " — ")
        if (parts.size < 2) return null
        return parts.last().trim().ifBlank { null }
    }

    private fun extractJsonLd(doc: Document): JsonLd {
        val scripts = doc.select("script[type='application/ld+json']")
        for (script in scripts) {
            val json = script.data()
            if (!json.contains("JobPosting", ignoreCase = true)) continue
            return parseJsonLd(json)
        }
        return JsonLd(null, null, null)
    }

    private fun parseJsonLd(json: String): JsonLd {
        var title: String? = null
        var company: String? = null
        var description: String? = null

        val titleMatch = Regex("\"title\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(json)
        if (titleMatch != null) title = unescape(titleMatch.groupValues[1])

        val orgMatch = Regex("\"hiringOrganization\"\\s*:\\s*\\{[^}]*?\"name\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(json)
            ?: Regex("\"name\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*,\\s*\"@type\"\\s*:\\s*\"Organization\"").find(json)
        if (orgMatch != null) company = unescape(orgMatch.groupValues[1])

        val descMatch = Regex("\"description\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(json)
        if (descMatch != null) description = unescape(descMatch.groupValues[1]).let { stripHtml(it) }

        return JsonLd(title, company, description)
    }

    private fun extractDescription(doc: Document): String? {
        doc.select("script, style, nav, footer, header, noscript").remove()

        val selectors = listOf(
            "[class*='description' i]",
            "[class*='job-description' i]",
            "[itemprop='description']",
            "[id*='description' i]",
            "main", "article"
        )
        for (selector in selectors) {
            val el = doc.selectFirst(selector) ?: continue
            val text = el.wholeText().cleanText()
            if (text.length > 200) return text
        }

        val body = doc.body()
        val text = body.wholeText().cleanText()
        return if (text.length > 200) text else null
    }

    private fun hostFromUrl(url: String): String? = runCatching {
        val host = java.net.URI(url).host ?: return null
        val skip = setOf(
            "www", "jobs", "careers", "career", "hiring", "apply", "boards",
            "us", "uk", "de", "fr", "ca", "au", "ie", "nl", "es", "it", "br", "in"
        )
        val labels = host.removePrefix("www.").split(".").toMutableList()
        while (labels.size > 1 && (labels.first() in skip || labels.first().length <= 3)) {
            labels.removeAt(0)
        }
        labels.firstOrNull()?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun stripHtml(html: String): String =
        Jsoup.parse(Entities.unescape(html)).text()

    private fun unescape(s: String): String {
        val result = Regex("\\\\u([0-9a-fA-F]{4})").replace(s) { m ->
            m.groupValues[1].toInt(16).toChar().toString()
        }
        return result
            .replace("\\\"", "\"")
            .replace("\\/", "/")
            .replace("\\n", " ")
            .replace("\\t", " ")
            .replace("\\r", " ")
    }

    // Decodes JSON-style escapes (e.g. "\u003Cb>" -> "<b>") that WebView
    // evaluateJavascript and embedded JSON-LD leave behind.
    private fun decodeEscapes(s: String): String =
        s.replace("\\\\", "\\")
            .replace("\\\"", "\"")
            .replace("\\/", "/")
            .replace("\\n", " ")
            .replace("\\t", " ")
            .replace("\\r", " ")
            .replace(Regex("\\\\u([0-9a-fA-F]{4})")) { m ->
                m.groupValues[1].toInt(16).toChar().toString()
            }

    private fun cleanDescription(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val decoded = decodeEscapes(text)
        return Jsoup.parse(decoded).text().cleanText()
    }

    fun trimFluff(text: String): String {
        val trimmedIntro = trimIntro(text)
        return trimOutro(trimmedIntro)
    }

    private fun trimIntro(text: String): String {
        val lower = text.lowercase()
        // Content markers that signal the start of actual job info
        val contentMarkers = listOf("opportunity", "role", "position", "summary", "responsibilities", "requirements")
        val firstRealContent = contentMarkers.asSequence()
            .map { lower.indexOf(it) }
            .filter { it != -1 }
            .minOrNull() ?: return text

        // Don't trim if the "content" is way too far down, might be a false positive
        if (firstRealContent > text.length * 0.5) return text

        val introMarkers = listOf(
            "creativity is our", "at ", "dedicated to building", "our mission",
            "we believe", "is a leading", "is the world's", "founded in"
        )

        val hasIntroMarker = introMarkers.any { marker ->
            val idx = lower.indexOf(marker)
            idx != -1 && idx < firstRealContent
        }

        return if (hasIntroMarker) text.substring(firstRealContent).trim() else text
    }

    private fun trimOutro(text: String): String {
        val lower = text.lowercase()
        val fluffMarkers = listOf(
            "benefits", "what we offer", "perks", "our values",
            "about the company", "about us", "equal opportunity employer",
            "diversity and inclusion", "physical requirements", "work environment",
            "why join us", "our culture", "who we are", "compensation & benefits",
            "we are an equal opportunity", "life at ", "equal opportunity",
            "work authorization", "background check", "visa sponsorship",
            "employment eligibility", "how to apply", "how we work", "eeo",
            "accessibility commitment", "visit us at", "join our team",
            "our purpose", "company culture"
        )

        var earliest = text.length
        for (marker in fluffMarkers) {
            // Flexible matching: use lowercase and trim colons
            val cleanMarker = marker.lowercase().trimEnd(':').trim()
            val index = lower.indexOf(cleanMarker)

            // Refine threshold: keep at least first 20%
            if (index != -1 && index < earliest && index > text.length * 0.2) {
                earliest = index
            }
        }

        return if (earliest < text.length) {
            text.substring(0, earliest).trim().removeSuffix(":").trim()
        } else {
            text
        }
    }

    private fun String.cleanText(): String =
        decodeEscapes(this).replace(Regex("\\s+"), " ").trim()

    private data class JsonLd(val title: String?, val company: String?, val description: String?)
}
