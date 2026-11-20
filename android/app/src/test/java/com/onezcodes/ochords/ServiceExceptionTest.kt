package com.onezcodes.ochords

import com.onezcodes.ochords.services.ServiceException
import com.onezcodes.ochords.services.userMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceExceptionTest {

    @Test
    fun userMessagePrefersServiceExceptionMessage() {
        assertEquals("You’re offline.", ServiceException.offline.userMessage)
        assertEquals("That email and password don’t match.", ServiceException.invalidCredentials.userMessage)
        assertEquals("We couldn’t read that sheet.", ServiceException.scanFailed.userMessage)
        assertEquals("Custom from server.", ServiceException.server("Custom from server.").userMessage)
    }

    @Test
    fun userMessageFallsBackForPlainThrowables() {
        assertEquals("boom", RuntimeException("boom").userMessage)
        assertEquals("Something went wrong. Try again.", RuntimeException("   ").userMessage)
        assertEquals("Something went wrong. Try again.", RuntimeException().userMessage)
        // Cancelled has an empty message — fall through to the generic copy.
        assertEquals("Something went wrong. Try again.", ServiceException.cancelled.userMessage)
    }
}
