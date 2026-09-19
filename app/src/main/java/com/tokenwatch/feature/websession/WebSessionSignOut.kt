package com.tokenwatch.feature.websession

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewDatabase
import android.webkit.WebStorage
import com.tokenwatch.data.web.UsagePageSpec
import com.tokenwatch.domain.model.AiProvider

/**
 * Signs [provider] out of this app's web view by expiring the cookies on its own domains and
 * deleting their site storage. Must run on the main thread.
 */
fun signOutOfWebSession(provider: AiProvider) {
    val cookies = CookieManager.getInstance()
    UsagePageSpec.forProvider(provider).sessionDomains.forEach { domain ->
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
    WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword()
    WebView(context).apply {
        clearCache(true)
        clearFormData()
        clearHistory()
        destroy()
    }
}
