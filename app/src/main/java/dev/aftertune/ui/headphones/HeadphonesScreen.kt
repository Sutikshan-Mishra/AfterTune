package dev.aftertune.ui.headphones

import dev.aftertune.ui.components.LocalContentBottomInset
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.aftertune.bt.AudioDevice
import dev.aftertune.bt.ConnectionStatus
import dev.aftertune.bt.HeadsetState
import dev.aftertune.ui.components.Badge
import dev.aftertune.ui.components.ButtonStyle
import dev.aftertune.ui.components.Glyph
import dev.aftertune.ui.components.GlyphKind
import dev.aftertune.ui.components.InfoRow
import dev.aftertune.ui.components.Notice
import dev.aftertune.ui.components.Overline
import dev.aftertune.ui.components.ScreenHeader
import dev.aftertune.ui.components.SectionTitle
import dev.aftertune.ui.components.StudioButton
import dev.aftertune.ui.components.StudioCard
import dev.aftertune.ui.components.StudioDivider
import dev.aftertune.ui.components.Tone
import dev.aftertune.ui.components.toneColor
import dev.aftertune.ui.theme.Shapes
import dev.aftertune.ui.theme.Space
import dev.aftertune.ui.theme.Studio
import dev.aftertune.ui.theme.StudioType

data class HeadphonesActions(
    val requestPermission: () -> Unit,
    val openAppSettings: () -> Unit,
    val openBluetoothSettings: () -> Unit,
    val refresh: () -> Unit,
)

/** Connection status for Bluetooth headphones. Lives at the top of the Settings page. */
@Composable
fun HeadphonesSection(
    state: HeadsetState,
    permissionBlocked: Boolean,
    actions: HeadphonesActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        ConnectionCard(state, permissionBlocked, actions)
    }
}

/** Pairing and sound tips. Lives near the bottom of the Settings page. */
@Composable
fun TroubleshootingSection(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) { TroubleshootingCard() }
}

@Composable
private fun ConnectionCard(state: HeadsetState, permissionBlocked: Boolean, actions: HeadphonesActions) {
    val c = Studio.colors
    val status = state.status
    val tone = when (status) {
        ConnectionStatus.CONNECTED -> Tone.POSITIVE
        ConnectionStatus.PERMISSION_REQUIRED, ConnectionStatus.BLUETOOTH_OFF -> Tone.WARNING
        ConnectionStatus.UNSUPPORTED -> Tone.ERROR
        else -> Tone.NEUTRAL
    }
    val target = state.target

    Column(Modifier.animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(Shapes.control).background(c.surfaceHigh),
                contentAlignment = Alignment.Center,
            ) { Glyph(GlyphKind.HEADPHONES, toneColor(tone), size = 22.dp) }
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Text(
                    if (!state.ready) "Checking…" else status.label,
                    style = StudioType.title,
                    color = if (tone == Tone.NEUTRAL) c.text else toneColor(tone),
                )
                Text(
                    target?.name ?: "Bluetooth headphones",
                    style = StudioType.bodySmall,
                    color = c.textMuted,
                )
            }
        }

        Spacer(Modifier.height(Space.m))
        when (status) {
            ConnectionStatus.PERMISSION_REQUIRED -> {
                Text(
                    "Allow Nearby devices to show status.",
                    style = StudioType.bodySmall,
                    color = c.textMuted,
                )
                Spacer(Modifier.height(Space.m))
                if (permissionBlocked) {
                    StudioButton("Open app settings", actions.openAppSettings, style = ButtonStyle.FILLED, modifier = Modifier.fillMaxWidth())
                } else {
                    StudioButton("Allow access", actions.requestPermission, style = ButtonStyle.FILLED, modifier = Modifier.fillMaxWidth())
                }
            }
            ConnectionStatus.UNSUPPORTED -> Text("No Bluetooth on this device.", style = StudioType.bodySmall, color = c.textMuted)
            else -> {
                StudioDivider()
                if (target != null) {
                    DeviceDetails(target)
                } else {
                    InfoRow("Paired", if (state.targetPaired) "Yes" else "No")
                    state.others.forEach { other ->
                        InfoRow("Also connected", other.name)
                    }
                }
                Spacer(Modifier.height(Space.m))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    StudioButton("Bluetooth settings", actions.openBluetoothSettings, glyph = GlyphKind.BLUETOOTH, modifier = Modifier.weight(1f))
                    StudioButton("Refresh", actions.refresh, glyph = GlyphKind.RESET, style = ButtonStyle.GHOST)
                }
            }
        }
    }
}

@Composable
private fun DeviceDetails(d: AudioDevice) {
    val c = Studio.colors
    InfoRow("Media", if (d.mediaAudio) "On" else "Off", valueColor = if (d.mediaAudio) c.accent else c.textMuted)
    InfoRow("Calls", if (d.calls) "On" else "Off", valueColor = if (d.calls) c.text else c.textMuted)
    InfoRow(
        "Battery",
        d.batteryPercent?.let { "$it %" } ?: "Not reported",
        valueColor = if (d.batteryPercent != null) c.text else c.textMuted,
        mono = d.batteryPercent != null,
    )
}

private data class Tip(val title: String, val steps: List<String>)

private val TIPS = listOf(
    Tip(
        "Pair the headphones",
        listOf(
            "Put them in pairing mode (see their manual).",
            "Choose them in the phone's Bluetooth settings.",
        ),
    ),
    Tip(
        "Clear old pairings",
        listOf(
            "Remove the headphones from the phone's Bluetooth list.",
            "Put them in pairing mode and pair again.",
        ),
    ),
    Tip(
        "Disconnects or drop-outs",
        listOf(
            "Turn off Bluetooth on other nearby devices that may grab the connection.",
            "Keep the phone within a few metres.",
        ),
    ),
    Tip(
        "Other apps don't sound different",
        listOf(
            "On the Sound tab, switch on “Apply to other apps”, then restart playback in that app.",
            "Some apps can't be reached this way. That's an Android limit.",
        ),
    ),
)

@Composable
private fun TroubleshootingCard() {
    val c = Studio.colors
    var open by rememberSaveable { mutableStateOf(-1) }
    Column {
        SectionTitle("Troubleshooting", Modifier.padding(bottom = Space.xs))
        TIPS.forEachIndexed { i, tip ->
            if (i > 0) StudioDivider()
            val expanded = open == i
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { open = if (expanded) -1 else i }
                    .padding(vertical = Space.m)
                    .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tip.title, Modifier.weight(1f), style = StudioType.label, color = c.text)
                val rot by animateFloatAsState(if (expanded) 180f else 0f, label = "tipChev")
                Glyph(GlyphKind.CHEVRON_DOWN, c.textMuted, Modifier.rotate(rot), size = 18.dp)
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(bottom = Space.m), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    tip.steps.forEachIndexed { n, s ->
                        Row {
                            Text("${n + 1}", Modifier.width(18.dp), style = StudioType.monoSmall, color = c.accent)
                            Text(s, style = StudioType.bodySmall, color = c.textMuted)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.xs))
    }
}
