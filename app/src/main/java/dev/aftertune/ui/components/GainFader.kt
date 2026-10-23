package dev.aftertune.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.aftertune.dsp.EqBands
import dev.aftertune.ui.theme.Studio
import dev.aftertune.ui.theme.StudioType

/**
 * Vertical fader for one EQ band. Relative drag (no jump on touch-down), 0.5 dB steps,
 * double-tap returns to 0 dB, haptic tick at 0 dB and at the limits, TalkBack adjustable.
 */
@Composable
fun GainFader(
    label: String,
    gainDb: Float,
    active: Boolean,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    trackHeight: androidx.compose.ui.unit.Dp = 200.dp,
    onActiveChange: (Boolean) -> Unit = {},
) {
    val c = Studio.colors
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(gainDb)
    val change by rememberUpdatedState(onChange)
    val activeChange by rememberUpdatedState(onActiveChange)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    // The thumb tracks the finger exactly while dragging; the spring only smooths programmatic changes (presets, reset).
    val shown by animateFloatAsState(gainDb, if (dragging) snap() else spring(stiffness = 1400f), label = "fader")
    val labelColor by animateColorAsState(if (dragging) c.accent else c.textMuted, label = "faderLabel")
    val accent by animateColorAsState(if (active) c.accent else c.textMuted, label = "faderAccent")
    val valueColor = if (gainDb != 0f) accent else c.textFaint
    val range = EqBands.MAX_GAIN_DB - EqBands.MIN_GAIN_DB

    fun emit(raw: Float) {
        val v = EqBands.normalizeGain(raw)
        if (v != current) {
            if (v == 0f || v == EqBands.MAX_GAIN_DB || v == EqBands.MIN_GAIN_DB) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            change(v)
        }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(EqBands.formatGain(gainDb), style = StudioType.monoSmall, color = valueColor, maxLines = 1)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .semantics {
                    contentDescription = "$label hertz"
                    stateDescription = "${EqBands.formatGain(gainDb).replace("−", "minus ")} decibels"
                    progressBarRangeInfo = ProgressBarRangeInfo(gainDb, EqBands.MIN_GAIN_DB..EqBands.MAX_GAIN_DB, steps = (range / EqBands.STEP_DB).toInt() - 1)
                    setProgress { target -> emit(target); true }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { emit(0f) })
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { dragging = true; dragValue = current; activeChange(true) },
                        onDragEnd = { dragging = false; activeChange(false) },
                        onDragCancel = { dragging = false; activeChange(false) },
                    ) { pointer, dy ->
                        pointer.consume()
                        val usable = size.height - 24.dp.toPx()
                        dragValue = (dragValue - dy / usable * range).coerceIn(EqBands.MIN_GAIN_DB, EqBands.MAX_GAIN_DB)
                        emit(dragValue)
                    }
                },
        ) {
            val cx = size.width / 2f
            val pad = 12.dp.toPx()
            val top = pad
            val bottom = size.height - pad
            fun y(db: Float) = top + (bottom - top) * (EqBands.MAX_GAIN_DB - db) / range
            val trackW = 3.dp.toPx()

            // Ticks at +12, +6, 0, -6, -12.
            for (db in listOf(12f, 6f, 0f, -6f, -12f)) {
                val tw = if (db == 0f) 9.dp.toPx() else 5.dp.toPx()
                drawLine(if (db == 0f) c.borderStrong else c.border, Offset(cx - trackW - tw, y(db)), Offset(cx - trackW - 2.dp.toPx(), y(db)), 1.dp.toPx())
            }
            drawRoundRect(c.surfaceHigh, Offset(cx - trackW / 2, top), Size(trackW, bottom - top), CornerRadius(trackW))
            val y0 = y(0f)
            val yv = y(shown)
            drawRoundRect(accent, Offset(cx - trackW / 2, minOf(y0, yv)), Size(trackW, kotlin.math.abs(yv - y0)), CornerRadius(trackW))

            val thumbW = (size.width - 8.dp.toPx()).coerceAtMost(26.dp.toPx())
            val thumbH = if (dragging) 14.dp.toPx() else 12.dp.toPx()
            drawRoundRect(c.surfaceRaised, Offset(cx - thumbW / 2, yv - thumbH / 2), Size(thumbW, thumbH), CornerRadius(4.dp.toPx()))
            drawRoundRect(
                if (dragging) accent else c.borderStrong,
                Offset(cx - thumbW / 2, yv - thumbH / 2),
                Size(thumbW, thumbH),
                CornerRadius(4.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()),
            )
            drawLine(accent, Offset(cx - thumbW / 4, yv), Offset(cx + thumbW / 4, yv), 2.dp.toPx())
        }
        Text(label, Modifier.padding(top = 2.dp), style = StudioType.monoSmall, color = labelColor, maxLines = 1)
    }
}
