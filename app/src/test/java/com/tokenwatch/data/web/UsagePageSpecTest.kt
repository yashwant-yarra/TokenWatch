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
            "https://chatgpt.com/codex/cloud/settings/usage",
            spec.url,
        )
        assertTrue(spec.allows("https://device.auth.openai.com/authorize"))
        assertTrue(spec.allows("https://openai.com/"))
        assertTrue(spec.allows("https://auth0.openai.com/u/mfa-otp-challenge"))
        assertTrue(spec.allows("https://accounts.google.com/o/oauth2/v2/auth"))
        assertTrue(spec.allows("https://appleid.apple.com/auth/authorize"))
        assertFalse(spec.allows("https://auth.openai.com.example.com/phishing"))
    }
}
