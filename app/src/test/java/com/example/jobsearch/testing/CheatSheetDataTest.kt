package com.example.jobsearch.testing

import com.example.jobsearch.data.CheatSheetData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheatSheetDataTest {

    @Test
    fun parseCheatSheetWithNotesFromText() {
        val text = """
            ABOUT THE COMPANY
            Associated Metalcast is a leading precision metal casting manufacturer.

            RELEVANT SKILLS
            Kotlin, C++, Java

            KEY HIGHLIGHTS
            • 5 years Android experience

            Q&A
            Q: Tell me about yourself.
            STRATEGY: Keep it concise.
            EXAMPLE ANSWER:
            I am a software engineer.

            NOTES
            What are the main technical challenges?
            What does a typical day look like?
        """.trimIndent()

        val data = CheatSheetData.fromText(text)
        assertTrue(data != null)
        assertEquals("Associated Metalcast is a leading precision metal casting manufacturer.", data?.aboutCompany)
        assertTrue(data?.relevantSkills?.contains("Kotlin") == true)
        assertTrue(data?.keyHighlights?.contains("5 years Android experience") == true)
        assertTrue(data?.toughQuestions?.isNotEmpty() == true)
        assertTrue(data?.notes?.contains("What are the main technical challenges?") == true)
    }
}
