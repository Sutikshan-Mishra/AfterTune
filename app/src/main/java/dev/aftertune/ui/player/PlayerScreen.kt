package dev.aftertune.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.aftertune.artwork.ArtworkState
import dev.aftertune.artwork.ExternalTrack
import dev.aftertune.audio.LevelAnalyzer
import dev.aftertune.audio.LibraryTrack
import dev.aftertune.audio.PlayerState
import dev.aftertune.audio.TrackTitle
import dev.aftertune.audio.Recommendation
import dev.aftertune.audio.TrackSearch
import dev.aftertune.ui.components.ArtBackdrop
import dev.aftertune.ui.components.ButtonStyle
import dev.aftertune.ui.components.Glyph
import dev.aftertune.ui.components.GlyphKind
import dev.aftertune.ui.components.IconAction
import dev.aftertune.ui.components.LocalContentBottomInset
import dev.aftertune.ui.components.Notice
import dev.aftertune.ui.components.StudioButton
import dev.aftertune.ui.components.StudioDivider
import dev.aftertune.ui.components.StudioSlider
import dev.aftertune.ui.components.Tone
import dev.aftertune.ui.theme.Shapes
import dev.aftertune.ui.theme.Space
import dev.aftertune.ui.theme.Studio
import dev.aftertune.ui.theme.StudioType

/**
 * The full music player, one fixed page (it does not scroll): the cover over a softly moving blurred copy of
 * itself that runs behind the status bar, title and artist with a like button, a seek bar and the transport
 * row. "Up next" and "For you" live in a bottom sheet opened from the pill at the bottom; the Up next list has a
 * search box that covers the queue and everything the app has seen. Follows the in-app player, or another
 * app's track (title, progress and controls, no seeking) while that is playing.
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
    library: List<LibraryTrack>,
    recommendations: List<Recommendation>,
    actions: PlayerActions,
    onToggleLike: () -> Unit,
    onToggleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Studio.colors
    val ready = art as? ArtworkState.Ready
    val tint = ready?.accent?.let { Color(it) } ?: c.accent
    var showSheet by rememberSaveable { mutableStateOf(false) }
    var sheetTab by rememberSaveable { mutableStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrub by remember { mutableFloatStateOf(0f) }
    var addMenu by remember { mutableStateOf(false) }

    // One set of values for the header: the in-app player, or another app's track while that is shown.
    val nowMs = rememberNowMs(external != null && external.playing)
    val hasAny = external != null || state.hasMedia
    val title = (external?.title ?: state.current?.title)?.let(TrackTitle::pretty) ?: "Nothing playing"
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

    // The art fills the whole screen, status bar included; only the content is inset.
    Box(modifier.fillMaxSize().background(c.background)) {
        ArtBackdrop(art, levels, artPlaying, Modifier.fillMaxSize(), ambient = ambientMotion)
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.30f), 0.35f to Color.Transparent, 0.62f to Color.Transparent, 1f to c.background.copy(alpha = 0.94f))),
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = LocalContentBottomInset.current + Space.m),
        ) {
            // Top bar: where the music is coming from, and the add menu.
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(48.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PLAYING FROM", style = StudioType.monoSmall, color = c.textMuted)
                    Text(if (external != null) "Another app" else "Your queue", style = StudioType.label, color = c.text)
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
            // Cover: takes whatever height is left (square, never wider than the screen); shrinks a little while paused.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val side = minOf(minOf(maxWidth, maxHeight - Space.m), 420.dp).coerceAtLeast(120.dp)
                Box(
                    Modifier
                        .size(side)
                        .graphicsLayer { scaleX = coverScale; scaleY = coverScale }
                        .clip(Shapes.card)
                        .background(c.surfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    val cover = ready?.cover
                    if (cover != null) {
                        Image(bitmap = cover, contentDescription = "Cover art", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Glyph(GlyphKind.WAVE, c.textFaint, size = 56.dp)
                    }
                }
            }
            Spacer(Modifier.height(Space.l))
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
            Spacer(Modifier.height(Space.s))
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
                IconAction(GlyphKind.PREVIOUS, "Previous", if (external != null) actions.externalPrevious else actions.previous, enabled = hasAny)
                PlayButton(playing, hasAny, if (external != null) actions.externalTogglePlay else actions.togglePlay)
                IconAction(GlyphKind.NEXT, "Next", if (external != null) actions.externalNext else actions.next, enabled = external != null || state.queue.size > 1)
                IconAction(
                    GlyphKind.REPEAT,
                    if (repeat) "Repeat queue on" else "Repeat queue off",
                    onToggleRepeat,
                    tint = if (repeat) tint else c.textMuted,
                    enabled = external == null,
                )
            }
            Spacer(Modifier.height(Space.l))
            // Opens the queue / recommendations sheet.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(c.surfaceRaised.copy(alpha = 0.85f))
                        .clickable(role = Role.Button, onClickLabel = "Open queue") {
                            sheetTab = if (external != null) 1 else sheetTab
                            showSheet = true
                        }
                        .padding(horizontal = 20.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(GlyphKind.QUEUE, c.text, size = 18.dp)
                    Spacer(Modifier.width(Space.s))
                    Text(
                        if (external != null) "For you" else if (state.queue.isEmpty()) "Add songs" else "Up next · ${state.queue.size}",
                        style = StudioType.label,
                        color = c.text,
                    )
                }
            }
        }
    }

    if (showSheet) {
        UpNextSheet(
            state = state,
            external = external,
            library = library,
            recommendations = recommendations,
            actions = actions,
            tint = tint,
            tab = sheetTab,
            onTab = { sheetTab = it },
            onDismiss = { showSheet = false },
        )
    }
}

/** Bottom sheet on a solid surface (no cover art behind it, so the lists stay easy to read). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpNextSheet(
    state: PlayerState,
    external: ExternalTrack?,
    library: List<LibraryTrack>,
    recommendations: List<Recommendation>,
    actions: PlayerActions,
    tint: Color,
    tab: Int,
    onTab: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Studio.colors
    var query by remember { mutableStateOf("") }
    val byUri = remember(library) { library.associateBy { it.uri } }
    val inQueue = remember(state.queue, library, query) { TrackSearch.queueMatches(state.queue, library, query) }
    val inLibrary = remember(state.queue, library, query) { TrackSearch.libraryMatches(state.queue, library, query) }
    val searching = query.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        contentColor = c.text,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerTab("Up next · ${state.queue.size}", tab == 0, tint) { onTab(0) }
                Spacer(Modifier.width(Space.l))
                PlayerTab("For you", tab == 1, tint) { onTab(1) }
                Spacer(Modifier.weight(1f))
                if (tab == 0 && state.queue.isNotEmpty() && !searching) {
                    StudioButton("Clear", actions.clear, style = ButtonStyle.GHOST, destructive = true)
                }
            }
            StudioDivider()
            if (tab == 0) {
                SearchField(query, { query = it }, Modifier.padding(horizontal = 16.dp, vertical = Space.s))
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    itemsIndexed(inQueue, key = { _, i -> "q$i:${state.queue[i].uri}" }) { _, index ->
                        val item = state.queue[index]
                        val current = external == null && index == state.currentIndex
                        val sub = byUri[item.uri]?.artist?.takeIf { it.isNotBlank() }
                        Row(
                            Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                                .clip(Shapes.control)
                                .background(if (current) tint.copy(alpha = 0.14f) else Color.Transparent)
                                .clickable(role = Role.Button, onClickLabel = "Play") {
                                    actions.playAt(index)
                                    if (searching) onDismiss()
                                }
                                .padding(start = Space.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                                if (current) Glyph(GlyphKind.WAVE, tint, size = 18.dp)
                                else Text("${index + 1}", style = StudioType.monoSmall, color = c.textFaint)
                            }
                            Spacer(Modifier.width(Space.m))
                            Column(Modifier.weight(1f).padding(vertical = Space.s)) {
                                Text(TrackTitle.pretty(item.title), style = StudioType.label, color = if (current) tint else c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (sub != null) Text(sub, style = StudioType.bodySmall, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconAction(GlyphKind.CLOSE, "Remove ${TrackTitle.pretty(item.title)}", { actions.removeAt(index) }, tint = c.textFaint)
                        }
                    }
                    if (inLibrary.isNotEmpty()) {
                        item(key = "libHeader") {
                            Text(
                                "In your library",
                                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = Space.l, bottom = Space.xs),
                                style = StudioType.monoSmall,
                                color = c.textFaint,
                            )
                        }
                        items(inLibrary, key = { "l${it.uri}" }) { t ->
                            val rec = Recommendation(t.uri, t.title, t.artist, false)
                            Row(
                                Modifier
                                    .padding(horizontal = 16.dp)
                                    .fillMaxWidth()
                                    .clip(Shapes.control)
                                    .clickable(role = Role.Button, onClickLabel = "Play") {
                                        actions.playRecommendation(rec)
                                        onDismiss()
                                    }
                                    .padding(start = Space.m),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f).padding(vertical = Space.s)) {
                                    Text(TrackTitle.pretty(t.title), style = StudioType.label, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (t.artist.isNotBlank()) Text(t.artist, style = StudioType.bodySmall, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconAction(GlyphKind.PLUS, "Add ${TrackTitle.pretty(t.title)} to queue", { actions.queueRecommendation(rec) }, tint = c.textMuted)
                            }
                        }
                    }
                    if (inQueue.isEmpty() && inLibrary.isEmpty()) {
                        item(key = "none") {
                            Text(
                                if (searching) "No songs match \u201c${query.trim()}\u201d." else "Your queue is empty.",
                                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = Space.l),
                                style = StudioType.bodySmall,
                                color = c.textMuted,
                            )
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    if (recommendations.isEmpty()) {
                        item(key = "emptyRecs") {
                            Text(
                                "Play a few songs and picks based on them show up here.",
                                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = Space.l),
                                style = StudioType.bodySmall,
                                color = c.textMuted,
                            )
                        }
                    }
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
                                Text(TrackTitle.pretty(rec.title), style = StudioType.label, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(rec.reason, style = StudioType.bodySmall, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (rec.inQueue) {
                                Text("In queue", Modifier.padding(end = Space.m), style = StudioType.monoSmall, color = c.textFaint)
                            } else {
                                IconAction(GlyphKind.PLUS, "Add ${TrackTitle.pretty(rec.title)} to queue", { actions.queueRecommendation(rec) }, tint = c.textMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Studio.colors
    val focus = LocalFocusManager.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(Shapes.control)
            .background(c.surfaceHigh)
            .padding(start = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(GlyphKind.SEARCH, c.textMuted, size = 18.dp)
        Spacer(Modifier.width(Space.s))
        Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
            if (value.isEmpty()) Text("Search songs, artists, albums", style = StudioType.body, color = c.textFaint)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = StudioType.body.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) IconAction(GlyphKind.CLOSE, "Clear search", { onChange("") }, tint = c.textMuted)
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
