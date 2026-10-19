package com.tokenwatch.core.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tokenwatch.core.ui.theme.LocalIsDarkTheme
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random
import kotlinx.coroutines.delay

/** False when the user turned on Remove animations; every playful motion checks this. */
fun motionEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()

/** Seconds since the bench started running, read only while drawing so grains don't recompose. */
val LocalGrainClock = staticCompositionLocalOf<State<Float>> { mutableFloatStateOf(0f) }

/** Sand-surface tilt in radians, following gravity so the sand levels as the phone tilts. */
val LocalSandTilt = staticCompositionLocalOf<State<Float>> { mutableFloatStateOf(0f) }

private const val MAX_TILT = 0.35f

/**
 * Runs the bench's shared motion: one frame clock for every falling grain, and the gravity sensor
 * for the sand's tilt. Both stop while the app is in the background and under Remove animations.
 */
@Composable
fun SandBench(content: @Composable () -> Unit) {
    val clock = remember { mutableFloatStateOf(0f) }
    val tilt = remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val gravity = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Sideways tilt of the screen; smoothed and clamped so the numbers stay readable.
                val angle = atan2(event.values[0], event.values[1]).coerceIn(-MAX_TILT, MAX_TILT)
                tilt.floatValue += (angle - tilt.floatValue) * 0.15f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (motionEnabled() && gravity != null) sensors.registerListener(listener, gravity, SensorManager.SENSOR_DELAY_UI)
        onPauseOrDispose {
            sensors.unregisterListener(listener)
            tilt.floatValue = 0f
        }
    }
    LaunchedEffect(Unit) {
        if (!motionEnabled()) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) withFrameNanos { clock.floatValue = (it - start) / 1e9f }
    }
    CompositionLocalProvider(LocalGrainClock provides clock, LocalSandTilt provides tilt, content = content)
}

/**
 * A quota window as a blown sand glass: sand in the upper bulb is what's left, sand in the lower
 * bulb is what's used. Purely visual; callers describe the reading for accessibility.
 *
 * @param leftPercent 0..100, or null when the provider didn't report a percentage (empty glass).
 * @param alertUsedPercent the user's alert limit (percent used), drawn as a line on the upper bulb.
 * @param stale the last read failed: the glass is drawn in a broken outline and its sand fades.
 * @param spinning the provider is being re-read: the glass turns full sprung circles.
 */
@Composable
fun SandGlass(
    leftPercent: Double?,
    sand: Color,
    modifier: Modifier = Modifier,
    alertUsedPercent: Int? = null,
    stale: Boolean = false,
    spinning: Boolean = false,
    width: Dp = 40.dp,
    height: Dp = 72.dp,
) {
    val look = rememberGlassLook()
    val clock = LocalGrainClock.current
    val tilt = LocalSandTilt.current
    // Sand settles into a new reading with a little give.
    val left by animateFloatAsState(
        targetValue = ((leftPercent ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessVeryLow),
        label = "sand",
    )
    val turn = remember { Animatable(0f) }
    LaunchedEffect(spinning) {
        if (spinning && motionEnabled()) {
            while (true) {
                turn.animateTo(turn.value + 360f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessVeryLow))
                delay(300)
            }
        } else if (turn.value % 360f != 0f) {
            // Finish the turn in progress so the glass always lands upright.
            val upright = ceil(turn.value / 360f) * 360f
            turn.animateTo(upright, spring(dampingRatio = 0.6f))
        }
    }
    Canvas(
        modifier
            .size(width, height)
            .graphicsLayer { rotationZ = turn.value },
    ) {
        drawGlass(
            left = if (leftPercent == null) null else left,
            sand = sand,
            look = look,
            alertLeft = alertUsedPercent?.let { (100 - it) / 100f },
            stale = stale || leftPercent == null,
            time = clock.value,
            tilt = tilt.value,
        )
    }
}

/** The colors a glass is drawn with, resolved from the current theme. */
data class GlassLook(
    val rim: Color,
    val body: Color,
    val highlight: Color,
    val cap: Color,
    val capEdge: Color,
    val shadow: Color,
    val alert: Color,
) {
    companion object {
        /** For drawing outside Compose (home-screen widgets). */
        fun of(dark: Boolean, rim: Color, ink: Color, alert: Color) = GlassLook(
            rim = rim,
            body = ink.copy(alpha = if (dark) 0.05f else 0.04f),
            highlight = Color.White.copy(alpha = if (dark) 0.22f else 0.75f),
            cap = if (dark) Color(0xFF3B342C) else Color(0xFF4A453E),
            capEdge = if (dark) Color(0xFF6E6354) else Color(0xFF8A847A),
            shadow = Color.Black.copy(alpha = if (dark) 0.55f else 0.18f),
            alert = alert,
        )
    }
}

@Composable
fun rememberGlassLook(): GlassLook {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalIsDarkTheme.current
    return remember(scheme, dark) { GlassLook.of(dark, scheme.outline, scheme.onSurface, scheme.error) }
}

// Fixed grain positions (normalized), so the speckle is the same on every frame and every glass.
private val GRAINS: List<Offset> = Random(7).let { r -> List(140) { Offset(r.nextFloat(), r.nextFloat()) } }

/**
 * Draws one glass. Shared by the app, the refresh indicator, and the home-screen widgets.
 * [time] drives the falling grains (null draws a still stream); [tilt] levels the sand surface.
 */
internal fun DrawScope.drawGlass(
    left: Float?,
    sand: Color,
    look: GlassLook,
    alertLeft: Float?,
    stale: Boolean,
    time: Float? = null,
    tilt: Float = 0f,
) {
    val w = size.width
    val h = size.height
    val hair = 1.25.dp.toPx()
    val capH = (h * 0.055f).coerceAtLeast(2.5.dp.toPx())
    val top = capH
    val bottom = h - capH - 3.dp.toPx()
    val mid = (top + bottom) / 2f
    val bulb = mid - top
    val cx = w / 2f
    val neck = w * 0.05f
    val side = w * 0.14f

    val upper = Path().apply {
        moveTo(side, top)
        lineTo(w - side, top)
        cubicTo(w - side, top + bulb * 0.58f, cx + neck, mid - bulb * 0.22f, cx + neck, mid)
        lineTo(cx - neck, mid)
        cubicTo(cx - neck, mid - bulb * 0.22f, side, top + bulb * 0.58f, side, top)
        close()
    }
    val lower = Path().apply {
        moveTo(side, bottom)
        lineTo(w - side, bottom)
        cubicTo(w - side, bottom - bulb * 0.58f, cx + neck, mid + bulb * 0.22f, cx + neck, mid)
        lineTo(cx - neck, mid)
        cubicTo(cx - neck, mid + bulb * 0.22f, side, bottom - bulb * 0.58f, side, bottom)
        close()
    }

    // Contact shadow on the bench under the glass.
    drawOval(
        Brush.radialGradient(listOf(look.shadow, Color.Transparent), center = Offset(cx, h - 2.dp.toPx()), radius = w * 0.55f),
        topLeft = Offset(w * 0.02f, h - 5.dp.toPx()),
        size = Size(w * 0.96f, 5.dp.toPx()),
    )

    // The body of the glass: faint in the middle, darker toward the walls where the glass is thickest.
    val body = Brush.horizontalGradient(
        0f to look.rim.copy(alpha = 0.16f),
        0.3f to look.body,
        0.7f to look.body,
        1f to look.rim.copy(alpha = 0.16f),
    )
    drawPath(upper, body)
    drawPath(lower, body)

    val sandColor = if (stale) sand.copy(alpha = 0.5f) else sand
    val slope = tan(tilt) * (w / 2f)
    if (left != null && left > 0.001f) {
        // Bulbs narrow toward the neck, so heights follow area: half the sand fills half the bulb.
        val level = mid - sqrt(left) * bulb
        val dish = (bulb * 0.08f).coerceAtMost(4.dp.toPx())
        val surface = Path().apply {
            moveTo(0f, level - slope)
            quadraticTo(cx, level + dish * 2f, w, level + slope)
            lineTo(w, mid)
            lineTo(0f, mid)
            close()
        }
        val from = level - dish - abs(slope)
        clipPath(upper) { clipPath(surface) { drawSand(sandColor, Offset(0f, from), Size(w, mid - from)) } }
    }
    val used = 1f - (left ?: 1f)
    var pileTop = bottom
    if (left != null && used > 0.001f) {
        val level = bottom - (1f - sqrt(1f - used)) * bulb
        val heap = (bulb * 0.1f).coerceAtMost(5.dp.toPx())
        pileTop = level - heap
        val pile = Path().apply {
            moveTo(0f, bottom)
            lineTo(0f, level - slope)
            quadraticTo(cx, level - heap * 2f, w, level + slope)
            lineTo(w, bottom)
            close()
        }
        val from = pileTop - abs(slope)
        clipPath(lower) { clipPath(pile) { drawSand(sandColor, Offset(0f, from), Size(w, bottom - from)) } }
    }
    // The running stream and its falling grains, while there's sand above to fall.
    if (left != null && left > 0.001f && used > 0.001f && !stale) {
        drawLine(sandColor.copy(alpha = 0.55f), Offset(cx, mid), Offset(cx, pileTop), strokeWidth = hair * 0.8f)
        if (time != null && time > 0f) {
            val fall = (pileTop - mid).coerceAtLeast(1f)
            val grain = (hair * 0.9f).coerceAtLeast(1f)
            repeat(5) { i ->
                val p = (time * 0.9f + i / 5f) % 1f
                // Grains accelerate as they fall.
                val y = mid + p * p * fall
                drawCircle(sandColor, radius = grain, center = Offset(cx + ((i % 3) - 1) * grain * 0.6f, y))
            }
        }
    }

    // Engraved graduations on the upper bulb: a quarter, half and three quarters left.
    listOf(0.25f, 0.5f, 0.75f).forEach { mark ->
        val y = mid - sqrt(mark) * bulb
        drawLine(look.rim, Offset(0f, y), Offset(side * 0.75f, y), strokeWidth = hair)
    }
    // The user's alert line, across the bulb and out past it like an engraved mark.
    if (alertLeft != null && alertLeft in 0f..1f) {
        val y = mid - sqrt(alertLeft) * bulb
        drawLine(
            look.alert,
            Offset(0f, y),
            Offset(w, y),
            strokeWidth = hair,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
        )
    }

    val outline = Stroke(
        width = hair,
        pathEffect = if (stale) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
    )
    drawPath(upper, look.rim, style = outline)
    drawPath(lower, look.rim, style = outline)
    // Wall thickness: a fainter inner rim just inside the outer one.
    val wall = 1.6.dp.toPx()
    scale(scaleX = (w - wall * 2f) / w, scaleY = 1f - wall * 2f / h, pivot = Offset(cx, mid)) {
        drawPath(upper, look.rim.copy(alpha = 0.35f), style = Stroke(width = hair * 0.6f))
        drawPath(lower, look.rim.copy(alpha = 0.35f), style = Stroke(width = hair * 0.6f))
    }

    // Highlights: a curved streak down the left of each bulb, where the light catches the glass.
    val streak = Stroke(width = (w * 0.035f).coerceIn(1.dp.toPx(), 2.5.dp.toPx()), cap = StrokeCap.Round)
    val inset = w * 0.07f
    drawPath(
        Path().apply {
            moveTo(side + inset, top + bulb * 0.12f)
            cubicTo(side + inset, top + bulb * 0.45f, cx - neck * 3f, mid - bulb * 0.3f, cx - neck * 2.2f, mid - bulb * 0.16f)
        },
        look.highlight,
        style = streak,
    )
    drawPath(
        Path().apply {
            moveTo(side + inset, bottom - bulb * 0.12f)
            cubicTo(side + inset, bottom - bulb * 0.4f, cx - neck * 3f, mid + bulb * 0.4f, cx - neck * 2.4f, mid + bulb * 0.3f)
        },
        look.highlight.copy(alpha = look.highlight.alpha * 0.6f),
        style = streak,
    )
    // A short rim light down the right wall of the upper bulb.
    drawPath(
        Path().apply {
            moveTo(w - side - inset * 0.6f, top + bulb * 0.18f)
            cubicTo(w - side - inset * 0.6f, top + bulb * 0.34f, w - side - inset * 1.2f, top + bulb * 0.48f, w - side - inset * 1.8f, top + bulb * 0.56f)
        },
        look.highlight.copy(alpha = look.highlight.alpha * 0.45f),
        style = Stroke(width = streak.width * 0.7f, cap = StrokeCap.Round),
    )

    // Caps: solid plates the glass stands between, with a lit top edge.
    val radius = CornerRadius(capH * 0.35f)
    drawRoundRect(look.cap, Offset(0f, 0f), Size(w, capH), radius)
    drawRoundRect(look.cap, Offset(0f, bottom), Size(w, capH), radius)
    drawLine(look.capEdge, Offset(capH * 0.3f, hair * 0.5f), Offset(w - capH * 0.3f, hair * 0.5f), strokeWidth = hair * 0.8f)
    drawLine(look.capEdge, Offset(capH * 0.3f, bottom + hair * 0.5f), Offset(w - capH * 0.3f, bottom + hair * 0.5f), strokeWidth = hair * 0.8f)
}

/** Sand with grain: a fill, then fixed darker and lighter specks so it reads as a material. */
private fun DrawScope.drawSand(color: Color, origin: Offset, area: Size) {
    drawRect(color, origin, area)
    // Volume: darker against the walls and at the bottom, lifted at the surface crest.
    drawRect(
        Brush.horizontalGradient(
            0f to Color.Black.copy(alpha = 0.28f * color.alpha),
            0.35f to Color.Transparent,
            0.65f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.28f * color.alpha),
        ),
        origin,
        area,
    )
    drawRect(
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.22f * color.alpha),
            0.35f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.18f * color.alpha),
            startY = origin.y,
            endY = origin.y + area.height,
        ),
        origin,
        area,
    )
    val speck = 1.1.dp.toPx()
    fun place(o: Offset) = Offset(origin.x + o.x * area.width, origin.y + o.y * area.height)
    drawPoints(GRAINS.subList(0, 70).map(::place), PointMode.Points, Color.Black.copy(alpha = 0.22f * color.alpha), strokeWidth = speck, cap = StrokeCap.Round)
    drawPoints(GRAINS.subList(70, 140).map(::place), PointMode.Points, Color.White.copy(alpha = 0.28f * color.alpha), strokeWidth = speck, cap = StrokeCap.Round)
}
