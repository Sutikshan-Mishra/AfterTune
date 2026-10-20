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
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class GlyphKind {
    CURVE, HEADPHONES, SLIDERS, PLAY, PAUSE, NEXT, PREVIOUS, PLUS, CLOSE, CHECK, RESET,
    INFO, WAVE, EDIT, TRASH, BLUETOOTH, CHEVRON_DOWN, WARNING, SAVE, MIC, MUTE, SHUFFLE,
}

/**
 * Line icons drawn on a 24-unit grid with one stroke weight, so the whole app shares a single,
 * consistent icon language. Decorative: callers provide semantics on the surrounding control.
 */
@Composable
fun Glyph(kind: GlyphKind, color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    Canvas(modifier.size(size)) { drawGlyph(kind, color) }
}

private fun DrawScope.drawGlyph(kind: GlyphKind, color: Color) {
    val u = size.minDimension / 24f
    val stroke = Stroke(width = 1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float = 1.8f) =
        drawLine(color, p(x1, y1), p(x2, y2), strokeWidth = w * u, cap = StrokeCap.Round)
    fun path(fill: Boolean = false, block: Path.() -> Unit) =
        drawPath(Path().apply(block), color, style = if (fill) Fill else stroke)
    fun Path.m(x: Float, y: Float) = moveTo(x * u, y * u)
    fun Path.l(x: Float, y: Float) = lineTo(x * u, y * u)
    fun Path.c(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
        cubicTo(x1 * u, y1 * u, x2 * u, y2 * u, x3 * u, y3 * u)

    when (kind) {
        GlyphKind.CURVE -> {
            path { m(2f, 16f); c(6f, 16f, 7f, 6f, 11f, 6f); c(15f, 6f, 15f, 14f, 18f, 14f); l(22f, 14f) }
            drawCircle(color, 1.6f * u, p(11f, 6f))
        }
        GlyphKind.HEADPHONES -> {
            drawArc(color, 180f, 180f, false, p(4f, 4f), Size(16f * u, 16f * u), style = stroke)
            line(4f, 12f, 4f, 14f); line(20f, 12f, 20f, 14f)
            drawRoundRect(color, p(2.5f, 13.5f), Size(4.5f * u, 7.5f * u), CornerRadius(1.6f * u), style = stroke)
            drawRoundRect(color, p(17f, 13.5f), Size(4.5f * u, 7.5f * u), CornerRadius(1.6f * u), style = stroke)
        }
        GlyphKind.SLIDERS -> {
            line(3f, 6f, 21f, 6f); line(3f, 12f, 21f, 12f); line(3f, 18f, 21f, 18f)
            drawCircle(color, 2.4f * u, p(9f, 6f))
            drawCircle(color, 2.4f * u, p(16f, 12f))
            drawCircle(color, 2.4f * u, p(7f, 18f))
        }
        GlyphKind.PLAY -> path(fill = true) { m(8f, 5f); l(19f, 12f); l(8f, 19f); close() }
        GlyphKind.PAUSE -> {
            drawRoundRect(color, p(6.5f, 5f), Size(3.5f * u, 14f * u), CornerRadius(1f * u))
            drawRoundRect(color, p(14f, 5f), Size(3.5f * u, 14f * u), CornerRadius(1f * u))
        }
        GlyphKind.NEXT -> {
            path(fill = true) { m(5f, 6f); l(14.5f, 12f); l(5f, 18f); close() }
            line(18f, 6f, 18f, 18f, 2.2f)
        }
        GlyphKind.PREVIOUS -> {
            path(fill = true) { m(19f, 6f); l(9.5f, 12f); l(19f, 18f); close() }
            line(6f, 6f, 6f, 18f, 2.2f)
        }
        GlyphKind.SHUFFLE -> {
            path { m(3f, 7f); l(7f, 7f); c(13f, 7f, 11f, 17f, 17f, 17f); l(20f, 17f) }
            path { m(3f, 17f); l(7f, 17f); c(9f, 17f, 10f, 15.5f, 10.8f, 14f) }
            path { m(13.2f, 10f); c(14f, 8.5f, 15f, 7f, 17f, 7f); l(20f, 7f) }
            path { m(17.5f, 4.5f); l(20.5f, 7f); l(17.5f, 9.5f) }
            path { m(17.5f, 14.5f); l(20.5f, 17f); l(17.5f, 19.5f) }
        }
        GlyphKind.PLUS -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
        GlyphKind.CLOSE -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
        GlyphKind.CHECK -> path { m(5f, 12.5f); l(10f, 17f); l(19f, 7f) }
        GlyphKind.RESET -> {
            val r = 7f
            val start = -60f
            val sweep = 290f
            drawArc(color, start, sweep, false, p(12f - r, 12f - r), Size(2 * r * u, 2 * r * u), style = stroke)
            val end = (start + sweep) * PI.toFloat() / 180f
            val tip = Offset((12f + r * cos(end)) * u, (12f + r * sin(end)) * u)
            val dir = Offset(-sin(end), cos(end)) // clockwise tangent
            arrowHead(color, tip, dir, 3.6f * u, 1.8f * u)
        }
        GlyphKind.INFO -> {
            drawCircle(color, 9f * u, p(12f, 12f), style = stroke)
            line(12f, 11f, 12f, 16.5f)
            drawCircle(color, 1.2f * u, p(12f, 7.8f))
        }
        GlyphKind.WAVE -> path { m(2f, 12f); l(5f, 12f); l(7f, 6f); l(10f, 18f); l(13f, 4f); l(16f, 20f); l(18f, 9f); l(20f, 12f); l(22f, 12f) }
        GlyphKind.EDIT -> {
            path { m(5f, 19f); l(6f, 15f); l(16f, 5f); l(19f, 8f); l(9f, 18f); close() }
            line(14f, 7f, 17f, 10f)
        }
        GlyphKind.TRASH -> {
            line(4f, 7f, 20f, 7f)
            path { m(9f, 7f); l(9f, 4.5f); l(15f, 4.5f); l(15f, 7f) }
            path { m(6f, 7f); l(7f, 20f); l(17f, 20f); l(18f, 7f) }
        }
        GlyphKind.BLUETOOTH -> path { m(7f, 7.5f); l(17f, 16.5f); l(12f, 21f); l(12f, 3f); l(17f, 7.5f); l(7f, 16.5f) }
        GlyphKind.CHEVRON_DOWN -> path { m(6f, 9f); l(12f, 15f); l(18f, 9f) }
        GlyphKind.WARNING -> {
            path { m(12f, 3.5f); l(21.5f, 20f); l(2.5f, 20f); close() }
            line(12f, 9.5f, 12f, 14f)
            drawCircle(color, 1.15f * u, p(12f, 17f))
        }
        GlyphKind.SAVE -> path { m(6f, 3.5f); l(18f, 3.5f); l(18f, 20.5f); l(12f, 16f); l(6f, 20.5f); close() }
        GlyphKind.MIC -> {
            drawRoundRect(color, p(9f, 3f), Size(6f * u, 11f * u), CornerRadius(3f * u), style = stroke)
            drawArc(color, 0f, 180f, false, p(5.5f, 5f), Size(13f * u, 12f * u), style = stroke)
            line(12f, 17f, 12f, 21f)
            line(9f, 21f, 15f, 21f)
        }
        GlyphKind.MUTE -> {
            path { m(4f, 9.5f); l(8f, 9.5f); l(12.5f, 5f); l(12.5f, 19f); l(8f, 14.5f); l(4f, 14.5f); close() }
            line(16f, 9.5f, 21f, 14.5f)
            line(21f, 9.5f, 16f, 14.5f)
        }
    }
}

private fun DrawScope.arrowHead(color: Color, tip: Offset, dir: Offset, length: Float, width: Float) {
    val back = Offset(tip.x - dir.x * length, tip.y - dir.y * length)
    val normal = Offset(-dir.y, dir.x)
    drawLine(color, tip, Offset(back.x + normal.x * length * 0.6f, back.y + normal.y * length * 0.6f), width, StrokeCap.Round)
    drawLine(color, tip, Offset(back.x - normal.x * length * 0.6f, back.y - normal.y * length * 0.6f), width, StrokeCap.Round)
}
