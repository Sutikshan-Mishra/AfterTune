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
internal fun rememberNowMs(active: Boolean): Long {
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
    onOpen: () -> Unit = {},
) {
    val c = Studio.colors
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
                    .clickable(role = Role.Button, onClickLabel = "Open player") { onOpen() }
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
                    .clickable(role = Role.Button, onClickLabel = "Open player") { onOpen() }
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

}

internal fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
