package dev.hd450btnc.companion.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.hd450btnc.companion.ui.theme.Studio

/**
 * Headphone connection as a single glanceable object, like a device complication: a round headphone
 * button whose ring is the battery level (when the headset reports it), with a presence dot in the corner.
 * No text; the state is also announced to screen readers. Tapping opens the Settings page, where the headphone details are.
 */
@Composable
fun DeviceBadge(
    label: String,
    tone: Tone,
    battery: Int?,
    pulsing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Studio.colors
    val toneTint = toneColor(tone)
    val dotAlpha = if (pulsing) {
        val t = rememberInfiniteTransition(label = "badgePulse")
        t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "badgeDot").value
    } else 1f
    val description = buildString {
        append("Headphones: ").append(label)
        if (battery != null) append(", battery ").append(battery).append(" percent")
    }

    Box(modifier.size(48.dp).semantics(mergeDescendants = true) { contentDescription = description }) {
        Box(
            Modifier
                .align(Alignment.Center)
                .size(44.dp)
                .clip(CircleShape)
                .background(c.surfaceRaised)
                .clickable(role = Role.Button, onClickLabel = "Open headphones", onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.matchParentSize()) {
                val stroke = 2.dp.toPx()
                val topLeft = Offset(stroke / 2f, stroke / 2f)
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(c.borderStrong, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                if (battery != null) {
                    drawArc(
                        c.accent, -90f, 360f * battery.coerceIn(0, 100) / 100f, false, topLeft, arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                } else if (tone == Tone.POSITIVE) {
                    drawArc(toneTint.copy(alpha = 0.55f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                }
            }
            Glyph(GlyphKind.HEADPHONES, if (tone == Tone.NEUTRAL) c.textMuted else c.text, size = 20.dp)
        }
        Box(
            Modifier.align(Alignment.BottomEnd).size(14.dp).clip(CircleShape).background(c.background),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(8.dp).alpha(dotAlpha).clip(CircleShape).background(toneTint))
        }
    }
}
