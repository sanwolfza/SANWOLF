package com.sanwolf.daw.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanwolf.daw.R
import kotlinx.coroutines.delay

/** Brand gold sampled from the logo artwork (#DFA22B). */
private val SanwolfGold = Color(0xFFDFA22B)
private val SanwolfGoldLight = Color(0xFFFFD36B)
private val SanwolfGoldDeep = Color(0xFF9A6A12)

/**
 * Wraps the app: on a cold start shows [SanwolfLoadingScreen] (no tap needed), then [content].
 * Survives configuration changes / process recreation via rememberSaveable, so it only shows once.
 */
@Composable
fun SanwolfSplashGate(content: @Composable () -> Unit) {
    var loaded by rememberSaveable { mutableStateOf(false) }
    if (loaded) {
        content()
    } else {
        SanwolfLoadingScreen(onFinished = { loaded = true })
    }
}

/**
 * Loading screen: logo inside slowly rotating / pulsing gold rings, "LOADING..." and a gold progress bar.
 *
 * @param progress optional real progress source (0..1). When null a smooth timed fill is used.
 * @param minDurationMs how long the timed fill takes (also the minimum time on screen).
 */
@Composable
fun SanwolfLoadingScreen(
    onFinished: () -> Unit,
    progress: (() -> Float)? = null,
    minDurationMs: Int = 2200,
) {
    val finish by rememberUpdatedState(onFinished)
    val timed = remember { Animatable(0f) }
    val fadeIn = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        fadeIn.animateTo(1f, tween(450, easing = LinearEasing))
    }
    LaunchedEffect(Unit) {
        timed.animateTo(1f, tween(minDurationMs, easing = FastOutSlowInEasing))
        // If a real source is attached, wait (bounded) until it reports done.
        if (progress != null) {
            var waited = 0
            while (progress().coerceIn(0f, 1f) < 1f && waited < 8000) {
                delay(50); waited += 50
            }
        }
        delay(250)
        finish()
    }

    val shown = if (progress != null) minOf(timed.value, progress().coerceIn(0f, 1f)) else timed.value
    val status = when {
        shown < 0.35f -> "Loading sounds…"
        shown < 0.7f -> "Tuning instruments…"
        shown < 0.98f -> "Warming up the mixer…"
        else -> "Ready"
    }

    val pulse = rememberInfiniteTransition(label = "splashPulse")
    val rotation by pulse.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
        label = "ringRotation"
    )
    val glow by pulse.animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ringGlow"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // App is landscape: size the ring from the short side.
        val ringSize = (if (maxHeight < maxWidth) maxHeight else maxWidth) * 0.62f
        Column(
            modifier = Modifier.alpha(fadeIn.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.size(ringSize), contentAlignment = Alignment.Center) {
                GoldRings(rotation = rotation, glow = glow, modifier = Modifier.fillMaxSize())
                Image(
                    painter = painterResource(R.drawable.sanwolf_logo),
                    contentDescription = "SANWOLF",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(ringSize * 0.74f)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "LOADING...",
                color = SanwolfGold,
                fontSize = 14.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 6.sp
            )
            Spacer(Modifier.height(10.dp))
            GoldProgressBar(
                fraction = shown,
                glow = glow,
                modifier = Modifier
                    .width(ringSize * 0.9f)
                    .height(12.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = status.uppercase(),
                color = SanwolfGold.copy(alpha = 0.8f),
                fontSize = 10.sp,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
private fun GoldRings(rotation: Float, glow: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val c = center
        val r = size.minDimension / 2f
        // Soft radial glow behind the logo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(SanwolfGold.copy(alpha = 0.18f * glow), Color.Transparent),
                center = c, radius = r
            ),
            radius = r, center = c
        )
        // Outer glowing ring (wide faint stroke + crisp stroke)
        drawCircle(SanwolfGold.copy(alpha = 0.18f * glow), radius = r * 0.92f, center = c, style = Stroke(width = r * 0.06f))
        drawCircle(SanwolfGold.copy(alpha = 0.9f * glow), radius = r * 0.92f, center = c, style = Stroke(width = r * 0.012f))
        // Inner thin rings
        drawCircle(SanwolfGold.copy(alpha = 0.35f), radius = r * 0.80f, center = c, style = Stroke(width = r * 0.006f))
        drawCircle(SanwolfGold.copy(alpha = 0.18f), radius = r * 0.70f, center = c, style = Stroke(width = r * 0.004f))
        // Rotating bright arcs
        rotate(rotation, pivot = c) {
            drawArc(
                brush = Brush.sweepGradient(listOf(Color.Transparent, SanwolfGoldLight, Color.Transparent), center = c),
                startAngle = 0f, sweepAngle = 120f, useCenter = false,
                topLeft = Offset(c.x - r * 0.86f, c.y - r * 0.86f),
                size = androidx.compose.ui.geometry.Size(r * 1.72f, r * 1.72f),
                style = Stroke(width = r * 0.014f)
            )
        }
        rotate(-rotation * 0.6f, pivot = c) {
            drawArc(
                color = SanwolfGold.copy(alpha = 0.55f),
                startAngle = 200f, sweepAngle = 70f, useCenter = false,
                topLeft = Offset(c.x - r * 0.75f, c.y - r * 0.75f),
                size = androidx.compose.ui.geometry.Size(r * 1.5f, r * 1.5f),
                style = Stroke(width = r * 0.008f)
            )
        }
        // Light flare at the top of the outer ring
        val flare = Offset(c.x, c.y - r * 0.92f)
        drawCircle(
            brush = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f * glow), SanwolfGold.copy(alpha = 0.4f * glow), Color.Transparent), center = flare, radius = r * 0.12f),
            radius = r * 0.12f, center = flare
        )
        drawLine(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, SanwolfGoldLight.copy(alpha = 0.8f * glow), Color.Transparent), startX = flare.x - r * 0.35f, endX = flare.x + r * 0.35f),
            start = Offset(flare.x - r * 0.35f, flare.y), end = Offset(flare.x + r * 0.35f, flare.y),
            strokeWidth = r * 0.006f
        )
    }
}

@Composable
private fun GoldProgressBar(fraction: Float, glow: Float, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF0E0B05))
            .border(1.dp, SanwolfGold.copy(alpha = 0.85f), shape)
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(SanwolfGoldLight, SanwolfGold, SanwolfGoldDeep.copy(alpha = 0.6f + 0.4f * glow))
                    )
                )
        )
    }
}
