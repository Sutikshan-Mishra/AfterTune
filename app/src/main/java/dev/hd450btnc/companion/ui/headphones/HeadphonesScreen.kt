package dev.hd450btnc.companion.ui.headphones

import dev.hd450btnc.companion.ui.components.LocalContentBottomInset
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
import dev.hd450btnc.companion.bt.AudioDevice
import dev.hd450btnc.companion.bt.ConnectionStatus
import dev.hd450btnc.companion.bt.HeadsetState
import dev.hd450btnc.companion.ui.components.Badge
import dev.hd450btnc.companion.ui.components.ButtonStyle
import dev.hd450btnc.companion.ui.components.Glyph
import dev.hd450btnc.companion.ui.components.GlyphKind
import dev.hd450btnc.companion.ui.components.InfoRow
import dev.hd450btnc.companion.ui.components.Notice
import dev.hd450btnc.companion.ui.components.Overline
import dev.hd450btnc.companion.ui.components.ScreenHeader
import dev.hd450btnc.companion.ui.components.SectionTitle
import dev.hd450btnc.companion.ui.components.StudioButton
import dev.hd450btnc.companion.ui.components.StudioCard
import dev.hd450btnc.companion.ui.components.StudioDivider
import dev.hd450btnc.companion.ui.components.Tone
import dev.hd450btnc.companion.ui.components.toneColor
import dev.hd450btnc.companion.ui.theme.Shapes
import dev.hd450btnc.companion.ui.theme.Space
import dev.hd450btnc.companion.ui.theme.Studio
import dev.hd450btnc.companion.ui.theme.StudioType

data class HeadphonesActions(
    val requestPermission: () -> Unit,
    val openAppSettings: () -> Unit,
    val openBluetoothSettings: () -> Unit,
    val refresh: () -> Unit,
)

@Composable
fun HeadphonesScreen(
    state: HeadsetState,
    permissionBlocked: Boolean,
    actions: HeadphonesActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.screen)
            .padding(bottom = Space.xl + LocalContentBottomInset.current),
    ) {
        ScreenHeader(overline = "Headphones", title = "Headphones")
        ConnectionCard(state, permissionBlocked, actions)
        Spacer(Modifier.height(Space.xl))
        NoiseGardCard()
        Spacer(Modifier.height(Space.xl))
        TroubleshootingCard()
    }
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
                    target?.name ?: "Sennheiser HD 4.50 BTNC",
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

@Composable
private fun NoiseGardCard() {
    val c = Studio.colors
    var whyOpen by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.animateContentSize()) {
        SectionTitle("NoiseGard")
        Spacer(Modifier.height(Space.xs))
        Text("Switch it on the headphones", style = StudioType.bodySmall, color = c.textMuted)
        Spacer(Modifier.height(Space.l))

        HoldShortcut()

        Spacer(Modifier.height(Space.m))
        Text(
            "Hold Volume + and Volume − together. The LED flashes violet to confirm.",
            style = StudioType.bodySmall,
            color = c.textMuted,
        )
        Spacer(Modifier.height(Space.xs))
        StudioDivider()

        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { whyOpen = !whyOpen }
                .padding(vertical = Space.m)
                .semantics { stateDescription = if (whyOpen) "Expanded" else "Collapsed" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Why not in the app?", Modifier.weight(1f), style = StudioType.label, color = c.text)
            val rot by animateFloatAsState(if (whyOpen) 180f else 0f, label = "chev")
            Glyph(GlyphKind.CHEVRON_DOWN, c.textMuted, Modifier.rotate(rot), size = 18.dp)
        }
        AnimatedVisibility(whyOpen) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Bullet("No noise-cancelling command exists over standard Bluetooth.")
                Bullet("Sennheiser hasn't published a protocol for this model.")
            }
        }
    }
}

/** Two keycaps with a looping hold indicator: shows "press both, keep holding" without claiming a duration. */
@Composable
private fun HoldShortcut() {
    val c = Studio.colors
    val t = rememberInfiniteTransition(label = "hold")
    val fill by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 2600
                0f at 0 using LinearEasing
                0f at 400 using LinearEasing
                1f at 2000 using LinearEasing
                1f at 2600
            },
            RepeatMode.Restart,
        ),
        label = "holdFill",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Shapes.control)
            .background(c.surfaceRaised)
            .padding(Space.l)
            .clearAndSetSemantics { contentDescription = "Shortcut: hold Volume plus and Volume minus together" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Keycap("+", fill > 0f)
            Text("+", Modifier.padding(horizontal = Space.m), style = StudioType.label, color = c.textFaint)
            Keycap("−", fill > 0f)
        }
        Spacer(Modifier.height(Space.m))
        Box(Modifier.width(150.dp).height(3.dp).clip(Shapes.small).background(c.surfaceHigh)) {
            Box(Modifier.fillMaxWidth(fill).height(3.dp).background(c.accent))
        }
        Spacer(Modifier.height(Space.s))
        Text("Hold both", style = StudioType.overline, color = c.textMuted)
    }
}

@Composable
private fun Keycap(symbol: String, pressed: Boolean) {
    val c = Studio.colors
    val border = if (pressed) c.accent else c.borderStrong
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width = 64.dp, height = 52.dp)
                .clip(Shapes.control)
                .background(if (pressed) c.accentSoft else c.surfaceHigh)
                .border(1.5.dp, border, Shapes.control),
            contentAlignment = Alignment.Center,
        ) {
            Text(symbol, style = StudioType.monoLarge, color = if (pressed) c.accent else c.text)
        }
        Spacer(Modifier.height(Space.xs))
        Text("Vol", style = StudioType.overline, color = c.textFaint)
    }
}

@Composable
private fun Bullet(text: String) {
    val c = Studio.colors
    Row {
        Box(Modifier.padding(top = 8.dp).size(4.dp).clip(Shapes.small).background(c.textFaint))
        Spacer(Modifier.width(Space.m))
        Text(text, style = StudioType.bodySmall, color = c.textMuted)
    }
}

private data class Tip(val title: String, val steps: List<String>)

private val TIPS = listOf(
    Tip(
        "Pair the headphones",
        listOf(
            "With them off, hold On/Off for about 4 seconds until the LED flashes blue and red.",
            "Choose the HD 4.50 BTNC in the phone's Bluetooth settings.",
        ),
    ),
    Tip(
        "Clear old pairings",
        listOf(
            "In pairing mode, press the multifunction and On/Off buttons together for about 4 seconds.",
            "Remove the headphones from the phone's list and pair again.",
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
