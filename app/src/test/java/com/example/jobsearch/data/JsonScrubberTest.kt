package com.example.jobsearch.data

import org.junit.Assert.assertEquals
import org.junit.Test

class JsonScrubberTest {

    @Test
    fun `test stripping markdown markers`() {
        val input = """
            ```json
            { "name": "John Doe" }
            ```
        """.trimIndent()
        val expected = """
            { "name": "John Doe" }
        """.trimIndent()
        assertEquals(expected, JsonScrubber.scrub(input))
    }

    @Test
    fun `test handling conversational filler`() {
        val input = """
            Here is the JSON you requested:
            {
                "summary": "Experienced developer"
            }
            I hope this helps!
        """.trimIndent()
        val expected = """
            {
                "summary": "Experienced developer"
            }
        """.trimIndent()
        assertEquals(expected, JsonScrubber.scrub(input))
    }

    @Test
    fun `test internal quote escaping`() {
        val input = """
            {
                "summary": "He said "Hello" and left",
                "nested": "Value with "quotes" inside"
            }
        """.trimIndent()
        // The scrubber's escapeInternalQuotesAndWhitespace logic:
        // It looks for " followed by , } ] or : to decide if it's structural or internal.
        // In "He said "Hello" and left", the first " is start of string.
        // The second " is followed by H (not structural), so it should be escaped.
        // The third " is followed by space (not structural), so it should be escaped.
        // The fourth " is followed by , (structural), so it should NOT be escaped (it's the end of string).
        
        val scrubbed = JsonScrubber.scrub(input)
        
        // Note: The scrubber also converts \n to \\n inside strings.
        // In this case, there are no newlines inside the values in the *input* string literal above,
        // but the input passed to scrub might have them if it was a multi-line string.
        
        // Let's check if the internal quotes are escaped.
        assertEquals(true, scrubbed.contains("""He said \"Hello\" and left"""))
        assertEquals(true, scrubbed.contains("""Value with \"quotes\" inside"""))
    }

    @Test
    fun `test balanced block selection`() {
        val input = """
            Filler { "short": "json" }
            {
                "long": "json block that should be selected",
                "data": 123
            }
            More filler
        """.trimIndent()
        val expected = """
            {
                "long": "json block that should be selected",
                "data": 123
            }
        """.trimIndent()
        assertEquals(expected, JsonScrubber.scrub(input))
    }
}
