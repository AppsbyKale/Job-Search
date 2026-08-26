package com.example.jobsearch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeDataTest {

    @Test
    fun `test toJson and fromJson consistency`() {
        val original = ResumeData(
            name = "John Doe",
            summary = "Developer",
            skills = listOf(SkillCategory("Coding", listOf("Kotlin", "Java"))),
            experience = listOf(
                ExperienceItem("Dev", "Google", "Mountain View", "2020-2024", listOf("Did stuff"))
            )
        )
        val json = original.toJson()
        val restored = ResumeData.fromJson(json)
        
        assertNotNull(restored)
        assertEquals(original, restored)
    }

    @Test
    fun `test fromText heuristic with headers`() {
        val input = """
            Jane Smith
            jane@example.com | 123-456-7890
            
            SUMMARY
            A very talented engineer.
            
            SKILLS
            Technical
            - Android, Kotlin, Swift
            
            EXPERIENCE
            Senior Dev | Tech Corp | NY
            2018 - Present
            - Led a team of five
            - Optimized performance
            
            EDUCATION
            BS Computer Science
            University of Nowhere, 2014-2018
        """.trimIndent()
        
        val data = ResumeData.fromText(input)
        
        assertEquals("Jane Smith", data.name)
        assertEquals("jane@example.com | 123-456-7890", data.contact)
        assertEquals("A very talented engineer.", data.summary)
        assertEquals(1, data.skills.size)
        assertEquals("Technical", data.skills[0].name)
        assertTrue(data.skills[0].skills.contains("Android"))
        assertEquals(1, data.experience.size)
        assertEquals("Senior Dev", data.experience[0].title)
        assertEquals("NY", data.experience[0].location)
        assertEquals(2, data.experience[0].bullets.size)
    }

    @Test
    fun `test fromText with JSON input`() {
        val jsonInput = """
            ```json
            {
                "name": "Json Person",
                "summary": "Parsing from JSON"
            }
            ```
        """.trimIndent()
        
        val data = ResumeData.fromText(jsonInput)
        
        assertEquals("Json Person", data.name)
        assertEquals("Parsing from JSON", data.summary)
    }

    @Test
    fun `test fromText with messy non-JSON input`() {
        val input = """
            Random text at start
            NAME: Random Guy
            CONTACT: random@guy.com
            SUMMARY: Just some guy.
        """.trimIndent()
        
        val data = ResumeData.fromText(input)
        
        // Headers in KNOWN_HEADERS: "personal info", "contact", "contact info", "skills", "core skills",
        // "relevant work experience", "work experience", "experience",
        // "education", "projects", "professional summary", "summary"
        
        // "SUMMARY" matches. "NAME" does not.
        // So "Random text at start" and "NAME: Random Guy" and "CONTACT: random@guy.com" 
        // fall into the default section (header = "").
        
        assertEquals("Random text at start", data.name) // First non-blank line
        assertEquals("NAME: Random Guy", data.contact) // Second non-blank line
        assertEquals("Just some guy.", data.summary)
    }
}
