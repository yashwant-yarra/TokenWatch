package com.tokenwatch.feature.websession

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.ClientCertRequest
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tokenwatch.BuildConfig
import com.tokenwatch.core.ui.components.BenchHeader
import com.tokenwatch.core.ui.components.PlateButton
import com.tokenwatch.core.ui.components.Rule
import com.tokenwatch.data.web.UsagePageSpec
import kotlinx.coroutines.delay
import org.json.JSONTokener

@Composable
fun WebSessionScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: WebSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.savedAt) {
        if (state.savedAt != null) onSaved()
    }
    WebSessionContent(
        providerName = viewModel.provider.displayName,
        spec = viewModel.spec,
        state = state,
        onBack = onBack,
        onPayload = viewModel::save,
    )
}

@Composable
private fun WebSessionContent(
    providerName: String,
    spec: UsagePageSpec,
    state: WebSessionUiState,
    onBack: () -> Unit,
    onPayload: (String, Boolean) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { BenchHeader("Connect $providerName", onBack = onBack) },
    ) { padding ->
        UsageWebView(
            spec = spec,
            state = state,
            onPayload = onPayload,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun UsageWebView(
    spec: UsagePageSpec,
    state: WebSessionUiState,
    onPayload: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val usageHost = remember(spec) { Uri.parse(spec.url).host }
    val webView = remember(spec.provider) { WebView(context) }
    var blockedHost by remember { mutableStateOf<String?>(null) }
    var popup by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(popup) {
        val shown = popup
        onDispose { shown?.destroy() }
    }
    BackHandler(enabled = popup != null) { popup = null }
    var pageUrl by remember { mutableStateOf<String?>(null) }
    val pageHost = pageUrl?.let { Uri.parse(it).host }

    DisposableEffect(webView) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            flushCookies(spec)
            webView.stopLoading()
            webView.destroy()
        }
    }

    // Provider pages render client-side and arrive via login redirects, so a single read on page load
    // misses them. Poll the provider's own host (never its sign-in host) until a read is saved.
    LaunchedEffect(webView) {
        while (true) {
            delay(2_000)
            if (webView.url?.let { Uri.parse(it).host } == usageHost) webView.extract(spec) { payload -> payload?.let { onPayload(it, false) } }
        }
    }

    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = {
                    webView.apply {
                        configureForUsagePage(
                            spec,
                            onBlocked = { blockedHost = it },
                            onAllowedPageStarted = {
                                blockedHost = null
                                pageUrl = it
                            },
                            onPopup = { popup = it },
                        )
                        loadUrl(spec.signInUrl)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            popup?.let { shown ->
                key(shown) { AndroidView(factory = { shown }, modifier = Modifier.fillMaxSize()) }
            }
        }
        Rule()
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                blockedHost?.let { "This sign-in option opens $it, which this app doesn't allow. Choose another way to sign in." }
                    ?: state.message
                    ?: GOOGLE_HINT.takeIf { pageHost == GOOGLE_HOST && usageHost != GEMINI_HOST }
                    ?: CLAUDE_EMAIL_HINT.takeIf { pageUrl?.startsWith(CLAUDE_LOGIN_URL) == true }
                    ?: "Sign in and open the usage page. Usage is saved automatically once it appears.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            PlateButton(
                "Read usage now",
                { webView.extract(spec) { payload -> payload?.let { onPayload(it, true) } } },
                Modifier.fillMaxWidth(),
                enabled = !state.saving,
            )
        }
    }
}

/** Locks a web view to [spec]'s HTTPS hosts. Shared by the sign-in screen and the hidden refresh reader. */
internal fun WebView.configureForUsagePage(
    spec: UsagePageSpec,
    onBlocked: (String?) -> Unit = {},
    onAllowedPageStarted: (String) -> Unit = {},
    // Shows a sign-in popup (or null when it closes). Without it, popups are refused.
    onPopup: ((WebView?) -> Unit)? = null,
    // Called when the page closes its own window (a popup finishing).
    onClose: () -> Unit = {},
) {
    // Must come before anything else touches this web view.
    webProfileOf(spec)?.let { WebViewCompat.setProfile(this, it.name) }
    // AndroidView otherwise gives WRAP_CONTENT, and a wrap-content web view measures 100vh/100svh as 0.
    // ChatGPT's login sets body { height: 100svh }, so its whole form collapsed to a blank page.
    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    // Inspectable from a computer in debug builds only, for fixing page readers; never in release.
    WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        allowFileAccessFromFileURLs = false
        allowUniversalAccessFromFileURLs = false
        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        safeBrowsingEnabled = true
        // Claude's "Continue with Google" signs in through a popup that hands the result back to the page.
        // Refused, the popup's last page loads in place of the page and stays blank.
        javaScriptCanOpenWindowsAutomatically = onPopup != null
        setSupportMultipleWindows(onPopup != null)
    }
    webChromeClient = object : WebChromeClient() {
        override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
            val transport = resultMsg?.obj as? WebView.WebViewTransport
            // Only popups the user tapped for; the popup gets the same host lock and cookie store.
            if (onPopup == null || !isUserGesture || transport == null) return false
            val popup = WebView(context).apply { configureForUsagePage(spec, onBlocked, onClose = { onPopup(null) }) }
            transport.webView = popup
            resultMsg.sendToTarget()
            onPopup(popup)
            return true
        }
        override fun onCloseWindow(window: WebView?) = onClose()
        override fun onPermissionRequest(request: android.webkit.PermissionRequest) = request.deny()
        override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: android.webkit.GeolocationPermissions.Callback) {
            callback.invoke(origin, false, false)
        }
    }
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            // Google's optional "add a selfie" prompt comes after sign-in has finished, and its "Not now"
            // doesn't work in a web view. Skip it and go on to the usage page.
            if (request.isForMainFrame && request.url.host == GOOGLE_ACCOUNT_HOST) {
                view.loadUrl(spec.url)
                return true
            }
            val blocked = request.isForMainFrame && !spec.allows(request.url.toString())
            // Say so instead of leaving the page's own spinner running forever.
            if (blocked) onBlocked(request.url.host)
            return blocked
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            // Form posts (such as submitting a sign-in code) skip shouldOverrideUrlLoading and land here,
            // so a block here must be reported too or the page just spins.
            if (url != null && !spec.allows(url)) {
                view.stopLoading()
                onBlocked(Uri.parse(url).host)
            } else if (url != null) {
                onAllowedPageStarted(url)
            }
        }

        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler, error: SslError?) = handler.cancel()
        override fun onReceivedClientCertRequest(view: WebView?, request: ClientCertRequest) = request.cancel()
    }
}

/** Runs [spec]'s extraction script; [onResult] gets the payload, or null if the page had none. */
/**
 * Writes [spec]'s cookies to disk now. The web view otherwise saves them only now and then, so a sign-in
 * was lost when the phone closed the app soon after (seen with GitHub, 2026-11). Main thread only.
 */
internal fun flushCookies(spec: UsagePageSpec) =
    (webProfileOf(spec)?.cookieManager ?: CookieManager.getInstance()).flush()

internal fun WebView.extract(spec: UsagePageSpec, onResult: (String?) -> Unit) =
    evaluateJavascript(spec.extractionScript) { result ->
        onResult(runCatching { JSONTokener(result).nextValue() as? String }.getOrNull())
    }

private const val GOOGLE_HOST = "accounts.google.com"
private const val GEMINI_HOST = "gemini.google.com"
private const val GOOGLE_ACCOUNT_HOST = "myaccount.google.com"

// Google refuses sign-in for other companies' apps inside embedded web views, and that check can't be
// honestly worked around. Email sign-in reaches the same account.
private const val GOOGLE_HINT = "If Google says this browser or app isn't secure, go back and choose " +
    "\"Continue with email\" with the same address."

private const val CLAUDE_LOGIN_URL = "https://claude.ai/login"

// Claude's email sign-in link opens in the phone's browser, not this web view, so signing in there
// never reaches the app. Opened elsewhere, the link shows a code that Claude's page here accepts.
private const val CLAUDE_EMAIL_HINT = "Claude's email link opens in your browser, not here. Open it " +
    "there, copy the verification code it shows, and enter it on this page."

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
