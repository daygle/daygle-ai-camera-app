package com.daygle.aicamera.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * OkHttp rejects CR/LF in a header value with an IllegalArgumentException before
 * the request is sent, so pasted tokens must be sanitized rather than allowed to
 * break every request.
 */
class HeaderValueTest {

    @Test
    fun stripsLineBreaksAndSurroundingWhitespace() {
        assertEquals("secret", " secret\r\n".asHeaderValue())
        assertEquals("token", "\ntoken\n".asHeaderValue())
        assertEquals("a b", "a b".asHeaderValue())
    }

    @Test
    fun blankValueStaysEmpty() {
        assertEquals("", " \r\n\t ".asHeaderValue())
    }
}
