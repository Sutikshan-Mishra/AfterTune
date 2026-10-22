package dev.hd450btnc.companion.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hd450btnc.companion.artwork.ArtworkState
import dev.hd450btnc.companion.artwork.ExternalTrack
import dev.hd450btnc.companion.audio.LevelAnalyzer
import dev.hd450btnc.companion.audio.PlayerState
import dev.hd450btnc.companion.audio.Recommendation
import dev.hd450btnc.companion.ui.components.ArtBackdrop
import dev.hd450btnc.companion.ui.components.ButtonStyle
import dev.hd450btnc.companion.ui.components.Glyph
import dev.hd450btnc.companion.ui.components.GlyphKind
import dev.hd450btnc.companion.ui.components.IconAction
import dev.hd450btnc.companion.ui.components.LocalContentBottomInset
import dev.hd450btnc.companion.ui.components.Notice
import dev.hd450btnc.companion.ui.components.StudioButton
import dev.hd450btnc.companion.ui.components.StudioDivider
import dev.hd450btnc.companion.ui.components.StudioSlider
import dev.hd450btnc.companion.ui.components.Tone
import dev.hd450btnc.companion.ui.theme.Shapes
import dev.hd450btnc.companion.ui.theme.Space
import dev.hd450btnc.companion.ui.theme.Studio
import dev.hd450btnc.companion.ui.theme.StudioType

/**
 * The full music player: a large cover over a softly moving blurred copy of itself, title and artist with a
 * like button, a seek bar, the usual transport row, and "Up next" / "For you" lists below. It follows the
 * in-app player, or another app's track (title, progress and controls, no seeking) while that is playing.
 */
@Composable
fun PlayerScreen(
    state: PlayerState,
    external: ExternalTrack?,
    art: ArtworkState,
    levels: LevelAnalyzer?,
    artPlaying: Boolean,
    ambientMotion: Boolean,
    artist: String?,
    liked: Boolean,
    repeat: Boolean,
    recommendations: List<Recommendation>,
    actions: PlayerActions,
    onToggleLike: () -> Unit,
    onToggleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Studio.colors
    val ready = art as? ArtworkState.Ready
    val tint = ready?.accent?.let { Color(it) } ?: c.accent
    var tab by rememberSaveable { mutableStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrub by remember { mutableFloatStateOf(0f) }
    var addMenu by remember { mutableStateOf(false) }

    // One set of values for the header: the in-app player, or another app's track while that is shown.
    val nowMs = rememberNowMs(external != null && external.playing)
    val hasAny = external != null || state.hasMedia
    val title = external?.title ?: state.current?.title ?: "Nothing playing"
    val subtitle = when {
        external != null -> external.artist.ifBlank { "Playing in another app" }
        state.hasMedia -> artist ?: "On this phone"
        else -> "Add songs to start"
    }
    val playing = external?.playing ?: state.isPlaying
    val positionMs = if (external != null) external.positionAt(nowMs).coerceAtLeast(0L) else state.positionMs
    val durationMs = if (external != null) external.durationMs else state.durationMs
    val canSeek = external == null && state.durationMs > 0
    val duration = durationMs.coerceAtLeast(1L)
    val coverScale by animateFloatAsState(if (playing) 1f else 0.9f, tween(350), label = "coverScale")

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Space.xl + LocalContentBottomInset.current),
    ) {
        item(key = "header") {
            Box(Modifier.fillMaxWidth()) {
                ArtBackdrop(art, levels, artPlaying, Modifier.matchParentSize(), ambient = ambientMotion)
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Brush.verticalGradient(0f to Color.Transparent, 0.6f to Color.Transparent, 1f to c.background)),
                )
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    // Top bar: where the music is coming from, and the add menu.
                    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(48.dp))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("PLAYING FROM", style = StudioType.monoSmall, color = c.textMuted)
                            Text(
                                if (external != null) "Another app" else "Your queue",
                                style = StudioType.label,
                                color = c.text,
                            )
                        }
                        Box {
                            IconAction(GlyphKind.PLUS, "Add music", { addMenu = true }, tint = c.text)
                            DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }, modifier = Modifier.background(c.surfaceRaised)) {
                                DropdownMenuItem(text = { Text("Files", color = c.text) }, onClick = { addMenu = false; actions.addMusic() })
                                DropdownMenuItem(text = { Text("Folder", color = c.text) }, onClick = { addMenu = false; actions.addFolder() })
                                DropdownMenuItem(text = { Text("Pink noise", color = c.text) }, onClick = { addMenu = false; actions.playReference() })
                            }
                        }
                    }
                    Spacer(Modifier.height(Space.s))
                    // Cover: shrinks a little while paused.
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .widthIn(max = 420.dp)
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .graphicsLayer { scaleX = coverScale; scaleY = coverScale }
                                .clip(Shapes.card)
                                .background(c.surfaceHigh),
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
                                Glyph(GlyphKind.WAVE, c.textFaint, size = 56.dp)
                            }
                        }
                    }
                    Spacer(Modifier.height(Space.xl))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(title, style = StudioType.display, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(subtitle, style = StudioType.body, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (external == null && state.hasMedia && state.current?.isReference == false) {
                            IconAction(
                                if (liked) GlyphKind.HEART_FILLED else GlyphKind.HEART,
                                if (liked) "Remove like" else "Like",
                                onToggleLike,
                                tint = if (liked) tint else c.textMuted,
                            )
                        }
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
                    Spacer(Modifier.height(Space.s))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconAction(
                            GlyphKind.SHUFFLE,
                            if (state.shuffle) "Shuffle on" else "Shuffle off",
                            actions.toggleShuffle,
                            tint = if (state.shuffle) tint else c.textMuted,
                            enabled = external == null && state.queue.size > 1,
                        )
                        IconAction(
                            GlyphKind.PREVIOUS,
                            "Previous",
                            if (external != null) actions.externalPrevious else actions.previous,
                            enabled = hasAny,
                        )
                        PlayButton(playing, hasAny, if (external != null) actions.externalTogglePlay else actions.togglePlay)
                        IconAction(
                            GlyphKind.NEXT,
                            "Next",
                            if (external != null) actions.externalNext else actions.next,
                            enabled = external != null || state.queue.size > 1,
                        )
                        IconAction(
                            GlyphKind.REPEAT,
                            if (repeat) "Repeat queue on" else "Repeat queue off",
                            onToggleRepeat,
                            tint = if (repeat) tint else c.textMuted,
                            enabled = external == null,
                        )
                    }
                    if (!hasAny) {
                        Spacer(Modifier.height(Space.l))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                            StudioButton("Files", actions.addMusic, glyph = GlyphKind.PLUS, modifier = Modifier.weight(1f))
                            StudioButton("Folder", actions.addFolder, glyph = GlyphKind.PLUS, modifier = Modifier.weight(1f))
                            StudioButton("Pink noise", actions.playReference, glyph = GlyphKind.WAVE, modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(Space.l))
                }
            }
        }

        item(key = "tabs") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlayerTab("Up next · ${state.queue.size}", tab == 0, tint) { tab = 0 }
                    Spacer(Modifier.width(Space.l))
                    PlayerTab("For you", tab == 1, tint) { tab = 1 }
                    Spacer(Modifier.weight(1f))
                    if (tab == 0 && state.queue.isNotEmpty()) {
                        StudioButton("Clear", actions.clear, style = ButtonStyle.GHOST, destructive = true)
                    }
                }
                StudioDivider()
            }
        }

        if (tab == 0) {
            itemsIndexed(state.queue, key = { i, item -> "q$i:${item.uri}" }) { index, item ->
                val current = external == null && index == state.currentIndex
                Row(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(Shapes.control)
                        .background(if (current) tint.copy(alpha = 0.14f) else Color.Transparent)
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
            if (state.queue.isEmpty()) {
                item(key = "emptyQueue") {
                    Text(
                        "Your queue is empty.",
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = Space.l),
                        style = StudioType.bodySmall,
                        color = c.textMuted,
                    )
                }
            }
        } else if (recommendations.isEmpty()) {
            item(key = "emptyRecs") {
                Text(
                    "Play a few songs and picks based on them show up here.",
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = Space.l),
                    style = StudioType.bodySmall,
                    color = c.textMuted,
                )
            }
        } else {
            items(recommendations, key = { "r${it.uri}" }) { rec ->
                Row(
                    Modifier
                        .padding(horizontal = 16.dp)
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
}

@Composable
private fun PlayButton(playing: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = Studio.colors
    Box(
        Modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(if (enabled) c.text else c.textFaint)
            .clickable(enabled = enabled, onClickLabel = if (playing) "Pause" else "Play", role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(if (playing) GlyphKind.PAUSE else GlyphKind.PLAY, c.background, size = 30.dp)
    }
}

@Composable
private fun PlayerTab(label: String, selected: Boolean, tint: Color, onClick: () -> Unit) {
    val c = Studio.colors
    Column(
        Modifier
            .clickable(role = Role.Tab, onClickLabel = label) { onClick() }
            .padding(vertical = Space.xs),
    ) {
        Text(label, style = StudioType.label, color = if (selected) c.text else c.textMuted)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.height(2.dp).width(24.dp).background(if (selected) tint else Color.Transparent))
    }
}
