package com.example.jobsearch.document

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.graphics.createBitmap
import com.example.jobsearch.data.CheatSheetData
import com.example.jobsearch.data.CoverLetterData
import com.example.jobsearch.data.Job
import com.example.jobsearch.data.ResumeData
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DocumentExporter(private val context: Context) {

    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("document", text))
    }

    fun shareText(title: String, text: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(
            Intent.createChooser(sendIntent, "Share $title")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun saveDocumentUri(uri: Uri, text: String): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
        }
        true
    }.getOrDefault(false)

    /**
     * Writes plain text as a multi-page PDF using PDFBox.
     * Uses PdfLayoutEngine for line wrapping and text sanitization.
     */
    fun writePdf(
        uri: Uri,
        text: String,
        resumeLayout: Boolean = false,
        coverLetterLayout: Boolean = false,
        cheatSheetLayout: Boolean = false,
        date: String = "",
        companyName: String = ""
    ): Boolean = runCatching {
        PDFBoxResourceLoader.init(context.applicationContext)
        val out = context.contentResolver.openOutputStream(uri) ?: return false
        out.use { writePdfToStream(it, text, resumeLayout, coverLetterLayout, cheatSheetLayout, date, companyName) }
        true
    }.getOrDefault(false)

    /**
     * Renders the same layout into one bitmap per page.
     */
    fun renderDocumentPages(
        text: String,
        resumeLayout: Boolean = false,
        coverLetterLayout: Boolean = false,
        cheatSheetLayout: Boolean = false,
        date: String = "",
        companyName: String = ""
    ): List<Bitmap>? = runCatching {
        PDFBoxResourceLoader.init(context.applicationContext)
        val file = File(context.cacheDir, "preview_${System.nanoTime()}.pdf")
        try {
            file.outputStream().use { writePdfToStream(it, text, resumeLayout, coverLetterLayout, cheatSheetLayout, date, companyName) }
            renderPdfFile(file)
        } finally {
            file.delete()
        }
    }.getOrNull()

    private fun writePdfToStream(
        out: OutputStream,
        text: String,
        resumeLayout: Boolean,
        coverLetterLayout: Boolean,
        cheatSheetLayout: Boolean,
        date: String,
        companyName: String
    ) {
        val font = DocumentStyle.font
        val boldFont = DocumentStyle.boldFont
        val italicFont = DocumentStyle.italicFont
        val fontSize = DocumentStyle.fontSize
        val headerFontSize = DocumentStyle.headerFontSize
        val leading = DocumentStyle.leading
        val resumeBodyFontSize = DocumentStyle.resumeBodyFontSize
        val resumeNameFontSize = DocumentStyle.resumeNameFontSize
        val resumeContactFontSize = DocumentStyle.resumeContactFontSize
        val resumeBodyLeading = DocumentStyle.resumeBodyLeading
        val resumeHeaderLeading = DocumentStyle.resumeHeaderLeading
        val resumeNameLeading = DocumentStyle.resumeNameLeading
        val resumeContactLeading = DocumentStyle.resumeContactLeading
        val leftMargin = DocumentStyle.leftMargin
        val rightMargin = DocumentStyle.rightMargin
        val topMargin = DocumentStyle.topMargin
        val bottomMargin = DocumentStyle.bottomMargin
        val pageWidth = PDRectangle.A4.width
        val pageHeight = PDRectangle.A4.height
        val maxWidth = pageWidth - leftMargin - rightMargin
        val minY = bottomMargin
        val startY = pageHeight - topMargin

        val doc = PDDocument()
        try {
            var cs: PDPageContentStream? = null
            var y = startY
            var currentPage = 0

            fun newPage(): PDPageContentStream {
                cs?.let {
                    it.endText()
                    it.close()
                }
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                val stream = PDPageContentStream(doc, page)
                stream.beginText()
                stream.setFont(font, fontSize)
                stream.setLeading(leading)
                cs = stream
                y = startY
                currentPage++
                return stream
            }

            cs = newPage()

            fun ensureSpace(lead: Float) {
                if (y - lead < minY) {
                    cs = newPage()
                }
            }

            fun renderHorizontalDivider() {
                cs?.let {
                    it.endText()
                    it.setLineWidth(0.75f)
                    val lineY = y + DocumentStyle.resumeDividerYOffset 
                    it.moveTo(leftMargin, lineY)
                    it.lineTo(pageWidth - rightMargin, lineY)
                    it.stroke()
                    it.beginText()
                }
            }

            fun renderLine(
                line: String,
                f: PDType1Font,
                size: Float,
                lead: Float,
                centered: Boolean = false,
                x: Float = leftMargin,
                w: Float = maxWidth
            ) {
                val clean = PdfLayoutEngine.sanitizeForPdf(line)
                if (clean.isBlank()) {
                    y -= lead
                    return
                }
                val wrapped = PdfLayoutEngine.wrapForPdf(clean, f, size, w)
                for (part in wrapped) {
                    ensureSpace(lead)
                    val textWidth = f.getStringWidth(part) / 1000f * size
                    val actualX = if (centered) (pageWidth - textWidth) / 2f else x
                    cs?.setFont(f, size)
                    cs?.setTextMatrix(1.0, 0.0, 0.0, 1.0, actualX.toDouble(), y.toDouble())
                    cs?.showText(part)
                    y -= lead
                }
            }

            fun measureHeight(text: String, f: PDType1Font, size: Float, lead: Float, w: Float = maxWidth): Float {
                val clean = PdfLayoutEngine.sanitizeForPdf(text)
                if (clean.isBlank()) return lead
                val wrappedCount = PdfLayoutEngine.wrapForPdf(clean, f, size, w).size
                return wrappedCount * lead
            }

            val resumeData = if (resumeLayout) ResumeData.fromJson(text) else null
            val legacyResumeData = if (resumeLayout && resumeData == null) ResumeData.fromText(text) else null
            val finalResumeData = if (resumeData?.isSubstantial() == true) {
                resumeData
            } else if (legacyResumeData?.isSubstantial() == true) {
                legacyResumeData
            } else {
                null
            }

            val coverLetterData = if (coverLetterLayout) CoverLetterData.fromJson(text) else null
            val legacyCoverLetterData = if (coverLetterLayout && coverLetterData == null) CoverLetterData.fromText(text) else null
            val finalCoverLetterData = if (coverLetterData?.isSubstantial() == true) {
                coverLetterData
            } else if (legacyCoverLetterData?.isSubstantial() == true) {
                legacyCoverLetterData
            } else {
                null
            }

            val cheatSheetData = if (cheatSheetLayout) (CheatSheetData.fromJson(text) ?: CheatSheetData.fromText(text)) else null
            val finalCheatSheetData = if (cheatSheetData?.isSubstantial() == true) cheatSheetData else null

            if (finalResumeData != null) {
                if (finalResumeData.name.isNotBlank()) {
                    renderLine(finalResumeData.name, boldFont, resumeNameFontSize, resumeNameLeading, true)
                }
                if (finalResumeData.contact.isNotBlank()) {
                    renderLine(finalResumeData.contact, font, resumeContactFontSize, resumeContactLeading, true)
                }
                y -= 8f

                if (finalResumeData.summary.isNotBlank()) {
                    renderLine("SUMMARY", boldFont, headerFontSize, resumeHeaderLeading, false)
                    renderHorizontalDivider()
                    y -= 2f
                    renderLine(finalResumeData.summary, font, resumeBodyFontSize, resumeBodyLeading, false)
                    y -= 6f
                }

                if (finalResumeData.skills.isNotEmpty()) {
                    renderLine("SKILLS", boldFont, headerFontSize, resumeHeaderLeading, false)
                    renderHorizontalDivider()
                    y -= 2f
                    
                    val cats = finalResumeData.skills
                    val gap = DocumentStyle.resumeSkillsGap
                    val colWidth = (maxWidth - gap) / 2f
                    
                    val catHeights = cats.map { cat ->
                        var h = 0f
                        if (cat.name.isNotBlank()) h += resumeBodyLeading
                        h += measureHeight("- " + cat.skills.joinToString(", "), font, resumeBodyFontSize, resumeBodyLeading, colWidth)
                        h + 4f
                    }
                    
                    val totalH = catHeights.sum()
                    val estimatedSkillsHeight = (totalH / 2f) + 20f 
                    if (y - estimatedSkillsHeight < minY) {
                        cs = newPage()
                    }

                    val leftCols = mutableListOf<Int>()
                    val rightCols = mutableListOf<Int>()
                    
                    var currentH = 0f
                    for (i in cats.indices) {
                        if (currentH < totalH / 2f || leftCols.isEmpty()) {
                            leftCols.add(i)
                            currentH += catHeights[i]
                        } else {
                            rightCols.add(i)
                        }
                    }

                    val initialY = y
                    
                    for (idx in leftCols) {
                        val cat = cats[idx]
                        if (cat.name.isNotBlank()) {
                            renderLine(cat.name, boldFont, resumeBodyFontSize, resumeBodyLeading, false, leftMargin, colWidth)
                        }
                        renderLine("- " + cat.skills.joinToString(", "), font, resumeBodyFontSize, resumeBodyLeading, false, leftMargin, colWidth)
                        y -= 4f
                    }
                    val afterLeftY = y
                    
                    y = initialY
                    val rightX = leftMargin + (maxWidth / 2f) + (gap / 2f)
                    for (idx in rightCols) {
                        val cat = cats[idx]
                        if (cat.name.isNotBlank()) {
                            renderLine(cat.name, boldFont, resumeBodyFontSize, resumeBodyLeading, false, rightX, colWidth)
                        }
                        renderLine("- " + cat.skills.joinToString(", "), font, resumeBodyFontSize, resumeBodyLeading, false, rightX, colWidth)
                        y -= 4f
                    }
                    
                    y = minOf(afterLeftY, y) - DocumentStyle.resumeSectionSpacing
                }

                if (finalResumeData.experience.isNotEmpty()) {
                    renderLine("EXPERIENCE", boldFont, headerFontSize, resumeHeaderLeading, false)
                    renderHorizontalDivider()
                    y -= 2f
                    for (exp in finalResumeData.experience) {
                        val renderedBullets = exp.bullets.take(5)
                        var blockHeight = resumeBodyLeading
                        if (exp.company.isNotBlank() || exp.location.isNotBlank() || exp.dates.isNotBlank()) {
                             blockHeight += resumeBodyLeading
                        }
                        for (bullet in renderedBullets) {
                            blockHeight += measureHeight("- $bullet", font, resumeBodyFontSize, resumeBodyLeading)
                        }
                        blockHeight += 8f

                        if (y - blockHeight < minY) {
                            cs = newPage()
                        }

                        renderLine(exp.title, italicFont, resumeBodyFontSize, resumeBodyLeading, false)
                        
                        val secondLine = mutableListOf<String>()
                        if (exp.company.isNotBlank()) secondLine.add(exp.company)
                        if (exp.location.isNotBlank() || exp.dates.isNotBlank()) {
                            val locDate = listOf(exp.location, exp.dates).filter { it.isNotBlank() }.joinToString(", ")
                            if (locDate.isNotBlank()) secondLine.add(locDate)
                        }
                        
                        if (secondLine.isNotEmpty()) {
                            renderLine(secondLine.joinToString(" | "), italicFont, resumeBodyFontSize, resumeBodyLeading, false)
                        }

                        for (bullet in renderedBullets) {
                            renderLine("- $bullet", font, resumeBodyFontSize, resumeBodyLeading, false)
                        }
                        y -= DocumentStyle.resumeSectionSpacing
                    }
                }

                if (finalResumeData.education.isNotEmpty()) {
                    renderLine("EDUCATION", boldFont, headerFontSize, resumeHeaderLeading, false)
                    renderHorizontalDivider()
                    y += 4f
                    for (edu in finalResumeData.education) {
                        renderLine(edu.school, boldFont, resumeBodyFontSize, resumeBodyLeading, false)
                        val secondLine = mutableListOf<String>()
                        if (edu.degree.isNotBlank()) secondLine.add(edu.degree)
                        if (edu.dates.isNotBlank()) secondLine.add(edu.dates)
                        if (secondLine.isNotEmpty()) {
                            renderLine(secondLine.joinToString(", "), italicFont, resumeBodyFontSize, resumeBodyLeading, false)
                        }
                        y -= 4f
                    }
                    y -= 2f
                }

                if (finalResumeData.projects.isNotEmpty()) {
                    renderLine("PROJECTS", boldFont, headerFontSize, resumeHeaderLeading, false)
                    renderHorizontalDivider()
                    y -= 2f
                    for (proj in finalResumeData.projects) {
                        renderLine(proj.name, boldFont, resumeBodyFontSize, resumeBodyLeading, false)
                        for (bullet in proj.bullets) {
                            renderLine("- $bullet", font, resumeBodyFontSize, resumeBodyLeading, false)
                        }
                        y -= 4f
                    }
                }
            } else if (finalCoverLetterData != null) {
                if (finalCoverLetterData.name.isNotBlank()) {
                    renderLine(finalCoverLetterData.name, boldFont, resumeNameFontSize, resumeNameLeading, true)
                }
                if (finalCoverLetterData.contact.isNotBlank()) {
                    renderLine(finalCoverLetterData.contact, font, resumeContactFontSize, resumeContactLeading, true)
                }
                y -= DocumentStyle.coverLetterHeaderGap

                if (date.isNotBlank()) {
                    renderLine(date, font, fontSize, leading, false)
                    y -= DocumentStyle.coverLetterDateGap
                }
                
                if (finalCoverLetterData.companyBlock.isNotBlank()) {
                    for (line in finalCoverLetterData.companyBlock.split("\n")) {
                        renderLine(line, font, fontSize, leading, false)
                    }
                    y -= DocumentStyle.coverLetterDateGap
                }
                
                renderLine(finalCoverLetterData.salutation, font, fontSize, leading, false)
                y -= DocumentStyle.coverLetterClosingGap
                
                for (p in finalCoverLetterData.paragraphs) {
                    renderLine(p, font, fontSize, leading, false)
                    y -= DocumentStyle.coverLetterParagraphGap
                }
                
                y -= DocumentStyle.coverLetterClosingGap
                renderLine(finalCoverLetterData.closing, font, fontSize, leading, false)
                y -= DocumentStyle.coverLetterParagraphGap
                if (finalCoverLetterData.signature.isNotBlank()) {
                    renderLine(finalCoverLetterData.signature, font, fontSize, leading, false)
                }
            } else if (finalCheatSheetData != null) {
                val effFontSize = 9.5f
                val effHeaderSize = 11.5f
                val effLeading = 12.5f

                renderLine("${companyName.ifBlank { "Company" }} Research", boldFont, resumeNameFontSize, resumeNameLeading, true)
                y -= 12f

                if (finalCheatSheetData.aboutCompany.isNotBlank()) {
                    renderLine("ABOUT THE COMPANY", boldFont, effHeaderSize, effLeading, false)
                    renderHorizontalDivider()
                    y -= 4f
                    renderLine(finalCheatSheetData.aboutCompany, font, effFontSize, effLeading, false)
                    y -= 8f
                }

                if (finalCheatSheetData.relevantSkills.isNotEmpty()) {
                    renderLine("RELEVANT SKILLS", boldFont, effHeaderSize, effLeading, false)
                    renderHorizontalDivider()
                    y -= 4f
                    renderLine(finalCheatSheetData.relevantSkills.joinToString(", "), font, effFontSize, effLeading, false)
                    y -= 8f
                }
                
                if (finalCheatSheetData.keyHighlights.isNotEmpty()) {
                    renderLine("KEY HIGHLIGHTS", boldFont, effHeaderSize, effLeading, false)
                    renderHorizontalDivider()
                    y -= 4f
                    for (h in finalCheatSheetData.keyHighlights) {
                        renderLine("• $h", font, effFontSize, effLeading, false)
                        y -= 2f
                    }
                    y -= 8f
                }
                
                if (finalCheatSheetData.toughQuestions.isNotEmpty()) {
                    renderLine("Q&A", boldFont, effHeaderSize, effLeading, false)
                    renderHorizontalDivider()
                    y -= 4f
                    for (tq in finalCheatSheetData.toughQuestions) {
                        renderLine("Q: ${tq.question}", boldFont, effFontSize, effLeading, false)
                        if (tq.strategy.isNotBlank()) {
                            renderLine("STRATEGY: ${tq.strategy}", italicFont, effFontSize, effLeading, false)
                        }
                        if (tq.exampleAnswer.isNotBlank()) {
                            renderLine("EXAMPLE ANSWER:\n${tq.exampleAnswer}", font, effFontSize, effLeading, false)
                        }
                        y -= 6f
                    }
                }

                if (finalCheatSheetData.notes.isNotBlank()) {
                    renderLine("NOTES", boldFont, effHeaderSize, effLeading, false)
                    renderHorizontalDivider()
                    y -= 4f
                    for (line in finalCheatSheetData.notes.replace("\r\n", "\n").split("\n")) {
                        renderLine(line, font, effFontSize, effLeading, false)
                    }
                }
            } else {
                for (line in text.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
                    renderLine(PdfLayoutEngine.normalizeBullet(line), font, fontSize, leading, false)
                }
            }

            cs?.let {
                it.endText()
                it.close()
            }
            doc.save(out)
        } finally {
            doc.close()
        }
    }

    private fun renderPdfFile(file: File): List<Bitmap> {
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fd)
        try {
            val pages = mutableListOf<Bitmap>()
            val scale = previewScale()
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                try {
                    val w = (page.width * scale).toInt().coerceAtLeast(1)
                    val h = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = createBitmap(w, h)
                    bitmap.eraseColor(Color.WHITE)
                    val matrix = Matrix().apply { setScale(scale, scale) }
                    page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    pages.add(bitmap)
                } finally {
                    page.close()
                }
            }
            return pages
        } finally {
            renderer.close()
            fd.close()
        }
    }

    private fun previewScale(): Float {
        val targetWidth = context.resources.displayMetrics.widthPixels
            .coerceIn(PDRectangle.A4.width.toInt(), 1240)
        return targetWidth / PDRectangle.A4.width
    }

    fun csvFor(jobs: List<Job>): String {
        val sb = StringBuilder()
        sb.append("Date,Title,Company,Status,URL,Tags,Has Resume,Has Cover Letter,Has Initial Email,Has Cheat Sheet,Has Follow-up\n")
        for (job in jobs) {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(Date(job.dateAdded))
            sb.append(csvCell(date)).append(',')
                .append(csvCell(job.title)).append(',')
                .append(csvCell(job.company)).append(',')
                .append(csvCell(job.status)).append(',')
                .append(csvCell(job.url)).append(',')
                .append(csvCell(job.tags)).append(',')
                .append(if (job.hasResume) "yes" else "no").append(',')
                .append(if (job.hasCoverLetter) "yes" else "no").append(',')
                .append(if (job.hasInitialEmail) "yes" else "no").append(',')
                .append(if (job.hasCheatSheet) "yes" else "no").append(',')
                .append(if (job.hasFollowUpEmail) "yes" else "no")
                .append('\n')
        }
        return sb.toString()
    }

    private fun csvCell(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
