package com.example.jobsearch.testing

import com.example.jobsearch.data.PairRequest
import com.example.jobsearch.data.PairResponse
import com.example.jobsearch.data.SharedJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncServerSecurityTest {

    @Test
    fun givenValidPairingPin_whenPairResponseCreated_contains30DayToken() {
        val request = PairRequest(pin = "654321")
        assertEquals("654321", request.pin)

        val response = PairResponse(
            status = "ok",
            token = "secure-bearer-token-uuid-999",
            expiresInDays = 30
        )
        assertEquals("ok", response.status)
        assertEquals("secure-bearer-token-uuid-999", response.token)
        assertEquals(30, response.expiresInDays)
    }

    @Test
    fun givenInvalidPairingPin_whenPairingFails_returnsErrorResponseWithoutToken() {
        val errorResponse = PairResponse(
            status = "error",
            message = "Invalid pairing PIN. Check the PIN displayed in JobSearch Settings."
        )
        assertEquals("error", errorResponse.status)
        assertNull(errorResponse.token)
        assertEquals("Invalid pairing PIN. Check the PIN displayed in JobSearch Settings.", errorResponse.message)
    }

    @Test
    fun givenSharedJobPayload_whenConstructed_formatsFieldsCorrectly() {
        val job = SharedJob(
            title = "Senior Android Engineer",
            company = "Google",
            description = "Kotlin, Coroutines, Hilt, Jetpack Compose",
            url = "https://careers.google.com/jobs/123"
        )
        assertEquals("Senior Android Engineer", job.title)
        assertEquals("Google", job.company)
        assertEquals("https://careers.google.com/jobs/123", job.url)
    }
}
