package com.example.jobsearch.document

import com.tom_roush.pdfbox.pdmodel.font.PDType1Font

/**
 * Single source of truth for all document export styling (font sizes, margins, leading, etc.).
 * This "Style Lock-in" ensures consistency across all document generation paths.
 */
object DocumentStyle {
    val font = PDType1Font.HELVETICA
    val boldFont = PDType1Font.HELVETICA_BOLD
    val italicFont = PDType1Font.HELVETICA_OBLIQUE
    
    const val fontSize = 11f
    const val headerFontSize = 13f
    const val leading = 15f
    
    const val resumeBodyFontSize = 10f
    const val resumeNameFontSize = 18f
    const val resumeContactFontSize = 9.5f
    
    const val resumeBodyLeading = 14f
    const val resumeHeaderLeading = 17f
    const val resumeNameLeading = 24f
    const val resumeContactLeading = 13f
    
    const val leftMargin = 56f
    const val rightMargin = 56f
    const val topMargin = 56f
    const val bottomMargin = 56f
    
    const val resumeSkillsGap = 20f
    const val resumeSectionSpacing = 6f
    const val resumeDividerYOffset = 12f
    
    const val coverLetterHeaderGap = 40f
    const val coverLetterDateGap = 12f
    const val coverLetterParagraphGap = 12f
    const val coverLetterClosingGap = 8f
}
