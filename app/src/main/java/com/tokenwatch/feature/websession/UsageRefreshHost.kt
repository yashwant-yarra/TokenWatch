package com.tokenwatch.feature.websession

import android.net.Uri
import android.webkit.WebView
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tokenwatch.data.web.UsagePageSpec
import com.tokenwatch.domain.model.AiProvider
import com.tokenwatch.domain.model.UsageFailure
import com.tokenwatch.domain.repository.UsageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

@HiltViewModel
class UsageRefreshViewModel @Inject constructor(private val repository: UsageRepository) : ViewModel() {
    val refreshing = repository.refreshing

    suspend fun trySave(provider: AiProvider, rawJson: String): Boolean =
        runCatching { repository.saveWebPayload(provider, rawJson) }.isSuccess

    fun finish(provider: AiProvider, failure: UsageFailure?) {
        viewModelScope.launch { repository.finishRefresh(provider, failure) }
    }
}

/**
 * Re-reads usage pages for providers the user asked to refresh, using the signed-in web session
 * in an invisible web view. Runs only while the app is on screen and only on request.
 */
@Composable
fun UsageRefreshHost(viewModel: UsageRefreshViewModel = hiltViewModel()) {
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    refreshing.forEach { provider -> key(provider) { HiddenUsageReader(provider, viewModel) } }
}

@Composable
private fun HiddenUsageReader(provider: AiProvider, viewModel: UsageRefreshViewModel) {
    val context = LocalContext.current
    val spec = remember(provider) { UsagePageSpec.forProvider(provider) }
    val webView = remember(provider) {
        WebView(context).apply {
            configureForUsagePage(spec)
            loadUrl(spec.url)
        }
    }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }
    LaunchedEffect(webView) {
        viewModel.finish(provider, webView.readUsage(spec) { viewModel.trySave(provider, it) })
    }
    // Same size as the screen, so the page picks the same layout it shows on the sign-in screen.
    val screen = LocalConfiguration.current
    AndroidView(
        factory = { webView },
        modifier = Modifier.requiredSize(screen.screenWidthDp.dp, screen.screenHeightDp.dp).alpha(0f),
    )
}

/**
 * Polls the page loaded in this web view until [save] accepts a reading. Returns null on success,
 * [UsageFailure.AuthExpired] if the page stays on a sign-in page, or a generic failure on timeout.
 */
internal suspend fun WebView.readUsage(spec: UsagePageSpec, save: suspend (String) -> Boolean): UsageFailure? {
    val usageHost = Uri.parse(spec.url).host
    var signedOutPolls = 0
    repeat(MAX_POLLS) {
        delay(2_000)
        val url = url?.let(Uri::parse)
        if (url != null && url.host == usageHost && !url.isSignedOutPage()) {
            signedOutPolls = 0
            val payload = extractNow(spec)
            if (payload != null && save(payload)) return null
        } else if (++signedOutPolls >= SIGNED_OUT_POLLS) {
            // Redirects pass through sign-in pages briefly, so only staying on one means signed out.
            return UsageFailure.AuthExpired
        }
    }
    return UsageFailure.Unknown("Usage page did not load")
}

/**
 * Where a signed-out session ends up: a sign-in page, or the site's bare home page (ChatGPT shows its
 * logged-out home instead of the usage page). Usage pages may redirect elsewhere on the same site, as
 * Claude does to its settings panel, so any other page counts as signed in.
 */
private fun Uri.isSignedOutPage(): Boolean = isSignedOutPath(pathSegments)

internal fun isSignedOutPath(segments: List<String>): Boolean =
    segments.isEmpty() || segments.any { it in setOf("login", "logout", "log-in", "auth", "signin") }

private suspend fun WebView.extractNow(spec: UsagePageSpec): String? = suspendCancellableCoroutine { cont ->
    extract(spec) { if (cont.isActive) cont.resume(it) }
}

private const val MAX_POLLS = 15
private const val SIGNED_OUT_POLLS = 3
