package com.sanwolf.daw.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.sanwolf.daw.ui.theme.SanwolfBlack
import com.sanwolf.daw.ui.theme.SanwolfGold
import com.sanwolf.daw.ui.theme.SanwolfOrange
import com.sanwolf.daw.ui.theme.SanwolfPanelBorder
import com.sanwolf.daw.ui.theme.SanwolfTextMuted
import com.sanwolf.daw.ui.theme.SanwolfTextPrimary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * FL Studio Mobile style rotary knob, SANWOLF black + gold.
 *
 * - Drag up / down to turn it (full travel = [travel]); taps never change the value.
 * - Double-tap resets to [defaultNorm] (and commits).
 * - While dragging, a value bubble shows above the knob.
 * - [steps] > 1 makes it detented (choice parameters).
 * - [bipolar] draws the arc from 12 o'clock (gains, +/- feedback).
 *
 * Values are normalised 0..1; the caller maps them (log ranges etc). [onChange] fires during the
 * drag (write the parameter, the audio thread hears it at once); [onCommit] once at release /
 * reset (one undo entry).
 */
@Composable
fun RotaryKnob(
    label: String,
    norm: Float,
    valueText: String,
    onChange: (Float) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
    defaultNorm: Float = 0f,
    enabled: Boolean = true,
    bipolar: Boolean = false,
    steps: Int = 0,
    knobSize: Dp = 38.dp,
    travel: Dp = 150.dp,
    accent: Color = SanwolfGold
) {
    var dragging by remember { mutableStateOf(false) }
    var dragNorm by remember { mutableFloatStateOf(norm) }
    val currentNorm by rememberUpdatedState(norm)
    val currentOnChange by rememberUpdatedState(onChange)
    val currentOnCommit by rememberUpdatedState(onCommit)
    val currentDefault by rememberUpdatedState(defaultNorm)
    val travelPx = with(LocalDensity.current) { travel.toPx() }.coerceAtLeast(1f)
    val shown = (if (dragging) dragNorm else norm).coerceIn(0f, 1f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .widthIn(min = knobSize)
            .semantics {
                contentDescription = "$label knob. Drag up or down to change, double-tap to reset."
                stateDescription = valueText
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(
                modifier = Modifier
                    .size(knobSize)
                    .pointerInput(enabled, steps, travelPx) {
                        if (!enabled) return@pointerInput
                        var accum = 0f
                        detectVerticalDragGestures(
                            onDragStart = {
                                accum = currentNorm.coerceIn(0f, 1f)
                                dragNorm = accum
                                dragging = true
                            },
                            onDragEnd = {
                                dragging = false
                                currentOnCommit()
                            },
                            onDragCancel = {
                                dragging = false
                                currentOnCommit()
                            },
                            onVerticalDrag = { change, dy ->
                                change.consume()
                                accum = (accum - dy / travelPx).coerceIn(0f, 1f)
                                val q = if (steps > 1) (accum * (steps - 1)).roundToInt().toFloat() / (steps - 1) else accum
                                if (q != dragNorm) {
                                    dragNorm = q
                                    currentOnChange(q)
                                }
                            }
                        )
                    }
                    .pointerInput(enabled) {
                        if (!enabled) return@pointerInput
                        detectTapGestures(onDoubleTap = {
                            currentOnChange(currentDefault)
                            currentOnCommit()
                        })
                    }
            ) {
                val stroke = size.minDimension * 0.09f
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                val arcInset = stroke / 2f + 1f
                val arcTopLeft = Offset(c.x - r + arcInset, c.y - r + arcInset)
                val arcSize = Size((r - arcInset) * 2f, (r - arcInset) * 2f)
                val on = enabled
                // Body: dark radial cap with a thin rim
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF34353B), Color(0xFF141417), SanwolfBlack),
                        center = Offset(c.x - r * 0.25f, c.y - r * 0.3f),
                        radius = r * 1.2f
                    ),
                    radius = r - stroke * 1.6f,
                    center = c
                )
                drawCircle(color = SanwolfPanelBorder, radius = r - stroke * 1.6f, center = c, style = Stroke(width = 1f))
                // Track (270 degrees, gap at the bottom)
                drawArc(
                    color = SanwolfPanelBorder,
                    startAngle = 135f, sweepAngle = 270f, useCenter = false,
                    topLeft = arcTopLeft, size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                // Value arc
                val valueAngle = 135f + 270f * shown
                val start = if (bipolar) 270f else 135f
                val sweep = valueAngle - start
                if (kotlin.math.abs(sweep) > 0.5f) {
                    val arcBrush = if (on) Brush.linearGradient(listOf(accent, SanwolfOrange), start = Offset(0f, size.height), end = Offset(size.width, 0f))
                    else Brush.linearGradient(listOf(SanwolfTextMuted, SanwolfTextMuted))
                    if (dragging && on) {
                        drawArc(
                            brush = arcBrush, startAngle = start, sweepAngle = sweep, useCenter = false,
                            topLeft = arcTopLeft, size = arcSize, alpha = 0.25f,
                            style = Stroke(width = stroke * 2.4f, cap = StrokeCap.Round)
                        )
                    }
                    drawArc(
                        brush = arcBrush, startAngle = start, sweepAngle = sweep, useCenter = false,
                        topLeft = arcTopLeft, size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                // Pointer
                val rad = (valueAngle * PI / 180.0).toFloat()
                val inner = (r - stroke * 1.6f) * 0.25f
                val outer = (r - stroke * 1.6f) * 0.85f
                drawLine(
                    color = if (on) SanwolfTextPrimary else SanwolfTextMuted,
                    start = Offset(c.x + cos(rad) * inner, c.y + sin(rad) * inner),
                    end = Offset(c.x + cos(rad) * outer, c.y + sin(rad) * outer),
                    strokeWidth = stroke * 0.8f,
                    cap = StrokeCap.Round
                )
            }
            if (dragging) {
                // Value tooltip while dragging
                Text(
                    valueText,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = SanwolfBlack,
                    maxLines = 1,
                    modifier = Modifier
                        .zIndex(2f)
                        .offset(y = -(knobSize / 2 + 10.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .background(SanwolfGold)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
        Text(
            label,
            fontSize = 9.sp,
            color = if (enabled) SanwolfTextMuted else SanwolfTextMuted.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            valueText,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = if (!enabled) SanwolfTextMuted else if (dragging) SanwolfOrange else accent,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center
        )
    }
}
