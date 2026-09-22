package com.example.jobsearch.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.graphics.createBitmap
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class PdfExporter(private val context: Context) {

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
        out.use { 
            writePdfToStream(it, text, resumeLayout, coverLetterLayout, cheatSheetLayout, date, companyName)
            it.flush()
        }
        true
    }.getOrDefault(false)

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
            FileOutputStream(file).use { out ->
                writePdfToStream(out, text, resumeLayout, coverLetterLayout, cheatSheetLayout, date, companyName)
                out.flush()
            }
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

            fun renderLine(
                txt: String,
                f: PDFont,
                sz: Float,
                lead: Float
            ) {
                if (cs == null) newPage()
                val activeCs = cs!!

                val wrapped = PdfLayoutEngine.wrapForPdf(txt, f as PDType1Font, sz, maxWidth)

                for (line in wrapped) {
                    if (y < minY) {
                        newPage()
                        cs!!.setFont(f, sz)
                        cs!!.setLeading(lead)
                        cs!!.newLineAtOffset(leftMargin, y)
                    }
                    if (currentPage == 1) {
                        if (y == startY) {
                            activeCs.setFont(f, sz)
                            activeCs.setLeading(lead)
                            activeCs.newLineAtOffset(leftMargin, y)
                        }
                    }

                    activeCs.setFont(f, sz)
                    activeCs.showText(line)
                    activeCs.newLine()
                    y -= lead
                }
            }

            newPage()

            if (resumeLayout) {
                val lines = text.lines()
                var isHeaderSection = true
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) {
                        y -= resumeBodyLeading / 2
                        continue
                    }
                    if (isHeaderSection && lines.indexOf(line) < 3) {
                        if (lines.indexOf(line) == 0) {
                            renderLine(trimmed, boldFont, resumeNameFontSize, resumeNameLeading)
                        } else if (lines.indexOf(line) == 1) {
                            renderLine(trimmed, font, resumeContactFontSize, resumeContactLeading)
                            isHeaderSection = false
                        }
                    } else if (trimmed == "EXPERIENCE" || trimmed == "EDUCATION" || trimmed == "PROJECTS" || trimmed == "SKILLS" || trimmed == "SUMMARY") {
                        y -= resumeBodyLeading
                        renderLine(trimmed, boldFont, headerFontSize, resumeHeaderLeading)
                    } else {
                        renderLine(trimmed, font, resumeBodyFontSize, resumeBodyLeading)
                    }
                }
            } else if (coverLetterLayout) {
                if (date.isNotBlank()) {
                    renderLine(date, font, fontSize, leading)
                    y -= leading
                }
                if (companyName.isNotBlank()) {
                    renderLine(companyName, boldFont, headerFontSize, leading)
                    y -= leading
                }
                val paragraphs = text.split("\n\n")
                for (p in paragraphs) {
                    val cleanP = p.replace("\n", " ").trim()
                    if (cleanP.isNotBlank()) {
                        renderLine(cleanP, font, fontSize, leading)
                        y -= leading
                    }
                }
            } else if (cheatSheetLayout) {
                val sections = text.split("\n\n")
                for (sec in sections) {
                    val lines = sec.lines()
                    if (lines.isNotEmpty()) {
                        val header = lines.first().trim()
                        renderLine(header, boldFont, headerFontSize, leading)
                        for (l in lines.drop(1)) {
                            val cleanL = l.trim()
                            if (cleanL.isNotBlank()) {
                                renderLine(cleanL, font, fontSize, leading)
                            }
                        }
                        y -= leading
                    }
                }
            } else {
                for (line in text.lines()) {
                    renderLine(line, font, fontSize, leading)
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

    private fun renderPdfFile(file: File): List<Bitmap>? {
        val bitmaps = mutableListOf<Bitmap>()
        try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val scale = 1.5f
                val width = (page.width * scale).toInt()
                val height = (page.height * scale).toInt()
                val bitmap = createBitmap(width, height)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmaps.add(bitmap)
                page.close()
            }
            renderer.close()
            pfd.close()
            return bitmaps
        } catch (e: Exception) {
            Log.e("PdfExporter", "Failed to render PDF preview", e)
            return null
        }
    }
}
