package com.secops.mobile.ui.dashboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusValidatorTest {

    @Test
    fun testSuccessfulResponseCodes() {
        // 2xx status codes should be considered online
        assertTrue(StatusValidator.isServiceOnline(200))
        assertTrue(StatusValidator.isServiceOnline(201))
        assertTrue(StatusValidator.isServiceOnline(204))
    }

    @Test
    fun testRedirectResponseCodes() {
        // 3xx status codes should be considered online (representing active redirect states)
        assertTrue(StatusValidator.isServiceOnline(301))
        assertTrue(StatusValidator.isServiceOnline(302))
        assertTrue(StatusValidator.isServiceOnline(303)) // Authelia redirect
        assertTrue(StatusValidator.isServiceOnline(307))
        assertTrue(StatusValidator.isServiceOnline(308)) // Caddy HTTPS redirect
    }

    @Test
    fun testUnauthorizedResponseCode() {
        // 401 Unauthorized should be considered online
        assertTrue(StatusValidator.isServiceOnline(401))
    }

    @Test
    fun testOfflineAndFailureResponseCodes() {
        // Clients/API errors and server errors should be considered offline
        assertFalse(StatusValidator.isServiceOnline(400))
        assertFalse(StatusValidator.isServiceOnline(403))
        assertFalse(StatusValidator.isServiceOnline(404))
        assertFalse(StatusValidator.isServiceOnline(500))
        assertFalse(StatusValidator.isServiceOnline(502))
        assertFalse(StatusValidator.isServiceOnline(503))
    }
}
