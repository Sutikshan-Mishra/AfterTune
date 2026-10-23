package dev.aftertune.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.aftertune.ui.theme.Studio

/**
 * Slim, Spotify-style slider that matches the EQ faders: a 4dp track, accent fill, and a small white
 * handle that grows while you drag. 48dp tall touch area. [value] is 0..1.
 */
@Composable
fun StudioSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    stateText: String? = null,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val c = Studio.colors
    var pressed by remember { mutableStateOf(false) }
    val handle by animateDpAsState(if (pressed) 18.dp else 12.dp, tween(120), label = "handle")
    val v = value.coerceIn(0f, 1f)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics {
                this.contentDescription = contentDescription
                if (stateText != null) stateDescription = stateText
                setProgress { target ->
                    if (enabled) onValueChange(target.coerceIn(0f, 1f))
                    enabled
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val pad = 12.dp.toPx()
                    fun fraction(x: Float): Float {
                        val usable = (size.width - 2 * pad).coerceAtLeast(1f)
                        return ((x - pad) / usable).coerceIn(0f, 1f)
                    }
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    onValueChange(fraction(down.position.x))
                    drag(down.id) { change ->
                        onValueChange(fraction(change.position.x))
                        change.consume()
                    }
                    pressed = false
                    onValueChangeFinished?.invoke()
                }
            },
    ) {
        val pad = 12.dp.toPx()
        val trackH = 4.dp.toPx()
        val w = size.width - 2 * pad
        val cy = size.height / 2f
        val radius = CornerRadius(trackH / 2f)
        drawRoundRect(c.borderStrong, Offset(pad, cy - trackH / 2f), Size(w, trackH), radius)
        val x = pad + w * v
        if (enabled) {
            drawRoundRect(c.accent, Offset(pad, cy - trackH / 2f), Size(w * v, trackH), radius)
            drawCircle(c.text, handle.toPx() / 2f, Offset(x, cy))
        } else {
            drawRoundRect(c.textFaint, Offset(pad, cy - trackH / 2f), Size(w * v, trackH), radius)
        }
    }
}
