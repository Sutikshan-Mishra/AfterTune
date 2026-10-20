package dev.hd450btnc.companion.ui.sound

import dev.hd450btnc.companion.ui.components.LocalContentBottomInset
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.hd450btnc.companion.bt.ConnectionStatus
import dev.hd450btnc.companion.bt.HeadsetState
import dev.hd450btnc.companion.dsp.EqBands
import dev.hd450btnc.companion.eq.BuiltInPreset
import dev.hd450btnc.companion.eq.EqController
import dev.hd450btnc.companion.eq.EqState
import dev.hd450btnc.companion.systemeq.SystemEqState
import dev.hd450btnc.companion.ui.components.ButtonStyle
import dev.hd450btnc.companion.artwork.ArtworkState
import dev.hd450btnc.companion.audio.LevelAnalyzer
import dev.hd450btnc.companion.ui.components.AnimatedEqCurve
import dev.hd450btnc.companion.ui.components.ArtBackdrop
import dev.hd450btnc.companion.ui.components.GlassCard
import dev.hd450btnc.companion.ui.components.GainFader
import dev.hd450btnc.companion.ui.components.GlyphKind
import dev.hd450btnc.companion.ui.components.Notice
import dev.hd450btnc.companion.ui.components.Overline
import dev.hd450btnc.companion.ui.components.PresetChip
import dev.hd450btnc.companion.ui.components.DeviceBadge
import dev.hd450btnc.companion.ui.components.StudioButton
import dev.hd450btnc.companion.ui.components.SectionTitle
import dev.hd450btnc.companion.ui.components.ExpandableRow
import dev.hd450btnc.companion.ui.components.StudioCard
import dev.hd450btnc.companion.ui.components.StudioSwitch
import dev.hd450btnc.companion.ui.components.Tone
import dev.hd450btnc.companion.ui.components.NameDialog
import dev.hd450btnc.companion.ui.theme.Space
import dev.hd450btnc.companion.ui.theme.Studio
import dev.hd450btnc.companion.ui.theme.StudioType

data class SoundActions(
    val selectPreset: (String) -> Unit,
    val setBand: (Int, Float) -> Unit,
    val reset: () -> Unit,
    val setEnabled: (Boolean) -> Unit,
    val saveProfile: (String) -> Unit,
    val openHeadphones: () -> Unit,
)

@Composable
fun SoundScreen(
    eq: EqState,
    headset: HeadsetState,
    systemEq: SystemEqState,
    globalMix: Boolean,
    panelRequest: String?,
    actions: SoundActions,
    systemEqActions: SystemEqActions,
    modifier: Modifier = Modifier,
    art: ArtworkState = ArtworkState.None,
    levels: LevelAnalyzer? = null,
    playing: Boolean = false,
) {
    val c = Studio.colors
    var showSave by rememberSaveable { mutableStateOf(false) }
    // Which band the finger is on (kept after release so the highlight can fade out on the right point).
    var focusBand by remember { mutableIntStateOf(0) }
    var adjusting by remember { mutableStateOf(false) }

    // The curve animates preset switches inside AnimatedEqCurve; faders animate themselves.
    val preamp = remember(eq.gains, eq.autoHeadroom) { eq.preampDb }
    val peakDb = remember(eq.gains) { eq.peakDb }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.screen)
            .padding(bottom = Space.xl + LocalContentBottomInset.current),
    ) {
        // Compact header: app name over a short subtitle, with the access/connection pill kept beside it.
        Row(
            Modifier.fillMaxWidth().padding(top = Space.l, bottom = Space.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = Space.m)) {
                Text(MAIN_TITLE, Modifier.semantics { heading() }, style = StudioType.display, color = c.text)
                Spacer(Modifier.height(2.dp))
                Text(MAIN_SUBTITLE, style = StudioType.bodySmall, color = c.textMuted)
            }
            val (label, tone) = headerStatus(headset)
            DeviceBadge(label, tone, headset.target?.batteryPercent, pulsing = !headset.ready, onClick = actions.openHeadphones)
        }

        GlassCard(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.l),
            backdrop = { ArtBackdrop(art, levels, playing, Modifier.matchParentSize()) },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Overline("Response")
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (eq.enabled) activeLabel(eq) else "Bypassed",
                        style = StudioType.heading,
                        color = if (eq.enabled) c.text else c.textMuted,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Overline("Preamp")
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (!eq.enabled) "—" else if (preamp == 0f) "0.0 dB" else "−${"%.1f".format(-preamp)} dB",
                        style = StudioType.monoValue,
                        color = c.textMuted,
                    )
                }
            }
            Spacer(Modifier.height(Space.m))
            AnimatedEqCurve(
                eq.gains, eq.enabled, preamp,
                focusBand = focusBand, adjusting = adjusting,
                accent = (art as? ArtworkState.Ready)?.accent?.let { androidx.compose.ui.graphics.Color(it) },
            )
            Spacer(Modifier.height(Space.s))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StudioSwitch(checked = eq.enabled, onCheckedChange = actions.setEnabled)
                Spacer(Modifier.width(Space.m))
                Text(if (eq.enabled) "EQ on" else "EQ off", style = StudioType.label, color = c.text, modifier = Modifier.weight(1f))
                StudioButton(
                    "Reset",
                    actions.reset,
                    style = ButtonStyle.GHOST,
                    glyph = GlyphKind.RESET,
                    enabled = !eq.isFlat,
                )
            }
        }

        AnimatedVisibility(visible = !eq.autoHeadroom && eq.enabled && peakDb > 0.0) {
            Notice(
                "Auto headroom is off. Boosts up to +${"%.1f".format(peakDb)} dB can clip.",
                tone = Tone.WARNING,
                glyph = GlyphKind.WARNING,
                modifier = Modifier.padding(top = Space.m),
            )
        }

        Spacer(Modifier.height(Space.xl))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Presets", Modifier.weight(1f))
            Text(
                if (eq.activeId == EqState.CUSTOM_ID || BuiltInPreset.byId(eq.activeId) != null) "Edits go to Custom" else "Saved profile",
                style = StudioType.bodySmall,
                color = c.textFaint,
            )
        }
        Spacer(Modifier.height(Space.s))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            BuiltInPreset.entries.forEach { p ->
                PresetChip(p.label, eq.activeId == p.id, { actions.selectPreset(p.id) })
            }
            PresetChip("Custom", eq.activeId == EqState.CUSTOM_ID, { actions.selectPreset(EqState.CUSTOM_ID) })
            eq.profiles.forEach { p ->
                PresetChip(p.name, eq.activeId == p.id, { actions.selectPreset(p.id) })
            }
        }

        Spacer(Modifier.height(Space.xl))
        GlassCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.s, vertical = Space.l)) {
            Row(Modifier.padding(horizontal = Space.s), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Overline("10-band EQ")
                    Spacer(Modifier.height(2.dp))
                    Text("±12 dB · double-tap a fader to zero", style = StudioType.bodySmall, color = c.textFaint)
                }
                StudioButton(
                    "Save",
                    { showSave = true },
                    style = ButtonStyle.OUTLINE,
                    glyph = GlyphKind.SAVE,
                    enabled = EqController.canSaveProfile(eq),
                )
            }
            Spacer(Modifier.height(Space.l))
            Row(Modifier.fillMaxWidth()) {
                for (i in 0 until EqBands.COUNT) {
                    GainFader(
                        label = EqBands.LABELS[i],
                        gainDb = eq.gains[i],
                        active = eq.enabled,
                        onChange = { actions.setBand(i, it) },
                        onActiveChange = { on -> if (on) focusBand = i; adjusting = on },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.l))
        if (panelRequest != null) {
            SystemEqCard(systemEq, globalMix, panelRequest, systemEqActions)
        } else {
            // Secondary tool: kept one tap away so the curve and faders stay the focus.
            ExpandableRow("Other apps") { SystemEqCard(systemEq, globalMix, panelRequest, systemEqActions) }
        }
    }

    if (showSave) {
        NameDialog(
            title = "Save profile",
            confirmLabel = "Save",
            initial = if (eq.activeId == EqState.CUSTOM_ID) "" else activeLabel(eq),
            onConfirm = { actions.saveProfile(it); showSave = false },
            onDismiss = { showSave = false },
        )
    }
}

const val MAIN_TITLE = "AfterTune"
const val MAIN_SUBTITLE = "HD 4.50 BTNC Companion"

private fun activeLabel(eq: EqState): String =
    BuiltInPreset.byId(eq.activeId)?.label ?: eq.activeProfile?.name ?: "Custom"

@Composable
private fun headerStatus(h: HeadsetState): Pair<String, Tone> = when {
    !h.ready -> "Checking" to Tone.NEUTRAL
    h.status == ConnectionStatus.CONNECTED -> "Connected" to Tone.POSITIVE
    h.status == ConnectionStatus.PERMISSION_REQUIRED -> "Allow access" to Tone.WARNING
    h.status == ConnectionStatus.BLUETOOTH_OFF -> "Bluetooth off" to Tone.WARNING
    else -> "Not connected" to Tone.NEUTRAL
}
