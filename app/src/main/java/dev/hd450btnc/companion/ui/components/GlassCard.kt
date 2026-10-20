package dev.hd450btnc.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.hd450btnc.companion.ui.theme.Shapes
import dev.hd450btnc.companion.ui.theme.Space
import dev.hd450btnc.companion.ui.theme.Studio

/**
 * Card with the same glass finish as the mini player (specular rim brightest on the top edge, a soft light
 * falling from the top, faint tint), for cards that sit inside the scrolling content. It does not sample the
 * content behind it (that would be a card sampling itself); instead it can host a [backdrop], e.g. the album
 * artwork visualizer, drawn behind everything in the card.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(Space.l),
    backdrop: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Studio.colors
    val dark = c.isDark
    val rim = remember(dark) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (dark) 0.34f else 0.90f),
                Color.White.copy(alpha = if (dark) 0.06f else 0.35f),
                Color.White.copy(alpha = if (dark) 0.14f else 0.55f),
            ),
        )
    }
    val wash = remember(dark) {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.07f else 0.35f), Color.Transparent))
    }
    Box(
        modifier
            .fillMaxWidth()
            .clip(Shapes.card)
            .background(if (dark) Color.Black else c.surface)
            .border(1.dp, rim, Shapes.card),
    ) {
        Box(Modifier.matchParentSize().background(c.surface.copy(alpha = if (backdrop != null) 0.35f else 0.80f)))
        backdrop?.invoke(this)
        Box(Modifier.matchParentSize().background(wash))
        Column(Modifier.padding(contentPadding), content = content)
    }
}
