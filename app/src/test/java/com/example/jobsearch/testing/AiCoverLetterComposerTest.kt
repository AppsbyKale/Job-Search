package com.example.jobsearch.testing

import com.example.jobsearch.ai.CoverLetterComposer
import com.example.jobsearch.data.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiCoverLetterComposerTest {

    private val sampleResume = """
        Elizabeth Kale Whaley
        (555) 123-4567 | ewhaley@example.com | Birmingham, AL

        SUMMARY
        Operations leader with 20 years of experience.
    """.trimIndent()

    @Test
    fun givenResumeText_whenExtractingHeader_extractsNameAndContactInfo() {
        val header = CoverLetterComposer.extractHeader(sampleResume)
        assertEquals("Elizabeth Kale Whaley\n(555) 123-4567 | ewhaley@example.com | Birmingham, AL", header)
    }

    @Test
    fun givenJobDescription_whenExtractingLocation_findsCityAndState() {
        val description = "Company is hiring a manager in Birmingham, AL."
        assertEquals("Birmingham, AL", CoverLetterComposer.extractLocation(description))
    }

    @Test
    fun givenEmptyText_whenExtractingHeader_returnsNull() {
        assertNull(CoverLetterComposer.extractHeader("   \n   "))
    }

    @Test
    fun givenJobWithLocation_whenCompanyBlockBuilt_includesCompanyNameAndLocation() {
        val job = Job(
            title = "Project Manager",
            company = "Tech Corp",
            description = "Based at our Birmingham, AL office."
        )
        assertEquals("Tech Corp\nBirmingham, AL", CoverLetterComposer.companyBlock(job))
    }
}
