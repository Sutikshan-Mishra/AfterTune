package dev.hd450btnc.companion.ui.settings

import dev.hd450btnc.companion.ui.components.LocalContentBottomInset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import dev.hd450btnc.companion.ui.theme.Shapes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hd450btnc.companion.BuildConfig
import dev.hd450btnc.companion.dsp.EqBands
import dev.hd450btnc.companion.dsp.EqResponse
import dev.hd450btnc.companion.eq.EqState
import dev.hd450btnc.companion.eq.SavedProfile
import dev.hd450btnc.companion.settings.AppSettings
import dev.hd450btnc.companion.settings.ThemeMode
import dev.hd450btnc.companion.ui.components.ButtonStyle
import dev.hd450btnc.companion.ui.components.ConfirmDialog
import dev.hd450btnc.companion.ui.components.GlyphKind
import dev.hd450btnc.companion.ui.components.IconAction
import dev.hd450btnc.companion.ui.components.InfoRow
import dev.hd450btnc.companion.ui.components.NameDialog
import dev.hd450btnc.companion.ui.components.Overline
import dev.hd450btnc.companion.ui.components.ScreenHeader
import dev.hd450btnc.companion.ui.components.SectionTitle
import dev.hd450btnc.companion.ui.components.ExpandableRow
import dev.hd450btnc.companion.ui.components.SegmentedChoice
import dev.hd450btnc.companion.ui.components.StudioButton
import dev.hd450btnc.companion.ui.components.StudioCard
import dev.hd450btnc.companion.ui.components.StudioDivider
import dev.hd450btnc.companion.ui.components.ToggleRow
import dev.hd450btnc.companion.ui.theme.Space
import dev.hd450btnc.companion.ui.theme.Studio
import dev.hd450btnc.companion.ui.theme.StudioType

data class SettingsActions(
    val selectProfile: (String) -> Unit,
    val renameProfile: (String, String) -> Unit,
    val deleteProfile: (String) -> Unit,
    val setAutoHeadroom: (Boolean) -> Unit,
    val updateSettings: ((AppSettings) -> AppSettings) -> Unit,
    val setExternalArtwork: (Boolean) -> Unit,
    val resetAll: () -> Unit,
)

@Composable
fun SettingsScreen(eq: EqState, settings: AppSettings, actions: SettingsActions, modifier: Modifier = Modifier) {
    val c = Studio.colors
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.screen)
            .padding(bottom = Space.xl + LocalContentBottomInset.current),
    ) {
        ScreenHeader(overline = "Settings", title = "Profiles & app")

        Section("Saved profiles")
        Column {
            if (eq.profiles.isEmpty()) {
                Text(
                    "No saved profiles yet.",
                    Modifier.padding(vertical = Space.m),
                    style = StudioType.bodySmall,
                    color = c.textMuted,
                )
            } else {
                eq.profiles.forEachIndexed { i, p ->
                    if (i > 0) StudioDivider()
                    ProfileRow(
                        profile = p,
                        active = eq.activeId == p.id,
                        onSelect = { actions.selectProfile(p.id) },
                        onRename = { renaming = p.id },
                        onDelete = { deleting = p.id },
                    )
                }
            }
            Text(
                "${eq.profiles.size} of ${EqState.MAX_PROFILES}",
                Modifier.padding(bottom = Space.s, top = Space.xs),
                style = StudioType.monoSmall,
                color = c.textFaint,
            )
        }

        Section("Sound")
        Column {
            ToggleRow(
                "Auto headroom",
                "Prevents clipping.",
                eq.autoHeadroom,
                actions.setAutoHeadroom,
            )
        }

        Section("Playback")
        Column {
            ToggleRow(
                "Repeat queue",
                null,
                settings.repeatQueue,
                { on -> actions.updateSettings { it.copy(repeatQueue = on) } },
            )
            StudioDivider()
            ToggleRow(
                "Pause on disconnect",
                null,
                settings.pauseOnDisconnect,
                { on -> actions.updateSettings { it.copy(pauseOnDisconnect = on) } },
            )
            StudioDivider()
            ToggleRow(
                "Playback from other apps",
                "Cover, title and progress. Needs notification access.",
                settings.externalArtwork,
                actions.setExternalArtwork,
            )
        }

        Section("Appearance")
        SegmentedChoice(
            options = ThemeMode.entries.toList(),
            selected = settings.theme,
            label = { it.label },
            onSelect = { mode -> actions.updateSettings { it.copy(theme = mode) } },
        )

        Section("About")
        Column {
            InfoRow("App", "AfterTune")
            InfoRow("Version", BuildConfig.VERSION_NAME, mono = true)
            InfoRow("Built for", "Sennheiser HD 4.50 BTNC")
            InfoRow("Developer", "Sutikshan Mishra")
            Text(
                "No accounts, ads or tracking. Everything stays on this phone.",
                Modifier.padding(vertical = Space.s),
                style = StudioType.bodySmall,
                color = c.textMuted,
            )
            ExpandableRow("Legal and credits") {
                AboutBlock(
                    "Unofficial community project",
                    "Not made, endorsed or supported by Sennheiser or Sonova. “Sennheiser”, “HD 4.50 BTNC” and “NoiseGard” are trademarks of their respective owners, used only to identify compatible hardware.",
                )
                AboutBlock(
                    "Privacy",
                    "No accounts, ads, analytics or network access. Profiles and settings stay on this phone and are excluded from cloud backup. Bluetooth access is used only to show connection and battery status. The microphone is used only while ambient listening is on, with a visible notification; nothing is recorded or saved.",
                )
                AboutBlock(
                    "Open source",
                    "Built with Jetpack Compose, Material 3, AndroidX Core/Activity/Lifecycle and AndroidX Media3 (ExoPlayer, Session), all under the Apache License 2.0. EQ filters follow Robert Bristow-Johnson's Audio EQ Cookbook; pink noise uses Paul Kellet's filter.",
                )
            }
        }

        Spacer(Modifier.height(Space.l))
        StudioButton(
            "Restore defaults",
            { confirmReset = true },
            style = ButtonStyle.OUTLINE,
            destructive = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    eq.profiles.firstOrNull { it.id == renaming }?.let { profile ->
        NameDialog(
            title = "Rename profile",
            confirmLabel = "Rename",
            initial = profile.name,
            onConfirm = { actions.renameProfile(profile.id, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }
    eq.profiles.firstOrNull { it.id == deleting }?.let { profile ->
        ConfirmDialog(
            title = "Delete “${profile.name}”?",
            message = if (eq.activeId == profile.id) "It is in use: its sound will stay active as Custom." else "This can't be undone.",
            confirmLabel = "Delete",
            onConfirm = { actions.deleteProfile(profile.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            title = "Restore defaults?",
            message = "Deletes saved profiles, clears the queue and resets settings.",
            confirmLabel = "Restore",
            onConfirm = { actions.resetAll(); confirmReset = false },
            onDismiss = { confirmReset = false },
        )
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(Space.xl))
    SectionTitle(title, Modifier.padding(bottom = Space.xs))
}

@Composable
private fun AboutBlock(title: String, body: String) {
    Column(Modifier.padding(vertical = Space.m)) {
        Text(title, style = StudioType.label, color = Studio.colors.text)
        Spacer(Modifier.height(Space.xs))
        Text(body, style = StudioType.bodySmall, color = Studio.colors.textMuted)
    }
}

@Composable
private fun ProfileRow(profile: SavedProfile, active: Boolean, onSelect: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val c = Studio.colors
    Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .clip(Shapes.control)
                .clickable(role = Role.Button, onClickLabel = "Use profile", onClick = onSelect)
                .padding(vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileSparkline(profile.gains, if (active) c.accent else c.textMuted)
            Spacer(Modifier.width(Space.m))
            Column {
                Text(profile.name, style = StudioType.label, color = if (active) c.accent else c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (active) "Active" else "Tap to use", style = StudioType.bodySmall, color = c.textFaint)
            }
        }
        IconAction(GlyphKind.EDIT, "Rename ${profile.name}", onRename, tint = c.textMuted)
        IconAction(GlyphKind.TRASH, "Delete ${profile.name}", onDelete, tint = c.textMuted)
    }
}

/** The profile's real response, small — a quick visual fingerprint, not decoration. */
@Composable
private fun ProfileSparkline(gains: List<Float>, color: androidx.compose.ui.graphics.Color) {
    val curve = remember(gains) { EqResponse.curve(EqBands.normalizeGains(gains), 40) }
    val border = Studio.colors.border
    Canvas(Modifier.size(width = 48.dp, height = 28.dp)) {
        val mid = size.height / 2
        drawLine(border, Offset(0f, mid), Offset(size.width, mid), 1f)
        val path = Path()
        curve.forEachIndexed { i, db ->
            val x = size.width * i / (curve.size - 1)
            val y = mid - (db.coerceIn(-15f, 15f) / 15f) * (size.height / 2 - 2f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
