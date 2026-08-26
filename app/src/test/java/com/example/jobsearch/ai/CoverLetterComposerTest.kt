package com.example.jobsearch.ai

import com.example.jobsearch.data.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoverLetterComposerTest {

    private val resume = """
        Elizabeth Kale Whaley
        (555) 123-4567 | ewhaley@example.com | Birmingham, AL

        SUMMARY
        Operations leader with 20 years of experience.
    """.trimIndent()

    @Test
    fun extractHeader_takesNameAndContactLines() {
        assertEquals(
            "Elizabeth Kale Whaley\n(555) 123-4567 | ewhaley@example.com | Birmingham, AL",
            CoverLetterComposer.extractHeader(resume)
        )
    }

    @Test
    fun extractHeader_skipsSectionHeadersAndLabels() {
        val text = """
            PERSONAL INFO
            Contact:
            Elizabeth Kale Whaley
            Email: ewhaley@example.com
            (555) 123-4567
            SUMMARY
            ...
        """.trimIndent()
        assertEquals(
            "Elizabeth Kale Whaley\n(555) 123-4567",
            CoverLetterComposer.extractHeader(text)
        )
    }

    @Test
    fun extractHeader_returnsNullWhenBlank() {
        assertNull(CoverLetterComposer.extractHeader("  \n  "))
    }

    @Test
    fun extractLocation_findsCityStateInDescription() {
        val description = "Ai Corporate Interiors is hiring a project manager. " +
            "Based at our Birmingham, AL office, you will coordinate delivery."
        assertEquals("Birmingham, AL", CoverLetterComposer.extractLocation(description))
    }

    @Test
    fun extractLocation_usesLabeledLocationLine() {
        val description = "Full-time. Location: Birmingham, AL. Competitive salary."
        assertEquals("Birmingham, AL", CoverLetterComposer.extractLocation(description))
    }

    @Test
    fun extractLocation_returnsNullWhenNoLocation() {
        val description = "We are looking for a project manager with a can-do attitude."
        assertNull(CoverLetterComposer.extractLocation(description))
    }

    @Test
    fun companyBlock_usesCompanyOnlyWhenNoLocation() {
        val job = Job(
            title = "Project Manager",
            company = "Ai Corporate Interiors",
            description = "Coordinate delivery, installation and maintenance."
        )
        assertEquals("Ai Corporate Interiors", CoverLetterComposer.companyBlock(job))
    }

    @Test
    fun companyBlock_addsLocationWhenPresent() {
        val job = Job(
            title = "Project Manager",
            company = "Ai Corporate Interiors",
            description = "Join our Birmingham, AL team. Multi-task and coordinate schedules."
        )
        assertEquals(
            "Ai Corporate Interiors\nBirmingham, AL",
            CoverLetterComposer.companyBlock(job)
        )
    }

    @Test
    fun compose_prependsHeaderAndCompanyBlockToBody() {
        val job = Job(
            title = "Project Manager",
            company = "Ai Corporate Interiors",
            description = "Join our Birmingham, AL team."
        )
        val body = """
            Dear Hiring Manager,

            I would love to join your team.

            Sincerely,
            Elizabeth Kale Whaley
        """.trimIndent()
        val expected = """
            Elizabeth Kale Whaley
            (555) 123-4567 | ewhaley@example.com | Birmingham, AL

            Ai Corporate Interiors
            Birmingham, AL

            Dear Hiring Manager,

            I would love to join your team.

            Sincerely,
            Elizabeth Kale Whaley
        """.trimIndent()
        assertEquals(expected, CoverLetterComposer.compose(body, resume, job))
    }

    @Test
    fun compose_returnsBodyWhenNoHeaderOrCompany() {
        val body = "Dear Hiring Manager,\n\nBody text."
        assertEquals(body, CoverLetterComposer.compose(body, "  ", Job(title = "PM")))
    }
}
