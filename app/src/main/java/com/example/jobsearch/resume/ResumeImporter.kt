package com.example.jobsearch.resume

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.zip.ZipInputStream

class ResumeImporter {

    suspend fun parse(context: Context, fileName: String, input: java.io.InputStream): String =
        withContext(Dispatchers.IO) {
            when {
                fileName.endsWith(".txt", ignoreCase = true) -> input.readBytes().toString(Charsets.UTF_8)
                fileName.endsWith(".docx", ignoreCase = true) -> parseDocx(input)
                fileName.endsWith(".pdf", ignoreCase = true) -> parsePdf(context, input)
                else -> throw IllegalArgumentException("Unsupported file type. Use a .txt, .docx or .pdf file.")
            }
        }

    private fun parseDocx(input: java.io.InputStream): String {
        val sb = StringBuilder()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    sb.append(readWordXml(zip))
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return sb.toString().trim().ifEmpty { throw IllegalArgumentException("No text found in document.") }
    }

    private fun readWordXml(zip: ZipInputStream): String {
        val sb = StringBuilder()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(zip, "UTF-8")

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "t", "w:t" -> sb.append(parser.nextText())
                    "p", "w:p", "br", "w:br" -> sb.append("\n")
                }
            }
            event = parser.next()
        }
        return sb.toString()
    }

    private fun parsePdf(context: Context, input: java.io.InputStream): String {
        PDFBoxResourceLoader.init(context.applicationContext)
        val bytes = input.readBytes()
        val text = extractPdfText(bytes.inputStream()).trim()
        if (text.isNotBlank()) return text
        throw IllegalArgumentException(
            "No text could be read from this PDF. If it is a scanned document, " +
                "please import a text-based PDF, or use a .txt or .docx file."
        )
    }

    private fun extractPdfText(input: java.io.InputStream): String = runCatching {
        PDDocument.load(input).use { doc ->
            if (doc.numberOfPages == 0) return ""
            PDFTextStripper().getText(doc)
        }
    }.getOrDefault("")
}
