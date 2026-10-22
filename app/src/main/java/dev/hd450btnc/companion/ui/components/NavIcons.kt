package dev.hd450btnc.companion.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

enum class NavIconKind { SOUND, PLAYER, AMBIENT, SETTINGS }

/**
 * Bottom-navigation icons with two states, like the big streaming apps: an outline when idle and a solid,
 * filled shape when selected. Drawn on a 24-unit grid.
 */
@Composable
fun NavIcon(kind: NavIconKind, selected: Boolean, color: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) { drawNavIcon(kind, selected, color) }
}

private fun DrawScope.drawNavIcon(kind: NavIconKind, selected: Boolean, color: Color) {
    val u = size.minDimension / 24f
    val sw = 1.8f
    val stroke = Stroke(width = sw * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, p(x1, y1), p(x2, y2), strokeWidth = sw * u, cap = StrokeCap.Round)

    // A rounded box that is solid when selected and an outline (same outer size) otherwise.
    fun box(x: Float, y: Float, w: Float, h: Float, r: Float) {
        if (selected) {
            drawRoundRect(color, p(x, y), Size(w * u, h * u), CornerRadius(r * u))
        } else {
            val i = sw / 2f
            drawRoundRect(color, p(x + i, y + i), Size((w - sw) * u, (h - sw) * u), CornerRadius(max(r - i, 0f) * u), style = stroke)
        }
    }

    when (kind) {
        NavIconKind.SOUND -> {
            val xs = floatArrayOf(2.8f, 7.9f, 13.0f, 18.1f)
            val hs = floatArrayOf(10f, 18f, 14f, 8f)
            for (i in xs.indices) box(xs[i], 12f - hs[i] / 2f, 3.4f, hs[i], 1.7f)
        }
        NavIconKind.AMBIENT -> {
            box(8.5f, 2.5f, 7f, 12f, 3.5f)
            drawArc(color, 0f, 180f, false, p(5f, 7f), Size(14f * u, 12f * u), style = stroke)
            line(12f, 19.2f, 12f, 21.8f)
            line(8.5f, 21.8f, 15.5f, 21.8f)
        }
        NavIconKind.PLAYER -> {
            // A rounded play triangle: outline when idle, solid (with the same rounded corners) when selected.
            val tri = Path().apply {
                moveTo(7.5f * u, 4.8f * u)
                lineTo(19.2f * u, 12f * u)
                lineTo(7.5f * u, 19.2f * u)
                close()
            }
            if (selected) drawPath(tri, color)
            drawPath(tri, color, style = Stroke(width = (sw + 1.2f) * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        NavIconKind.SETTINGS -> {
            val ys = floatArrayOf(6f, 12f, 18f)
            val xs = floatArrayOf(15f, 10f, 13f)
            val knob = 2.7f
            val gap = knob + 1.2f
            for (i in ys.indices) {
                line(3f, ys[i], xs[i] - gap, ys[i])
                line(xs[i] + gap, ys[i], 21f, ys[i])
                if (selected) {
                    drawCircle(color, knob * u, p(xs[i], ys[i]))
                } else {
                    drawCircle(color, (knob - sw / 2f) * u, p(xs[i], ys[i]), style = stroke)
                }
            }
        }
    }
}
