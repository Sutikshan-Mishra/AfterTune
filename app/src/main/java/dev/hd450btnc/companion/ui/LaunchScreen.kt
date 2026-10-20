package dev.hd450btnc.companion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.hd450btnc.companion.ui.theme.Space
import dev.hd450btnc.companion.ui.theme.Studio
import dev.hd450btnc.companion.ui.theme.StudioType

const val DEVELOPER_CREDIT = "Developed by Sutikshan Mishra"

/**
 * In-app launch screen. Android's system splash cannot place text at the bottom (its branding slot
 * is an image, API 31+ only), so this screen carries the credit. It is shown only while start-up work
 * runs (see AppRoot), bounded by a short minimum so the credit is readable.
 */
@Composable
fun LaunchScreen() {
    val c = Studio.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Canvas(Modifier.size(72.dp)) {
                val u = size.minDimension / 24f
                val stroke = Stroke(width = 1.8f * u, cap = StrokeCap.Round)
                drawArc(c.text, 180f, 180f, false, Offset(4f * u, 3f * u), Size(16f * u, 16f * u), style = stroke)
                drawRoundRect(c.text, Offset(2.5f * u, 12f * u), Size(4.5f * u, 8f * u), CornerRadius(1.6f * u))
                drawRoundRect(c.text, Offset(17f * u, 12f * u), Size(4.5f * u, 8f * u), CornerRadius(1.6f * u))
                val p = Path().apply {
                    moveTo(8.5f * u, 17f * u)
                    cubicTo(10f * u, 13.5f * u, 11f * u, 13.5f * u, 12f * u, 16f * u)
                    cubicTo(13f * u, 18.5f * u, 14f * u, 18.5f * u, 15.5f * u, 15f * u)
                }
                drawPath(p, c.accent, style = Stroke(width = 1.6f * u, cap = StrokeCap.Round))
            }
            Spacer(Modifier.height(Space.l))
            Text("AfterTune", Modifier.semantics { heading() }, style = StudioType.title, color = c.text)
            Spacer(Modifier.height(Space.xs))
            Text("Unofficial HD 4.50 BTNC companion", style = StudioType.bodySmall, color = c.textMuted)
        }
        Text(
            DEVELOPER_CREDIT,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = Space.screen, vertical = Space.xl),
            style = StudioType.bodySmall,
            color = c.textMuted,
            textAlign = TextAlign.Center,
        )
    }
}
