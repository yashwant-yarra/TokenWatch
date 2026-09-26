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
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

@OptIn(ExperimentalMaterial3Api::class)
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
        topBar = {
            TopAppBar(
                title = { Text("Connect $providerName") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
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
    var pageUrl by remember { mutableStateOf<String?>(null) }
    val pageHost = pageUrl?.let { Uri.parse(it).host }

    DisposableEffect(webView) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
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
        AndroidView(
            factory = {
                webView.apply {
                    configureForUsagePage(spec, onBlocked = { blockedHost = it }, onAllowedPageStarted = {
                        blockedHost = null
                        pageUrl = it
                    })
                    loadUrl(spec.signInUrl)
                }
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Button(
                onClick = { webView.extract(spec) { payload -> payload?.let { onPayload(it, true) } } },
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Read usage now")
            }
        }
    }
}

/** Locks a web view to [spec]'s HTTPS hosts. Shared by the sign-in screen and the hidden refresh reader. */
internal fun WebView.configureForUsagePage(
    spec: UsagePageSpec,
    onBlocked: (String?) -> Unit = {},
    onAllowedPageStarted: (String) -> Unit = {},
) {
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
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
    }
    webChromeClient = object : WebChromeClient() {
        override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean = false
        override fun onPermissionRequest(request: android.webkit.PermissionRequest) = request.deny()
        override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: android.webkit.GeolocationPermissions.Callback) {
            callback.invoke(origin, false, false)
        }
    }
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
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
internal fun WebView.extract(spec: UsagePageSpec, onResult: (String?) -> Unit) =
    evaluateJavascript(spec.extractionScript) { result ->
        onResult(runCatching { JSONTokener(result).nextValue() as? String }.getOrNull())
    }

private const val GOOGLE_HOST = "accounts.google.com"
private const val GEMINI_HOST = "gemini.google.com"

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
