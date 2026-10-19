package com.tokenwatch.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tokenwatch.R
import com.tokenwatch.domain.model.AiProvider
import com.tokenwatch.domain.model.ConnectionStatus
import com.tokenwatch.domain.model.UsageFailure
import java.time.Instant

/**
 * The one section style: a hairline rule, an engraved heading, then the content. No cards; the
 * rule is what separates one group from the next.
 */
@Composable
fun Section(
    title: String?,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Rule()
        Column(Modifier.padding(top = 18.dp, bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (title != null || action != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (title != null) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f).semantics { heading() },
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    action?.invoke()
                }
            }
            content()
        }
    }
}

/** A provider's mark, bare: the name always sits beside it, so it's decorative. */
@Composable
fun ProviderMark(provider: AiProvider, size: Dp = 22.dp) {
    when (provider) {
        AiProvider.CLAUDE -> Image(painterResource(R.drawable.ic_provider_claude), null, Modifier.size(size))
        AiProvider.GEMINI -> Image(painterResource(R.drawable.ic_provider_gemini), null, Modifier.size(size))
        // The OpenAI mark is monochrome, so it follows the theme's text color.
        AiProvider.CODEX -> Image(
            painterResource(R.drawable.ic_provider_codex),
            null,
            Modifier.size(size),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
        )
    }
}

fun connectionLabel(status: ConnectionStatus): String = when (status) {
    ConnectionStatus.CONNECTED -> "Connected"
    ConnectionStatus.EXPIRED -> "Connection expired"
    ConnectionStatus.REVOKED -> "Access revoked"
    ConnectionStatus.DISCONNECTED -> "Not connected"
    ConnectionStatus.ERROR -> "Connection problem"
}

/** Header for the three bench-bar screens, so their titles match. */
@Composable
fun TopLevelBar(title: String) = BenchHeader(title)

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().semantics { contentDescription = "Loading" }, contentAlignment = Alignment.Center) {
        TurningGlass()
    }
}

@Composable
fun MessageState(
    glyph: Glyph?,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    art: (@Composable () -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.widthIn(max = 420.dp),
        ) {
            if (art != null) {
                art()
            } else if (glyph != null) {
                GlyphIcon(glyph, size = 40.dp)
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                PlateButton(actionLabel, onAction, Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlyphIcon(Glyph.INFO, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 18.dp, modifier = Modifier.padding(top = 1.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A failed read, hung under its glass like an errata slip: the one place the alert color fills a
 * surface. The glass above keeps its last reading, drawn in a broken outline.
 */
@Composable
fun ErrataSlip(
    provider: AiProvider,
    failure: UsageFailure,
    now: Instant,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(3.dp))
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.ALERT, tint = MaterialTheme.colorScheme.onErrorContainer, size = 18.dp)
        Text(
            failure.message(provider, now),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 10.dp),
        )
        if (actionLabel != null && onAction != null) {
            PlateButton(actionLabel, onAction, style = PlateStyle.QUIET, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
fun LabelValueRow(label: String, value: String, modifier: Modifier = Modifier, emphasize: Boolean = false) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge)
    }
}
