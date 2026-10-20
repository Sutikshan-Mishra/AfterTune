package dev.hd450btnc.companion.ui.components

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.hd450btnc.companion.artwork.ArtworkState
import dev.hd450btnc.companion.audio.LevelAnalyzer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Very blurred album artwork that breathes with the music. Artwork supplies the colours; the audio analysis
 * ([levels], read-only, from the in-app player) supplies the movement. With no valid artwork ([art] is None)
 * it draws nothing, so the card stays pure black. A dark scrim keeps whatever is drawn above it readable.
 *
 * - Playing: scale/drift/brightness follow bass, mids and highs.
 * - Paused: eases to a calm, still picture. It does not claim to be live.
 * - Reduced motion (animations off in system settings): still picture only.
 * - The frame loop runs only while playing or while settling, and stops by itself.
 */
@Composable
fun ArtBackdrop(
    art: ArtworkState,
    levels: LevelAnalyzer?,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val reduced = remember {
        try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (e: Exception) {
            false
        }
    }
    val ready = art as? ArtworkState.Ready
    val fade by animateFloatAsState(if (ready != null) 1f else 0f, tween(700), label = "artFade")
    // Keep the last picture only while it fades to black; a new track's picture replaces it when it is ready.
    var shown by remember { mutableStateOf<ArtworkState.Ready?>(null) }
    if (ready != null && shown !== ready) shown = ready

    var drive by remember { mutableFloatStateOf(0f) }
    var bass by remember { mutableFloatStateOf(0f) }
    var mids by remember { mutableFloatStateOf(0f) }
    var treble by remember { mutableFloatStateOf(0f) }
    var phase by remember { mutableFloatStateOf(0f) }

    val animate = ready != null && levels != null && !reduced
    LaunchedEffect(animate, playing, ready) {
        if (!animate || levels == null) {
            drive = 0f; bass = 0f; mids = 0f; treble = 0f
            return@LaunchedEffect
        }
        var last = 0L
        while (true) {
            val settled = withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
                last = now
                val tl = if (playing) levels.level else 0f
                val tb = if (playing) levels.low else 0f
                val tm = if (playing) levels.mid else 0f
                val th = if (playing) levels.high else 0f
                drive = LevelAnalyzer.smooth(drive, tl, dt)
                bass = LevelAnalyzer.smooth(bass, tb, dt)
                mids = LevelAnalyzer.smooth(mids, tm, dt)
                treble = LevelAnalyzer.smooth(treble, th, dt)
                phase += dt * (0.25f + 0.9f * drive)
                !playing && drive < 0.004f && bass < 0.004f && mids < 0.004f && treble < 0.004f
            }
            if (settled) break
        }
    }

    val shade = remember { Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent, Color.Black.copy(alpha = 0.45f))) }
    Canvas(modifier) {
        val s = shown
        if (s == null || fade <= 0.01f) return@Canvas
        val w = size.width
        val h = size.height
        val img = s.bitmap
        val a = fade * s.strength
        val src = IntSize(img.width, img.height)

        // Slow drifting copies of the same tiny picture, scaled up so it is extremely blurred.
        fun layer(scale: Float, dx: Float, dy: Float, alpha: Float) {
            val dw = (w * scale).roundToInt()
            val dh = (h * scale).roundToInt()
            drawImage(
                image = img,
                srcSize = src,
                dstOffset = IntOffset(((w - dw) / 2f + dx).roundToInt(), ((h - dh) / 2f + dy).roundToInt()),
                dstSize = IntSize(dw, dh),
                alpha = alpha.coerceIn(0f, 1f),
                filterQuality = FilterQuality.High,
            )
        }
        val p = phase
        layer(1.45f + 0.16f * bass, sin(p * 0.7f) * w * (0.04f + 0.08f * mids), cos(p * 0.5f) * h * 0.05f, a * (0.60f + 0.35f * drive))
        layer(2.10f + 0.10f * treble, -sin(p * 0.45f + 1.7f) * w * (0.05f + 0.07f * treble), -cos(p * 0.6f) * h * 0.06f, a * (0.35f + 0.30f * treble))

        // Darkening keeps readings legible; it lifts slightly with the music but never goes below a safe floor.
        val scrim = (0.62f - 0.16f * drive).coerceAtLeast(0.46f)
        drawRect(Color.Black.copy(alpha = scrim * fade))
        drawRect(shade)
    }
}
