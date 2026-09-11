package com.example.jobsearch.testing

import com.example.jobsearch.data.ExperienceItem
import com.example.jobsearch.data.ResumeData
import com.example.jobsearch.data.SkillCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataResumeParsingTest {

    @Test
    fun givenResumeData_whenConvertedToJsonAndBack_restoresIdenticalObject() {
        val original = ResumeData(
            name = "Jane Doe",
            summary = "Senior Android Engineer",
            skills = listOf(SkillCategory("Technical", listOf("Kotlin", "Jetpack Compose"))),
            experience = listOf(
                ExperienceItem("Android Lead", "Tech Corp", "San Francisco, CA", "2021-Present", listOf("Led mobile team"))
            )
        )
        val json = original.toJson()
        val restored = ResumeData.fromJson(json)

        assertNotNull(restored)
        assertEquals(original, restored)
    }

    @Test
    fun givenPlainResumeText_whenParsedWithHeuristics_extractsFieldsAccurately() {
        val input = """
            Jane Smith
            jane@example.com | 123-456-7890
            
            SUMMARY
            Senior engineer specializing in mobile architecture.
            
            SKILLS
            Technical
            - Android, Kotlin, Swift
            
            EXPERIENCE
            Senior Dev | Tech Corp | NY
            2018 - Present
            - Architected modular mobile application
            
            EDUCATION
            BS Computer Science
            University, 2014-2018
        """.trimIndent()

        val data = ResumeData.fromText(input)

        assertEquals("Jane Smith", data.name)
        assertEquals("jane@example.com | 123-456-7890", data.contact)
        assertEquals("Senior engineer specializing in mobile architecture.", data.summary)
        assertEquals(1, data.skills.size)
        assertEquals("Technical", data.skills[0].name)
        assertTrue(data.skills[0].skills.contains("Android"))
    }
}
