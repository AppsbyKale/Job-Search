package com.example.jobsearch.document

import com.tom_roush.pdfbox.pdmodel.font.PDType1Font

/**
 * Utility layout engine for coordinate math, text wrapping, and character sanitization
 * during PDF rendering.
 */
object PdfLayoutEngine {

    fun wrapForPdf(text: String, font: PDType1Font, fontSize: Float, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        val current = StringBuilder()
        for (word in text.split(" ")) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (current.isEmpty() || font.getStringWidth(sanitizeForPdf(candidate)) / 1000f * fontSize <= maxWidth) {
                current.setLength(0)
                current.append(candidate)
            } else {
                lines.add(sanitizeForPdf(current.toString()))
                current.setLength(0)
                current.append(word)
            }
        }
        if (current.isNotEmpty()) lines.add(sanitizeForPdf(current.toString()))
        return lines
    }

    fun sanitizeForPdf(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            val v = c.code
            sb.append(if (v in 32..126 || v in 160..255) c else ' ')
        }
        return sb.toString()
    }

    fun normalizeBullet(line: String): String {
        val t = line.trimStart()
        if (t.startsWith("* ")) return "- " + t.substring(2)
        if (t.startsWith("*")) return "- " + t.substring(1).trimStart()
        return line
    }
}
