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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import android.os.SystemClock
import dev.hd450btnc.companion.artwork.ArtworkState
import dev.hd450btnc.companion.artwork.ExternalTrack
import dev.hd450btnc.companion.audio.Recommendation
import kotlinx.coroutines.delay
import dev.hd450btnc.companion.audio.LevelAnalyzer
import dev.hd450btnc.companion.ui.components.ArtBackdrop
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
    val playRecommendation: (Recommendation) -> Unit,
    val queueRecommendation: (Recommendation) -> Unit,
    val externalTogglePlay: () -> Unit,
    val externalNext: () -> Unit,
    val externalPrevious: () -> Unit,
)

/** Wall-clock ticker (twice a second) used to advance another app's playback position between its updates. */
@Composable
private fun rememberNowMs(active: Boolean): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(active) {
        while (active) {
            now = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    return now
}

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
    external: ExternalTrack? = null,
    ambientMotion: Boolean = false,
    recommendations: List<Recommendation> = emptyList(),
) {
    val c = Studio.colors
    var sheetOpen by remember { mutableStateOf(false) }
    val nowMs = rememberNowMs(external != null && external.playing)
    val progress = if (external != null) {
        external.fractionAt(nowMs).coerceAtLeast(0f)
    } else if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    GlassSurface(
        backdrop, Shapes.glass, c.isDark, c.surfaceRaised,
        modifier = modifier.fillMaxWidth().padding(horizontal = Space.m),
    ) {
    Box {
    // Solid dark base so nothing behind the player shows through; the soft cover art sits on top of it.
    Box(Modifier.matchParentSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.9f)))
    ArtBackdrop(art, levels, artPlaying, Modifier.matchParentSize(), ambient = ambientMotion)
    Column {
        // Thin progress line along the top edge.
        Box(Modifier.fillMaxWidth().height(2.dp).background(c.text.copy(alpha = 0.12f))) {
            Box(Modifier.fillMaxWidth(progress).height(2.dp).background(if (eqEnabled) c.accent else c.textMuted))
        }
        if (external != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = "Open queue") { sheetOpen = true }
                    .padding(start = Space.l, end = Space.xs, top = Space.xs, bottom = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(external.title, style = StudioType.label, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        external.artist.ifBlank { "Playing in another app" },
                        style = StudioType.bodySmall,
                        color = c.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconAction(GlyphKind.PREVIOUS, "Previous", actions.externalPrevious, tint = c.textMuted)
                IconAction(if (external.playing) GlyphKind.PAUSE else GlyphKind.PLAY, if (external.playing) "Pause" else "Play", actions.externalTogglePlay, filled = true)
                IconAction(GlyphKind.NEXT, "Next", actions.externalNext, tint = c.textMuted)
            }
        } else if (!state.hasMedia) {
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

    if (sheetOpen) QueueSheet(state, actions, art, external, recommendations) { sheetOpen = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(
    state: PlayerState,
    actions: PlayerActions,
    art: ArtworkState,
    external: ExternalTrack?,
    recommendations: List<Recommendation>,
    onDismiss: () -> Unit,
) {
    val c = Studio.colors
    val ready = art as? ArtworkState.Ready
    val tint = ready?.accent?.let { androidx.compose.ui.graphics.Color(it) } ?: c.accent
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var scrubbing by remember { mutableStateOf(false) }
    var scrub by remember { mutableFloatStateOf(0f) }
    var tab by remember { mutableIntStateOf(0) }

    // One set of values for the header: the in-app player, or another app's track while that is what is shown.
    val nowMs = rememberNowMs(external != null && external.playing)
    val title = external?.title ?: state.current?.title ?: "—"
    val subtitle = external?.artist?.ifBlank { "Playing in another app" }
    val playing = external?.playing ?: state.isPlaying
    val positionMs = if (external != null) external.positionAt(nowMs).coerceAtLeast(0L) else state.positionMs
    val durationMs = if (external != null) external.durationMs else state.durationMs
    val canSeek = external == null && state.durationMs > 0
    val duration = durationMs.coerceAtLeast(1L)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = c.surface,
        shape = Shapes.card,
        dragHandle = {
            Box(
                Modifier
                    .statusBarsPadding()
                    .padding(top = Space.m)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(Shapes.small)
                    .background(c.borderStrong),
            )
        },
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
            Text(title, style = StudioType.title, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = StudioType.bodySmall, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (external == null && state.error != null) {
                Spacer(Modifier.height(Space.s))
                Notice(state.error, tone = Tone.WARNING, glyph = GlyphKind.WARNING)
            }
            Spacer(Modifier.height(Space.m))
            StudioSlider(
                value = if (scrubbing) scrub else positionMs.toFloat() / duration,
                onValueChange = { scrubbing = true; scrub = it },
                onValueChangeFinished = { actions.seekTo((scrub * duration).toLong()); scrubbing = false },
                enabled = canSeek,
                contentDescription = "Playback position",
            )
            Row {
                Text(formatTime(if (scrubbing) (scrub * duration).toLong() else positionMs), style = StudioType.monoSmall, color = c.textMuted)
                Spacer(Modifier.weight(1f))
                Text(formatTime(durationMs), style = StudioType.monoSmall, color = c.textMuted)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = Space.s), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconAction(
                    GlyphKind.SHUFFLE,
                    if (state.shuffle) "Shuffle on" else "Shuffle off",
                    actions.toggleShuffle,
                    tint = if (state.shuffle) tint else c.textMuted,
                    enabled = external == null && state.queue.size > 1,
                )
                Spacer(Modifier.width(Space.m))
                IconAction(GlyphKind.PREVIOUS, "Previous", if (external != null) actions.externalPrevious else actions.previous)
                Spacer(Modifier.width(Space.l))
                IconAction(
                    if (playing) GlyphKind.PAUSE else GlyphKind.PLAY,
                    if (playing) "Pause" else "Play",
                    if (external != null) actions.externalTogglePlay else actions.togglePlay,
                    filled = true,
                )
                Spacer(Modifier.width(Space.l))
                IconAction(GlyphKind.NEXT, "Next", if (external != null) actions.externalNext else actions.next)
            }
            Spacer(Modifier.height(Space.m))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SheetTab("Queue · ${state.queue.size}", tab == 0, tint) { tab = 0 }
                Spacer(Modifier.width(Space.l))
                SheetTab("For you", tab == 1, tint) { tab = 1 }
                Spacer(Modifier.weight(1f))
                if (tab == 0) StudioButton("Clear", { actions.clear(); onDismiss() }, style = ButtonStyle.GHOST, destructive = true)
            }
            StudioDivider()
            if (tab == 0) {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                    itemsIndexed(state.queue, key = { i, item -> "$i:${item.uri}" }) { index, item ->
                        val current = external == null && index == state.currentIndex
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
            } else if (recommendations.isEmpty()) {
                Text(
                    "Play a few songs and picks based on them show up here.",
                    Modifier.fillMaxWidth().padding(vertical = Space.l),
                    style = StudioType.bodySmall,
                    color = c.textMuted,
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                    items(recommendations, key = { it.uri }) { rec ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(Shapes.control)
                                .clickable(role = Role.Button, onClickLabel = "Play") { actions.playRecommendation(rec) }
                                .padding(start = Space.m),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).padding(vertical = Space.s)) {
                                Text(rec.title, style = StudioType.label, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(rec.reason, style = StudioType.bodySmall, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (rec.inQueue) {
                                Text("In queue", Modifier.padding(end = Space.m), style = StudioType.monoSmall, color = c.textFaint)
                            } else {
                                IconAction(GlyphKind.PLUS, "Add ${rec.title} to queue", { actions.queueRecommendation(rec) }, tint = c.textMuted)
                            }
                        }
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

@Composable
private fun SheetTab(label: String, selected: Boolean, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val c = Studio.colors
    Column(
        Modifier
            .clickable(role = Role.Tab, onClickLabel = label) { onClick() }
            .padding(vertical = Space.xs),
    ) {
        Text(label, style = StudioType.label, color = if (selected) c.text else c.textMuted)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.height(2.dp).width(24.dp).background(if (selected) tint else androidx.compose.ui.graphics.Color.Transparent))
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
