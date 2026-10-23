package dev.aftertune.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * "Liquid glass" for a floating element, built only from Compose graphics layers and platform
 * RenderEffects (no dependency).
 *
 * The screen content is recorded once into [GlassBackdrop.layer] and drawn normally. The glass pane is a
 * separate layer BEHIND the footer's own content: it draws a copy of the recorded content (shifted so it lines
 * up with what is behind the footer) and applies a RenderEffect to that layer only. Title, buttons and the
 * progress line are drawn above the pane, so they are never blurred.
 *
 * Effects: API 33+: blur, then a refraction/dispersion shader (AGSL) that bends content near the rounded edge.
 * API 31-32: blur only. API 26-30: translucent surface (no backdrop effect exists on those versions).
 * If the pane's effect cannot render, an almost-opaque dark base under the pane keeps the footer legible.
 */

/** Extra bottom space screens should leave so their last item clears the floating player + nav bar. */
val LocalContentBottomInset = compositionLocalOf { 0.dp }

private val GlassSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Stable
class GlassBackdrop(val layer: GraphicsLayer) {
    /** Where the recorded content starts on screen. */
    var origin by mutableStateOf(Offset.Zero)

    /** Bumped each time the content is re-recorded so the pane redraws with the fresh frame. */
    var version by mutableIntStateOf(0)
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/** Marks the content that sits behind glass. It draws normally and is also recorded into [backdrop]. */
fun Modifier.recordBackdrop(backdrop: GlassBackdrop): Modifier = this
    .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
    .drawWithContent {
        backdrop.layer.record(this, layoutDirection, IntSize(size.width.roundToInt(), size.height.roundToInt())) {
            this@drawWithContent.drawContent()
        }
        drawLayer(backdrop.layer)
        backdrop.version = backdrop.version + 1
    }

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object GlassRefraction {
    private const val AGSL = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float bevel;
uniform float strength;

half4 main(float2 p) {
    float2 halfBox = size * 0.5;
    float2 rel = p - halfBox;
    float2 q = abs(rel) - halfBox + float2(radius);
    float2 m = max(q, float2(0.0));
    float d = length(m) + min(max(q.x, q.y), 0.0) - radius;
    float t = clamp(-d / bevel, 0.0, 1.0);
    float k = pow(1.0 - t, 2.2);
    float2 sgn = float2(rel.x < 0.0 ? -1.0 : 1.0, rel.y < 0.0 ? -1.0 : 1.0);
    float2 n = float2(0.0, 0.0);
    if (length(m) > 0.001) {
        n = normalize(m) * sgn;
    } else if (q.x > q.y) {
        n = float2(sgn.x, 0.0);
    } else {
        n = float2(0.0, sgn.y);
    }
    float2 off = -n * k * strength;
    float2 lo = float2(0.0);
    float2 hi = size - float2(1.0);
    half4 cr = content.eval(clamp(p + off, lo, hi));
    half4 cg = content.eval(clamp(p + off * 0.88, lo, hi));
    half4 cb = content.eval(clamp(p + off * 0.76, lo, hi));
    return half4(cr.r, cg.g, cb.b, cg.a);
}
"""

    fun chain(w: Int, h: Int, blurPx: Float, corner: Float, bevel: Float, strength: Float): RenderEffect {
        val shader = RuntimeShader(AGSL)
        shader.setFloatUniform("size", w.toFloat(), h.toFloat())
        shader.setFloatUniform("radius", corner)
        shader.setFloatUniform("bevel", bevel)
        shader.setFloatUniform("strength", strength)
        val refract = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
        val blur = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP)
        // inner (blur) runs first, then the refraction bends the blurred pixels.
        return android.graphics.RenderEffect.createChainEffect(refract, blur).asComposeRenderEffect()
    }
}

private fun paneEffect(w: Int, h: Int, blurPx: Float, corner: Float, bevel: Float, strength: Float): RenderEffect {
    val blur = BlurEffect(blurPx, blurPx, TileMode.Clamp)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CUR_DEVELOPMENT || w <= 0 || h <= 0) return blur
    return try {
        GlassRefraction.chain(w, h, blurPx, corner, bevel, strength)
    } catch (t: Throwable) {
        blur // a shader problem must never take the app down
    }
}

/**
 * A floating glass surface over [backdrop]. [content] is drawn sharp on top of the pane. Below API 31, or with
 * no backdrop, the pane is a plain translucent fill.
 */
@Composable
fun GlassSurface(
    backdrop: GlassBackdrop?,
    shape: Shape,
    dark: Boolean,
    fallback: Color,
    modifier: Modifier = Modifier,
    corner: Dp = 28.dp,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val cornerPx = with(density) { corner.toPx() }
    val blurPx = with(density) { 14.dp.toPx() }
    val bevelPx = with(density) { 26.dp.toPx() }
    val strengthPx = with(density) { 18.dp.toPx() }
    val active = backdrop != null && GlassSupported
    val rim = remember(dark) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (dark) 0.50f else 0.90f),
                Color.White.copy(alpha = if (dark) 0.08f else 0.35f),
                Color.White.copy(alpha = if (dark) 0.20f else 0.55f),
            ),
        )
    }
    val tint = if (dark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.30f)
    val scrim = if (dark) Color.Black.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.22f)
    val wash = remember(dark) {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.12f else 0.40f), Color.Transparent))
    }

    Box(modifier.clip(shape).border(1.dp, rim, shape)) {
        if (active && backdrop != null) {
            var pos by remember { mutableStateOf(Offset.Zero) }
            var paneSize by remember { mutableStateOf(IntSize.Zero) }
            val effect = remember(paneSize, blurPx, cornerPx) { paneEffect(paneSize.width, paneSize.height, blurPx, cornerPx, bevelPx, strengthPx) }
            Box(
                Modifier
                    .matchParentSize()
                    .onGloballyPositioned { pos = it.positionInRoot(); paneSize = it.size }
                    // Near-opaque base: if the effect ever failed to render, the footer would still be legible.
                    .background(if (dark) Color.Black.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.85f))
                    .graphicsLayer {
                        renderEffect = effect
                        compositingStrategy = CompositingStrategy.Offscreen
                        clip = true
                        this.shape = shape
                    }
                    .drawBehind {
                        val observed = backdrop.version // read so the pane redraws whenever the content is re-recorded
                        val dx = pos.x - backdrop.origin.x
                        val dy = pos.y - backdrop.origin.y
                        translate(-dx, -dy) { drawLayer(backdrop.layer) }
                    },
            )
        } else {
            Box(Modifier.matchParentSize().background(fallback.copy(alpha = 0.94f)))
        }
        // Scrim, tint and a specular wash from the top edge. Above the blurred pane, below the foreground.
        Box(
            Modifier
                .matchParentSize()
                .background(if (active) scrim else Color.Transparent)
                .background(tint)
                .drawBehind { drawRect(wash, size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.55f)) },
        )
        content()
    }
}
