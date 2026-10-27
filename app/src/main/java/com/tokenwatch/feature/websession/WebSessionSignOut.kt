package com.tokenwatch.feature.websession

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewDatabase
import android.webkit.WebStorage
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import com.tokenwatch.data.web.UsagePageSpec
import com.tokenwatch.domain.model.AiProvider

/**
 * Signs [provider] out of this app's web view by expiring the cookies on its own domains and
 * deleting their site storage. Must run on the main thread.
 */
fun signOutOfWebSession(provider: AiProvider) {
    val spec = UsagePageSpec.forProvider(provider)
    // A provider with its own cookie store holds nothing else, so all of it goes.
    webProfileOf(spec)?.let { profile ->
        profile.clear()
        return
    }
    val cookies = CookieManager.getInstance()
    spec.sessionDomains.forEach { domain ->
        val url = "https://$domain"
        cookieNames(cookies.getCookie(url)).forEach { name ->
            // Only Path=/ cookies are visible here; sign-in cookies use it, so narrower paths are left.
            cookies.setCookie(url, "$name=; Max-Age=0; Path=/; Secure")
            cookies.setCookie(url, "$name=; Max-Age=0; Path=/; Secure; Domain=$domain")
        }
        WebStorage.getInstance().deleteOrigin(url)
    }
    cookies.flush()
}

/** [spec]'s own cookie store, or null when it shares the app's (or this phone's WebView can't keep separate ones). Main thread only. */
internal fun webProfileOf(spec: UsagePageSpec): Profile? = spec.webProfile
    ?.takeIf { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }
    ?.let { ProfileStore.getInstance().getOrCreateProfile(it) }

private fun Profile.clear() {
    cookieManager.removeAllCookies(null)
    cookieManager.flush()
    webStorage.deleteAllData()
}

/** Names from a `Cookie` header value such as `a=1; b=2`. */
internal fun cookieNames(header: String?): List<String> =
    header.orEmpty().split(';').map { it.substringBefore('=').trim() }.filter { it.isNotEmpty() }.distinct()

/** Wipes everything the in-app web view stored: sign-ins, site storage, page cache and form data. Main thread only. */
fun clearAllWebData(context: Context) {
    CookieManager.getInstance().apply {
        removeAllCookies(null)
        flush()
    }
    WebStorage.getInstance().deleteAllData()
    AiProvider.entries.mapNotNull { webProfileOf(UsagePageSpec.forProvider(it)) }.forEach { it.clear() }
    WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword()
    WebView(context).apply {
        clearCache(true)
        clearFormData()
        clearHistory()
        destroy()
    }
}
