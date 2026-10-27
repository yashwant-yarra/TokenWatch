package com.tokenwatch.data.web

import com.tokenwatch.domain.model.AiProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsagePageSpecTest {
    @Test
    fun `Claude allows its pages and Google sign-in`() {
        val spec = UsagePageSpec.forProvider(AiProvider.CLAUDE)

        assertTrue(spec.allows("https://claude.ai/new#settings/usage"))
        assertTrue(spec.allows("https://auth.anthropic.com/login"))
        assertTrue(spec.allows("https://accounts.google.com/o/oauth2/auth"))
        assertFalse(spec.allows("https://accounts.google.com.example.com/phishing"))
    }

    @Test
    fun `Codex starts on the usage page`() {
        val spec = UsagePageSpec.forProvider(AiProvider.CODEX)
        assertEquals(
            "https://chatgpt.com/settings/usage?tab=overview",
            spec.url,
        )
        assertTrue(spec.allows("https://device.auth.openai.com/authorize"))
        assertTrue(spec.allows("https://openai.com/"))
        assertTrue(spec.allows("https://auth0.openai.com/u/mfa-otp-challenge"))
        assertTrue(spec.allows("https://accounts.google.com/o/oauth2/v2/auth"))
        assertTrue(spec.allows("https://appleid.apple.com/auth/authorize"))
        assertFalse(spec.allows("https://auth.openai.com.example.com/phishing"))
    }

    @Test
    fun `Google sign-in can finish for every provider`() {
        AiProvider.entries.map(UsagePageSpec::forProvider).forEach { spec ->
            // Where Google sets its sign-in cookies after 2-step verification.
            assertTrue(spec.allows("https://accounts.youtube.com/accounts/SetSID"))
            assertFalse(spec.allows("https://www.youtube.com/"))
            // ...and then on the country's own Google sign-in address.
            assertTrue(spec.allows("https://accounts.google.co.in/accounts/SetSID"))
            assertTrue(spec.allows("https://accounts.google.com.au/accounts/SetSID"))
            assertTrue(spec.allows("https://accounts.google.de/accounts/SetSID"))
            // Lookalikes anyone could register are not Google's.
            assertFalse(spec.allows("https://accounts.google.co.in.example.com/"))
            assertFalse(spec.allows("https://accounts.google.example.com/"))
            assertFalse(spec.allows("https://accounts.google.com.xyz/"))
            assertFalse(spec.allows("http://accounts.google.co.in/accounts/SetSID"))
        }
    }

    @Test
    fun `Gemini sign-in returns to the usage page`() {
        val spec = UsagePageSpec.forProvider(AiProvider.GEMINI)
        assertTrue(spec.allows(spec.signInUrl))
        assertEquals("https://gemini.google.com/usage", java.net.URLDecoder.decode(spec.signInUrl.substringAfter("continue="), "UTF-8"))
    }

    @Test
    fun `only Gemini keeps its own cookie store`() {
        assertEquals("gemini", UsagePageSpec.forProvider(AiProvider.GEMINI).webProfile)
        assertEquals(null, UsagePageSpec.forProvider(AiProvider.CLAUDE).webProfile)
        assertEquals(null, UsagePageSpec.forProvider(AiProvider.CODEX).webProfile)
    }
}
