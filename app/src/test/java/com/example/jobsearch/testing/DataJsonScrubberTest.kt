package com.example.jobsearch.testing

import com.example.jobsearch.data.JsonScrubber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataJsonScrubberTest {

    @Test
    fun givenMarkdownJsonCodeBlock_whenScrubbed_returnsCleanJsonString() {
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
    fun givenConversationalAiFillerText_whenScrubbed_extractsJsonBlockOnly() {
        val input = """
            Here is the JSON response:
            {
                "summary": "Experienced developer"
            }
            Hope this helps!
        """.trimIndent()
        val expected = """
            {
                "summary": "Experienced developer"
            }
        """.trimIndent()
        assertEquals(expected, JsonScrubber.scrub(input))
    }

    @Test
    fun givenUnescapedInternalQuotes_whenScrubbed_escapesQuotesCorrectly() {
        val input = """
            {
                "summary": "He said "Hello" and left",
                "nested": "Value with "quotes" inside"
            }
        """.trimIndent()
        val scrubbed = JsonScrubber.scrub(input)
        assertTrue(scrubbed.contains("""He said \"Hello\" and left"""))
        assertTrue(scrubbed.contains("""Value with \"quotes\" inside"""))
    }

    @Test
    fun givenMultipleJsonBlocks_whenScrubbed_selectsLargestValidBlock() {
        val input = """
            Prefix { "short": "json" }
            {
                "long": "target json block to be extracted",
                "data": 123
            }
            Suffix text
        """.trimIndent()
        val expected = """
            {
                "long": "target json block to be extracted",
                "data": 123
            }
        """.trimIndent()
        assertEquals(expected, JsonScrubber.scrub(input))
    }
}
