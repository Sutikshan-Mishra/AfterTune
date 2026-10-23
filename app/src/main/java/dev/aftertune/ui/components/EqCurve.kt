package dev.aftertune.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.lerp
import android.provider.Settings
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.aftertune.dsp.EqBands
import dev.aftertune.dsp.EqResponse
import dev.aftertune.ui.theme.Studio
import dev.aftertune.ui.theme.StudioType

/** "+3.0 dB · 1 kHz" for the band being dragged. */
fun formatBandReadout(gainDb: Float, freqHz: Double): String {
    val g = java.lang.String.format(java.util.Locale.ROOT, "%.1f", kotlin.math.abs(gainDb))
    val sign = when {
        gainDb > 0.049f -> "+"
        gainDb < -0.049f -> "\u2212"
        else -> ""
    }
    val f = if (freqHz >= 1000.0) {
        val k = freqHz / 1000.0
        (if (k == Math.rint(k)) k.toInt().toString() else java.lang.String.format(java.util.Locale.ROOT, "%.1f", k)) + " kHz"
    } else {
        freqHz.toInt().toString() + " Hz"
    }
    return "$sign$g dB \u00b7 $f"
}

private const val RANGE_DB = 15f
private const val POINTS = 160

/**
 * Owns the per-band animation so only this composable recomposes on each animation frame
 * (the surrounding screen stays untouched while a preset glides).
 */
@Composable
fun AnimatedEqCurve(
    target: List<Float>,
    enabled: Boolean,
    preampDb: Float,
    modifier: Modifier = Modifier,
    focusBand: Int = -1,
    adjusting: Boolean = false,
    accent: Color? = null,
) {
    val context = LocalContext.current
    // Respect "remove animations": with the system animation scale at 0 the curve jumps straight to the target.
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val animated = List(EqBands.COUNT) { i ->
        // The band under the finger follows it exactly (no spring lag); preset changes still glide.
        val spec: AnimationSpec<Float> =
            if (reduceMotion || (adjusting && i == focusBand)) snap() else spring(stiffness = 900f)
        animateFloatAsState(target[i], spec, label = "band$i").value
    }
    EqCurveView(animated, enabled, preampDb, modifier, focusBand, adjusting, accent)
}

/**
 * Frequency response computed from the exact filter cascade the audio engine runs.
 * [gains] should already be animated by the caller so preset switches glide.
 */
@Composable
fun EqCurveView(
    gains: List<Float>,
    enabled: Boolean,
    preampDb: Float,
    modifier: Modifier = Modifier,
    focusBand: Int = -1,
    adjusting: Boolean = false,
    accent: Color? = null,
) {
    val c = Studio.colors
    val measurer = rememberTextMeasurer()
    // One coefficient set per gains change (same filters the audio engine runs), shared by the curve and the nodes.
    val coeffs = remember(gains) { EqBands.coefficients(gains, EqResponse.REFERENCE_SAMPLE_RATE) }
    val curve = remember(coeffs) {
        FloatArray(POINTS) { i ->
            EqResponse.responseDb(coeffs, EqResponse.logFrequency(i, POINTS), EqResponse.REFERENCE_SAMPLE_RATE).toFloat()
        }
    }
    val bandPoints = remember(coeffs) {
        EqBands.FREQUENCIES.map { EqResponse.responseDb(coeffs, it, EqResponse.REFERENCE_SAMPLE_RATE).toFloat() }
    }
    val path = remember { Path() }
    val fill = remember { Path() }
    val lineColor by animateColorAsState(if (enabled) (accent ?: c.accent) else c.textFaint, label = "curveColor")
    val fillAlpha by animateFloatAsState(if (enabled) 0.22f else 0f, label = "curveFill")
    // 0..1 emphasis of the band being dragged; eases out quickly on release.
    val emphasis by animateFloatAsState(if (adjusting && focusBand in 0 until EqBands.COUNT) 1f else 0f, tween(140), label = "focus")
    val bubbleStyle = remember(c.text) { StudioType.monoSmall.copy(color = c.text) }
    val labelStyle = remember(c.textFaint) { StudioType.monoSmall.copy(color = c.textFaint) }
    val freqLabels = remember(measurer, labelStyle) {
        listOf(100.0 to "100", 1000.0 to "1k", 10_000.0 to "10k").map { (f, t) -> Triple(f, t, measurer.measure(t, labelStyle)) }
    }
    val gainLabels = remember(measurer, labelStyle) {
        listOf(12f to "+12", 0f to "0", -12f to "−12").map { (db, t) -> db to measurer.measure(t, labelStyle) }
    }
    val summary = if (enabled) "Equalizer curve. Peak ${"%.1f".format(curve.maxOrNull() ?: 0f)} dB" else "Equalizer bypassed"

    Canvas(
        modifier
            .fillMaxWidth()
            .height(176.dp)
            .semantics { contentDescription = summary },
    ) {
        val w = size.width
        val h = size.height
        val labelPad = 18.dp.toPx()
        val plotTop = 6.dp.toPx()
        val plotBottom = h - labelPad
        val plotH = plotBottom - plotTop
        fun y(db: Float) = plotTop + plotH * (0.5f - db / (2 * RANGE_DB))
        fun x(freq: Double) = (EqResponse.logPosition(freq) * w).toFloat()

        // Grid: fine hairlines at decades and +/-6/12 dB, with 0 dB slightly stronger.
        for (db in listOf(-12f, -6f, 6f, 12f)) {
            drawLine(c.border, Offset(0f, y(db)), Offset(w, y(db)), 1f)
        }
        drawLine(c.borderStrong, Offset(0f, y(0f)), Offset(w, y(0f)), 1f)
        for ((f, _, m) in freqLabels) {
            drawLine(c.border, Offset(x(f), plotTop), Offset(x(f), plotBottom), 1f)
            drawText(m, topLeft = Offset(x(f) - m.size.width / 2f, h - m.size.height))
        }
        for ((db, m) in gainLabels) {
            val ty = if (db == -12f) y(db) + 2.dp.toPx() else y(db) - m.size.height - 2.dp.toPx()
            drawText(m, topLeft = Offset(4.dp.toPx(), ty))
        }

        // Faint guide at the band being adjusted, behind the curve.
        if (emphasis > 0f && focusBand in 0 until EqBands.COUNT) {
            val gx = x(EqBands.FREQUENCIES[focusBand])
            drawLine(lineColor.copy(alpha = 0.30f * emphasis), Offset(gx, plotTop), Offset(gx, plotBottom), 1.dp.toPx())
        }

        // Curve and soft fill towards the 0 dB line.
        path.reset()
        fill.reset()
        for (i in curve.indices) {
            val px = w * i / (POINTS - 1)
            val py = y(curve[i].coerceIn(-RANGE_DB, RANGE_DB))
            if (i == 0) {
                path.moveTo(px, py); fill.moveTo(px, y(0f)); fill.lineTo(px, py)
            } else {
                path.lineTo(px, py); fill.lineTo(px, py)
            }
        }
        fill.lineTo(w, y(0f))
        fill.close()
        if (fillAlpha > 0f) {
            drawPath(
                fill,
                Brush.verticalGradient(
                    0f to lineColor.copy(alpha = fillAlpha),
                    0.5f to lineColor.copy(alpha = fillAlpha * 0.03f),
                    1f to lineColor.copy(alpha = fillAlpha),
                    startY = plotTop,
                    endY = plotBottom,
                ),
            )
        }
        if (enabled) {
            // Soft glow: two wider, fainter strokes under the line.
            drawPath(path, lineColor.copy(alpha = 0.10f), style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, lineColor.copy(alpha = 0.18f), style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        drawPath(
            path,
            lineColor,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = if (enabled) null else PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
            ),
        )

        // Band nodes sit on the actual curve.
        for (i in 0 until EqBands.COUNT) {
            val center = Offset(x(EqBands.FREQUENCIES[i]), y(bandPoints[i].coerceIn(-RANGE_DB, RANGE_DB)))
            val e = if (i == focusBand) emphasis else 0f
            val r = lerp(4.5f, 8f, e).dp.toPx()
            if (e > 0f) drawCircle(lineColor.copy(alpha = 0.20f * e), 15.dp.toPx(), center)
            drawCircle(c.surface, r, center)
            drawCircle(lineColor, r, center, style = Stroke(1.6.dp.toPx()))
            if (e > 0f) drawCircle(lineColor.copy(alpha = e), 2.5.dp.toPx(), center)
        }

        // Readout above the band being dragged: "+3.0 dB · 1 kHz".
        if (enabled && emphasis > 0f && focusBand in 0 until minOf(gains.size, EqBands.COUNT)) {
            val readout = measurer.measure(formatBandReadout(gains[focusBand], EqBands.FREQUENCIES[focusBand]), bubbleStyle)
            val padX = 9.dp.toPx()
            val padY = 4.dp.toPx()
            val bw = readout.size.width + 2 * padX
            val bh = readout.size.height + 2 * padY
            val node = Offset(x(EqBands.FREQUENCIES[focusBand]), y(bandPoints[focusBand].coerceIn(-RANGE_DB, RANGE_DB)))
            val bx = (node.x - bw / 2f).coerceIn(0f, (w - bw).coerceAtLeast(0f))
            var by = node.y - 16.dp.toPx() - bh
            if (by < 0f) by = node.y + 16.dp.toPx()
            drawRoundRect(c.surfaceHigh.copy(alpha = 0.96f * emphasis), Offset(bx, by), Size(bw, bh), CornerRadius(bh / 2f))
            drawRoundRect(lineColor.copy(alpha = 0.6f * emphasis), Offset(bx, by), Size(bw, bh), CornerRadius(bh / 2f), style = Stroke(1.dp.toPx()))
            drawText(readout, topLeft = Offset(bx + padX, by + padY), alpha = emphasis)
        }

        // Preamp marker on the right edge when headroom is applied.
        if (enabled && preampDb < 0f) {
            val py = y(preampDb.coerceAtLeast(-RANGE_DB))
            drawLine(c.textMuted, Offset(w - 10.dp.toPx(), py), Offset(w, py), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}
