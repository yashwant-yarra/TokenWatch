package com.tokenwatch.feature.websession

import com.tokenwatch.data.web.UsagePageSpec
import com.tokenwatch.domain.model.AiProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSessionSignOutTest {
    @Test
    fun cookieNamesAreReadFromTheHeader() {
        assertEquals(listOf("sessionKey", "__Host-flag", "cf_clearance"), cookieNames("sessionKey=abc; __Host-flag=1;cf_clearance=x=y; sessionKey=dup"))
        assertEquals(emptyList<String>(), cookieNames(null))
    }

    @Test
    fun signingOutOfClaudeOrCodexLeavesSharedSignInAlone() {
        listOf(AiProvider.CLAUDE, AiProvider.CODEX).forEach { provider ->
            val domains = UsagePageSpec.forProvider(provider).sessionDomains
            assertFalse(provider.name, domains.any { it.endsWith("google.com") || it.endsWith("apple.com") })
        }
        assertTrue("google.com" in UsagePageSpec.forProvider(AiProvider.GEMINI).sessionDomains)
    }

    @Test
    fun onlySignInPagesAndBareHomePagesCountAsSignedOut() {
        assertTrue(isSignedOutPath(emptyList())) // ChatGPT's logged-out home
        assertTrue(isSignedOutPath(listOf("login")))
        assertTrue(isSignedOutPath(listOf("auth", "login")))
        assertFalse(isSignedOutPath(listOf("new"))) // Claude redirects its usage page to /new#settings/usage
        assertFalse(isSignedOutPath(listOf("codex", "cloud", "settings", "usage")))
    }
}
