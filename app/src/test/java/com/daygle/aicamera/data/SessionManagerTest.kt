package com.daygle.aicamera.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionManagerTest {

    @Test
    fun extractCsrfToken_readsQuotedAndUnquotedValues() {
        assertEquals(
            "abc123",
            SessionManager.extractCsrfToken("""<form><input type="hidden" name="csrf_token" value="abc123"></form>"""),
        )
        assertEquals(
            "xyz",
            SessionManager.extractCsrfToken("""<INPUT value='xyz' name='csrf_token'/>"""),
        )
        assertEquals("tok", SessionManager.extractCsrfToken("<input name=csrf_token value=tok>"))
    }

    @Test
    fun extractCsrfToken_ignoresOtherInputsAndEmptyValues() {
        assertNull(SessionManager.extractCsrfToken("""<input name="username" value="admin">"""))
        assertNull(SessionManager.extractCsrfToken("""<input name="csrf_token" value="">"""))
        assertNull(SessionManager.extractCsrfToken(""))
    }

    @Test
    fun normalizeBaseUrl_defaultsToHttpsAndAddsTrailingSlash() {
        assertEquals("https://cam.example.com/", SessionManager.normalizeBaseUrl("cam.example.com").toString())
        assertEquals("http://10.0.0.2:8080/", SessionManager.normalizeBaseUrl(" http://10.0.0.2:8080 ").toString())
        assertEquals("https://example.com/daygle/", SessionManager.normalizeBaseUrl("https://example.com/daygle").toString())
    }

    @Test
    fun normalizeBaseUrl_rejectsBlankAndInvalidInput() {
        assertNull(SessionManager.normalizeBaseUrl("   "))
        assertNull(SessionManager.normalizeBaseUrl("https://"))
    }
}
