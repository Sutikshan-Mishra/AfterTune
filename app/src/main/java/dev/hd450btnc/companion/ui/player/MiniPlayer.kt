package dev.hd450btnc.companion.ui.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import dev.hd450btnc.companion.artwork.ArtworkState
import dev.hd450btnc.companion.audio.LevelAnalyzer
import dev.hd450btnc.companion.ui.components.ArtBackdrop
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hd450btnc.companion.audio.PlayerState
import dev.hd450btnc.companion.ui.components.ButtonStyle
import dev.hd450btnc.companion.ui.components.GlassBackdrop
import dev.hd450btnc.companion.ui.components.GlassSurface
import dev.hd450btnc.companion.ui.components.StudioSlider
import dev.hd450btnc.companion.ui.components.Glyph
import dev.hd450btnc.companion.ui.components.GlyphKind
import dev.hd450btnc.companion.ui.components.IconAction
import dev.hd450btnc.companion.ui.components.Notice
import dev.hd450btnc.companion.ui.components.Overline
import dev.hd450btnc.companion.ui.components.StudioButton
import dev.hd450btnc.companion.ui.components.StudioDivider
import dev.hd450btnc.companion.ui.components.Tone
import dev.hd450btnc.companion.ui.theme.Shapes
import dev.hd450btnc.companion.ui.theme.Space
import dev.hd450btnc.companion.ui.theme.Studio
import dev.hd450btnc.companion.ui.theme.StudioType

data class PlayerActions(
    val togglePlay: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val seekTo: (Long) -> Unit,
    val playAt: (Int) -> Unit,
    val removeAt: (Int) -> Unit,
    val clear: () -> Unit,
    val addMusic: () -> Unit,
    val addFolder: () -> Unit,
    val toggleShuffle: () -> Unit,
    val playReference: () -> Unit,
)

/** Compact transport docked above the navigation bar on every screen. Tap to open the queue. */
@Composable
fun MiniPlayer(
    state: PlayerState,
    eqEnabled: Boolean,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    art: ArtworkState = ArtworkState.None,
    levels: LevelAnalyzer? = null,
    artPlaying: Boolean = false,
) {
    val c = Studio.colors
    var sheetOpen by remember { mutableStateOf(false) }
    val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f

    GlassSurface(
        backdrop, Shapes.glass, c.isDark, c.surfaceRaised,
        modifier = modifier.fillMaxWidth().padding(horizontal = Space.m),
    ) {
    Box {
    // Solid dark base so nothing behind the player shows through; the soft cover art sits on top of it.
    Box(Modifier.matchParentSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.9f)))
    ArtBackdrop(art, levels, artPlaying, Modifier.matchParentSize())
    Column {
        // Thin progress line along the top edge.
        Box(Modifier.fillMaxWidth().height(2.dp).background(c.text.copy(alpha = 0.12f))) {
            Box(Modifier.fillMaxWidth(progress).height(2.dp).background(if (eqEnabled) c.accent else c.textMuted))
        }
        if (!state.hasMedia) {
            Row(
                Modifier.fillMaxWidth().padding(start = Space.l, end = Space.s, top = Space.s, bottom = Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Nothing playing", style = StudioType.label, color = c.text)
                    Text("EQ applies to playback here", style = StudioType.bodySmall, color = c.textMuted)
                }
                StudioButton(if (state.preparingReference) "Preparing" else "Pink noise", actions.playReference, style = ButtonStyle.GHOST, glyph = GlyphKind.WAVE, enabled = !state.preparingReference)
                var addMenu by remember { mutableStateOf(false) }
                Box {
                    StudioButton("Add", { addMenu = true }, style = ButtonStyle.OUTLINE, glyph = GlyphKind.PLUS)
                    DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }, modifier = Modifier.background(c.surfaceRaised)) {
                        DropdownMenuItem(text = { Text("Files", color = c.text) }, onClick = { addMenu = false; actions.addMusic() })
                        DropdownMenuItem(text = { Text("Folder", color = c.text) }, onClick = { addMenu = false; actions.addFolder() })
                    }
                }
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = "Open queue") { sheetOpen = true }
                    .padding(start = Space.l, end = Space.xs, top = Space.xs, bottom = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(state.current?.title ?: "Ready", style = StudioType.label, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        state.error ?: when {
                            state.isBuffering -> "Loading…"
                            eqEnabled -> "In-app playback · EQ on"
                            else -> "In-app playback · EQ off"
                        },
                        style = StudioType.bodySmall,
                        color = if (state.error != null) c.error else c.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconAction(GlyphKind.PREVIOUS, "Previous", actions.previous, tint = c.textMuted)
                IconAction(if (state.isPlaying) GlyphKind.PAUSE else GlyphKind.PLAY, if (state.isPlaying) "Pause" else "Play", actions.togglePlay, filled = true)
                IconAction(GlyphKind.NEXT, "Next", actions.next, tint = c.textMuted, enabled = state.queue.size > 1)
            }
        }
    }
    }
    }

    if (sheetOpen) QueueSheet(state, actions, art) { sheetOpen = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(state: PlayerState, actions: PlayerActions, art: ArtworkState, onDismiss: () -> Unit) {
    val c = Studio.colors
    val ready = art as? ArtworkState.Ready
    val tint = ready?.accent?.let { androidx.compose.ui.graphics.Color(it) } ?: c.accent
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var scrubbing by remember { mutableStateOf(false) }
    var scrub by remember { mutableFloatStateOf(0f) }
    val duration = state.durationMs.coerceAtLeast(1L)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = c.surface,
        shape = Shapes.card,
        dragHandle = { Box(Modifier.padding(top = Space.m).size(width = 36.dp, height = 4.dp).clip(Shapes.small).background(c.borderStrong)) },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.screen)) {
            Spacer(Modifier.height(Space.m))
            // Large cover (or a calm placeholder when the track has none).
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(208.dp).clip(Shapes.card).background(c.surfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    val cover = ready?.cover
                    if (cover != null) {
                        Image(
                            bitmap = cover,
                            contentDescription = "Cover art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Glyph(GlyphKind.WAVE, c.textFaint, size = 44.dp)
                    }
                }
            }
            Spacer(Modifier.height(Space.l))
            Text(state.current?.title ?: "—", style = StudioType.title, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (state.error != null) {
                Spacer(Modifier.height(Space.s))
                Notice(state.error, tone = Tone.WARNING, glyph = GlyphKind.WARNING)
            }
            Spacer(Modifier.height(Space.m))
            StudioSlider(
                value = if (scrubbing) scrub else state.positionMs.toFloat() / duration,
                onValueChange = { scrubbing = true; scrub = it },
                onValueChangeFinished = { actions.seekTo((scrub * duration).toLong()); scrubbing = false },
                enabled = state.durationMs > 0,
                contentDescription = "Playback position",
            )
            Row {
                Text(formatTime(if (scrubbing) (scrub * duration).toLong() else state.positionMs), style = StudioType.monoSmall, color = c.textMuted)
                Spacer(Modifier.weight(1f))
                Text(formatTime(state.durationMs), style = StudioType.monoSmall, color = c.textMuted)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = Space.s), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconAction(
                    GlyphKind.SHUFFLE,
                    if (state.shuffle) "Shuffle on" else "Shuffle off",
                    actions.toggleShuffle,
                    tint = if (state.shuffle) tint else c.textMuted,
                    enabled = state.queue.size > 1,
                )
                Spacer(Modifier.width(Space.m))
                IconAction(GlyphKind.PREVIOUS, "Previous", actions.previous)
                Spacer(Modifier.width(Space.l))
                IconAction(if (state.isPlaying) GlyphKind.PAUSE else GlyphKind.PLAY, if (state.isPlaying) "Pause" else "Play", actions.togglePlay, filled = true)
                Spacer(Modifier.width(Space.l))
                IconAction(GlyphKind.NEXT, "Next", actions.next)
            }
            Spacer(Modifier.height(Space.m))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Overline("Queue · ${state.queue.size}", Modifier.weight(1f))
                StudioButton("Clear", { actions.clear(); onDismiss() }, style = ButtonStyle.GHOST, destructive = true)
            }
            StudioDivider()
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                itemsIndexed(state.queue, key = { i, item -> "$i:${item.uri}" }) { index, item ->
                    val current = index == state.currentIndex
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(Shapes.control)
                            .background(if (current) tint.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable(role = Role.Button, onClickLabel = "Play") { actions.playAt(index) }
                            .padding(start = Space.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                            if (current) {
                                Glyph(GlyphKind.WAVE, tint, size = 18.dp)
                            } else {
                                Text("${index + 1}", style = StudioType.monoSmall, color = c.textFaint)
                            }
                        }
                        Spacer(Modifier.width(Space.m))
                        Text(
                            item.title,
                            Modifier.weight(1f),
                            style = StudioType.label,
                            color = if (current) tint else c.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconAction(GlyphKind.CLOSE, "Remove ${item.title}", { actions.removeAt(index) }, tint = c.textFaint)
                    }
                }
            }
            Spacer(Modifier.height(Space.m))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                StudioButton("Files", actions.addMusic, glyph = GlyphKind.PLUS, modifier = Modifier.weight(1f))
                StudioButton("Folder", actions.addFolder, glyph = GlyphKind.PLUS, modifier = Modifier.weight(1f))
                StudioButton("Pink noise", actions.playReference, glyph = GlyphKind.WAVE, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(Space.xl))
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
