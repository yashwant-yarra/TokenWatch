package com.tokenwatch.core.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Modifier.Node
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------------
// Press feedback: an ink wash instead of a ripple, and a drawn focus ring for keyboards.

/** Replaces the platform ripple for every clickable in the app. */
class InkWash(private val ink: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = InkWashNode(interactionSource, ink)
    override fun equals(other: Any?) = other is InkWash && other.ink == ink
    override fun hashCode() = ink.hashCode()
}

private class InkWashNode(private val source: InteractionSource, private val ink: Color) : Node(), DrawModifierNode {
    private var pressed = 0
    private var focused = 0
    private val wash = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> pressed++
                    is PressInteraction.Release, is PressInteraction.Cancel -> pressed = (pressed - 1).coerceAtLeast(0)
                    is FocusInteraction.Focus -> focused++
                    is FocusInteraction.Unfocus -> focused = (focused - 1).coerceAtLeast(0)
                }
                launch { wash.animateTo(if (pressed > 0) 1f else 0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                invalidateDraw()
            }
        }
        coroutineScope.launch { snapshotFlow { wash.value }.collect { invalidateDraw() } }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (wash.value > 0f) drawRect(ink.copy(alpha = 0.09f * wash.value))
        if (focused > 0) drawRect(ink, style = Stroke(width = 2.dp.toPx()))
    }
}

// ---------------------------------------------------------------------------------------------
// Haptics: small physical answers to taps, steps and finished reads.

class Haptics(private val view: View) {
    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    fun press() = view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    fun confirm() = view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
    )
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

// ---------------------------------------------------------------------------------------------
// Glyphs: the app's own hairline icon set, drawn in the glasses' stroke.

enum class Glyph { BACK, CHEVRON, USAGE, HISTORY, ACCOUNTS, SETTINGS, INFO, ALERT, CHECK }

@Composable
fun GlyphIcon(glyph: Glyph, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / 24f
        val line = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        fun poly(vararg pts: Float, closed: Boolean = false) = Path().apply {
            moveTo(pts[0] * u, pts[1] * u)
            var i = 2
            while (i < pts.size) { lineTo(pts[i] * u, pts[i + 1] * u); i += 2 }
            if (closed) close()
        }
        when (glyph) {
            Glyph.BACK -> {
                drawLine(tint, p(19f, 12f), p(5f, 12f), strokeWidth = line.width, cap = StrokeCap.Round)
                drawPath(poly(11f, 6f, 5f, 12f, 11f, 18f), tint, style = line)
            }
            Glyph.CHEVRON -> drawPath(poly(9.5f, 6f, 15.5f, 12f, 9.5f, 18f), tint, style = line)
            Glyph.USAGE -> {
                drawLine(tint, p(5.5f, 3.5f), p(18.5f, 3.5f), strokeWidth = line.width, cap = StrokeCap.Round)
                drawLine(tint, p(5.5f, 20.5f), p(18.5f, 20.5f), strokeWidth = line.width, cap = StrokeCap.Round)
                drawPath(
                    Path().apply {
                        moveTo(7.5f * u, 3.5f * u)
                        cubicTo(7.5f * u, 9f * u, 11f * u, 10f * u, 11f * u, 12f * u)
                        cubicTo(11f * u, 14f * u, 7.5f * u, 15f * u, 7.5f * u, 20.5f * u)
                        moveTo(16.5f * u, 3.5f * u)
                        cubicTo(16.5f * u, 9f * u, 13f * u, 10f * u, 13f * u, 12f * u)
                        cubicTo(13f * u, 14f * u, 16.5f * u, 15f * u, 16.5f * u, 20.5f * u)
                    },
                    tint,
                    style = line,
                )
                drawPath(poly(9f, 18.8f, 12f, 16.2f, 15f, 18.8f, closed = true), tint)
            }
            Glyph.HISTORY -> {
                drawLine(tint, p(3.5f, 20f), p(20.5f, 20f), strokeWidth = line.width, cap = StrokeCap.Round)
                drawPath(poly(4f, 6f, 9f, 11f, 13f, 9f, 16f, 15f, 20f, 5f), tint, style = line)
            }
            Glyph.ACCOUNTS -> listOf(6f, 12f, 18f).forEach { y ->
                drawCircle(tint, radius = 1.7f * u, center = p(5f, y))
                drawLine(tint, p(9.5f, y), p(20f, y), strokeWidth = line.width, cap = StrokeCap.Round)
            }
            Glyph.SETTINGS -> {
                drawLine(tint, p(3f, 15f), p(21f, 15f), strokeWidth = line.width, cap = StrokeCap.Round)
                listOf(4f, 8f, 12f, 16f, 20f).forEach { x -> drawLine(tint, p(x, 15f), p(x, 18.5f), strokeWidth = line.width * 0.8f, cap = StrokeCap.Round) }
                drawLine(tint, p(14f, 4.5f), p(14f, 15f), strokeWidth = line.width * 1.2f, cap = StrokeCap.Round)
                drawCircle(tint, radius = 2.2f * u, center = p(14f, 5f))
            }
            Glyph.INFO -> {
                drawCircle(tint, radius = 8.5f * u, center = p(12f, 12f), style = line)
                drawLine(tint, p(12f, 11f), p(12f, 16.5f), strokeWidth = line.width, cap = StrokeCap.Round)
                drawCircle(tint, radius = 1.1f * u, center = p(12f, 7.7f))
            }
            Glyph.ALERT -> {
                drawPath(poly(12f, 3.5f, 21f, 19.5f, 3f, 19.5f, closed = true), tint, style = line)
                drawLine(tint, p(12f, 9.5f), p(12f, 14f), strokeWidth = line.width, cap = StrokeCap.Round)
                drawCircle(tint, radius = 1.1f * u, center = p(12f, 16.8f))
            }
            Glyph.CHECK -> drawPath(poly(5.5f, 12.5f, 10f, 17f, 18.5f, 7.5f), tint, style = line)
        }
    }
}

/** A 48dp touch target holding one glyph, for header actions such as Back. */
@Composable
fun GlyphButton(glyph: Glyph, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    Box(
        modifier
            .size(48.dp)
            .clip(RoundedCornerShape(2.dp))
            .clickable(role = Role.Button, onClickLabel = label) { haptics.press(); onClick() }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(glyph) }
}

// ---------------------------------------------------------------------------------------------
// Structure: the hairline rule and the screen header.

@Composable
fun Rule(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.outlineVariant) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** Every screen's header: engraved title, with a drawn Back when the screen is nested. */
@Composable
fun BenchHeader(title: String, onBack: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .height(64.dp)
            .padding(start = if (onBack != null) 6.dp else 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) GlyphButton(Glyph.BACK, "Back", onBack, Modifier.padding(end = 4.dp))
        Text(
            title,
            style = if (onBack != null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Buttons: ink plates. Filled for the one main action, Outline for secondary, Quiet for minor.

enum class PlateStyle { FILLED, OUTLINE, QUIET }

@Composable
fun PlateButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PlateStyle = PlateStyle.FILLED,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(3.dp)
    val ground = MaterialTheme.colorScheme.background
    Box(
        modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .clip(shape)
            .then(
                when (style) {
                    PlateStyle.FILLED -> Modifier.background(color, shape)
                    PlateStyle.OUTLINE -> Modifier.border(1.dp, color.copy(alpha = 0.55f), shape)
                    PlateStyle.QUIET -> Modifier
                },
            )
            .clickable(enabled = enabled, role = Role.Button) { haptics.press(); onClick() }
            .padding(horizontal = if (style == PlateStyle.QUIET) 12.dp else 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (style == PlateStyle.QUIET) TextDecoration.Underline else null,
            ),
            color = if (style == PlateStyle.FILLED) ground else color,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Toggle: a square-shouldered lever whose block springs across and fills with ink.

/** Visual only; the whole row carries the toggle semantics and the click. */
@Composable
fun GlassToggle(checked: Boolean, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val rim = MaterialTheme.colorScheme.outline
    val raised = MaterialTheme.colorScheme.surfaceContainerHigh
    val ground = MaterialTheme.colorScheme.background
    val travel by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "toggle",
    )
    Canvas(modifier.size(54.dp, 32.dp)) {
        val hair = 1.25.dp.toPx()
        val r = CornerRadius(3.dp.toPx())
        drawRoundRect(lerp(Color.Transparent, raised, travel), cornerRadius = r)
        drawRoundRect(rim, cornerRadius = r, style = Stroke(hair))
        val pad = 4.dp.toPx()
        val block = size.height - pad * 2
        val x = pad + (size.width - block - pad * 2) * travel
        // The block stretches a little mid-travel, like it's being pushed.
        val stretch = 1f + 0.35f * (1f - abs(travel * 2f - 1f))
        drawRoundRect(
            lerp(ground, ink, travel),
            topLeft = Offset(x - (block * (stretch - 1f)) * travel, pad),
            size = Size(block * stretch, block),
            cornerRadius = CornerRadius(2.dp.toPx()),
        )
        drawRoundRect(
            lerp(rim, ink, travel),
            topLeft = Offset(x - (block * (stretch - 1f)) * travel, pad),
            size = Size(block * stretch, block),
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = Stroke(hair),
        )
    }
}

private fun lerp(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

// ---------------------------------------------------------------------------------------------
// Scale selector: engraved cells; an inverted ink plate springs to the chosen one.

@Composable
fun <T> ScaleSelector(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val rim = MaterialTheme.colorScheme.outline
    val ground = MaterialTheme.colorScheme.background
    val haptics = rememberHaptics()
    val index = options.indexOf(selected).coerceAtLeast(0)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(3.dp))
            .border(1.dp, rim, RoundedCornerShape(3.dp)),
    ) {
        val cell = maxWidth / options.size
        val plateX by animateDpAsState(
            targetValue = cell * index,
            animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
            label = "plate",
        )
        Box(Modifier.offset(x = plateX).width(cell).fillMaxHeight().background(ink))
        Row(Modifier.fillMaxWidth().fillMaxHeight().selectableGroup()) {
            options.forEachIndexed { i, option ->
                if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(rim.copy(alpha = 0.5f)))
                val isSelected = i == index
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(selected = isSelected, role = Role.RadioButton) {
                            if (!isSelected) haptics.tick()
                            onSelect(option)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(option),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium),
                        color = if (isSelected) ground else ink,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Scale slider: a ruled scale with a needle; ticks click under the thumb as it passes.

@Composable
fun ScaleSlider(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
    stateLabel: (Int) -> String,
    modifier: Modifier = Modifier,
    majorEvery: Int = 5,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val rim = MaterialTheme.colorScheme.outline
    val haptics = rememberHaptics()
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onValueChange)
    val finish by rememberUpdatedState(onValueChangeFinished)
    val needle by animateFloatAsState(
        targetValue = (value - range.first).toFloat() / (range.last - range.first),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "needle",
    )
    val edge = with(LocalDensity.current) { 10.dp.toPx() }
    fun valueAt(x: Float, width: Float): Int {
        val t = ((x - edge) / (width - edge * 2)).coerceIn(0f, 1f)
        return (range.first + t * (range.last - range.first)).roundToInt()
    }
    fun set(v: Int) {
        if (v != current) { haptics.tick(); change(v) }
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .semantics {
                stateDescription = stateLabel(value)
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), range.first.toFloat()..range.last.toFloat(), range.last - range.first - 1)
                setProgress { target ->
                    change(target.roundToInt().coerceIn(range))
                    finish()
                    true
                }
            }
            .pointerInput(range) {
                detectTapGestures { set(valueAt(it.x, size.width.toFloat())); finish() }
            }
            .pointerInput(range) {
                detectHorizontalDragGestures(
                    onDragEnd = { finish() },
                    onDragCancel = { finish() },
                ) { change, _ -> set(valueAt(change.position.x, size.width.toFloat())) }
            },
    ) {
        val hair = 1.25.dp.toPx()
        val base = size.height * 0.7f
        val span = size.width - edge * 2
        drawLine(rim, Offset(edge, base), Offset(size.width - edge, base), strokeWidth = hair)
        val steps = range.last - range.first
        for (i in 0..steps) {
            val x = edge + span * i / steps
            val v = range.first + i
            val major = v == range.first || v == range.last || v % majorEvery == 0
            drawLine(rim, Offset(x, base), Offset(x, base - if (major) 12.dp.toPx() else 6.dp.toPx()), strokeWidth = hair)
        }
        val nx = edge + span * needle
        drawLine(ink, Offset(edge, base), Offset(nx, base), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
        drawLine(ink, Offset(nx, 4.dp.toPx()), Offset(nx, base + 6.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawRoundRect(
            ink,
            topLeft = Offset(nx - 6.dp.toPx(), 0f),
            size = Size(12.dp.toPx(), 9.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx()),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Dialog: a bench slip that springs up from the table.

@Composable
fun BenchDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
) {
    Dialog(onDismissRequest = onDismiss) {
        val enter = remember { Animatable(if (motionEnabled()) 0f else 1f) }
        LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)) }
        val shape = RoundedCornerShape(4.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = (1f - enter.value) * 48.dp.toPx()
                    scaleX = 0.94f + 0.06f * enter.value
                    scaleY = 0.94f + 0.06f * enter.value
                    alpha = enter.value.coerceIn(0f, 1f)
                }
                .background(MaterialTheme.colorScheme.surfaceContainer, shape)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), shape)
                .padding(start = 24.dp, end = 12.dp, top = 24.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 12.dp).semantics { heading() })
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PlateButton(dismissLabel, onDismiss, style = PlateStyle.QUIET)
                PlateButton(confirmLabel, onConfirm)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Refreshing: the turning glass, on its own (loading) and as the pull-to-refresh indicator.

/** A small glass that keeps flipping with a spring; still under Remove animations. */
@Composable
fun TurningGlass(modifier: Modifier = Modifier, turning: Boolean = true, pull: Float = 0f) {
    val turn = remember { Animatable(0f) }
    LaunchedEffect(turning) {
        if (turning && motionEnabled()) {
            while (true) {
                turn.animateTo(turn.value + 180f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow))
                delay(260)
            }
        } else {
            turn.snapTo(0f)
        }
    }
    SandGlassMini(
        modifier.graphicsLayer { rotationZ = if (turning) turn.value else pull.coerceIn(0f, 1f) * 180f },
    )
}

@Composable
private fun SandGlassMini(modifier: Modifier) {
    val look = rememberGlassLook()
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.size(18.dp, 30.dp)) {
        drawGlass(left = 0.62f, sand = ink, look = look, alertLeft = null, stale = false)
    }
}

/**
 * Pull-to-refresh as turning the glass: a slip drops with your pull, its glass tips toward a flip,
 * gives a haptic tick when you've pulled far enough, then keeps flipping while usage is re-read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.TurningGlassIndicator(state: PullToRefreshState, isRefreshing: Boolean) {
    val haptics = rememberHaptics()
    LaunchedEffect(state) {
        snapshotFlow { state.distanceFraction >= 1f }.distinctUntilChanged().collect { past -> if (past) haptics.tick() }
    }
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .graphicsLayer {
                val f = if (isRefreshing) 1f else state.distanceFraction
                // Centered in the gap the content opens (see pullGap).
                translationY = f.coerceAtMost(PULL_STRETCH) * PULL_GAP.toPx() / 2f - 15.dp.toPx()
                alpha = (f * 1.6f).coerceIn(0f, 1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        TurningGlass(turning = isRefreshing, pull = state.distanceFraction)
    }
}

private val PULL_GAP = 72.dp
private const val PULL_STRETCH = 1.4f

/** Moves the pulled content down so the turning glass gets its own gap instead of covering a line. */
@OptIn(ExperimentalMaterial3Api::class)
fun Modifier.pullGap(state: PullToRefreshState, isRefreshing: Boolean): Modifier = graphicsLayer {
    val f = if (isRefreshing) 1f else state.distanceFraction
    translationY = f.coerceAtMost(PULL_STRETCH) * PULL_GAP.toPx()
}

// ---------------------------------------------------------------------------------------------
// Numerals that roll: a new percent slides in from the direction it moved.

@Composable
fun RollingText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            val up = (targetState.filter(Char::isDigit).toIntOrNull() ?: 0) >= (initialState.filter(Char::isDigit).toIntOrNull() ?: 0)
            val dir = if (up) 1 else -1
            (slideInVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)) { dir * it } + fadeIn()) togetherWith
                (slideOutVertically(spring(stiffness = Spring.StiffnessLow)) { -dir * it } + fadeOut())
        },
        modifier = modifier,
        label = "numeral",
    ) { Text(it, style = style, color = color) }
}

/** A gentle entrance for a block of content: springs up a few dp and fades in, once. */
@Composable
fun Arrive(index: Int, content: @Composable () -> Unit) {
    val reveal = remember { Animatable(if (motionEnabled()) 0f else 1f) }
    LaunchedEffect(Unit) {
        delay(60L * index)
        reveal.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
    }
    Box(
        Modifier.graphicsLayer {
            alpha = reveal.value.coerceIn(0f, 1f)
            translationY = (1f - reveal.value) * 24.dp.toPx()
        },
    ) { content() }
}
