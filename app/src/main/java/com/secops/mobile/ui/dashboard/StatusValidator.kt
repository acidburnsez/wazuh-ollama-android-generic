package com.secops.mobile.ui.dashboard

object StatusValidator {
    /**
     * Determines if a service is online based on its HTTP response code.
     * We accept:
     * - 2xx (Success)
     * - 3xx (Redirects, e.g., Authelia's 303 See Other, or HTTPS 308 Permanent Redirect)
     * - 401 (Unauthorized, but the server is running and responding)
     */
    fun isServiceOnline(code: Int): Boolean {
        return (code in 200..399) || code == 401
    }
}
