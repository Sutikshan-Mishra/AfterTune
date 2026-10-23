package dev.aftertune.ui.ambient

import dev.aftertune.ui.components.LocalContentBottomInset
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import dev.aftertune.ui.components.ExpandableRow
import dev.aftertune.ui.components.toneColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.aftertune.ambient.AmbientGain
import dev.aftertune.ambient.AmbientInput
import dev.aftertune.ambient.AmbientPhase
import dev.aftertune.ambient.AmbientProcessor
import dev.aftertune.ambient.AmbientStatus
import dev.aftertune.ambient.Availability
import dev.aftertune.ambient.RoutePlan
import dev.aftertune.ui.components.ButtonStyle
import dev.aftertune.ui.components.Glyph
import dev.aftertune.ui.components.GlyphKind
import dev.aftertune.ui.components.InfoRow
import dev.aftertune.ui.components.Notice
import dev.aftertune.ui.components.Overline
import dev.aftertune.ui.components.ScreenHeader
import dev.aftertune.ui.components.SectionTitle
import dev.aftertune.ui.components.SegmentedChoice
import dev.aftertune.ui.components.StatusPill
import dev.aftertune.ui.components.StudioButton
import dev.aftertune.ui.components.StudioCard
import dev.aftertune.ui.components.StudioDivider
import dev.aftertune.ui.components.StudioSlider
import dev.aftertune.ui.components.Tone
import dev.aftertune.ui.theme.Shapes
import dev.aftertune.ui.theme.Space
import dev.aftertune.ui.theme.Studio
import dev.aftertune.ui.theme.StudioType

enum class MicPermission(val label: String) { GRANTED("Allowed"), NOT_ASKED("Not yet"), DENIED("Denied"), BLOCKED("Blocked") }

data class AmbientActions(
    val start: () -> Unit,
    val stop: () -> Unit,
    val selectInput: (AmbientInput) -> Unit,
    val setGain: (Float) -> Unit,
    val resetGain: () -> Unit,
    val setMuted: (Boolean) -> Unit,
    val openAppSettings: () -> Unit,
)

@Composable
fun AmbientScreen(
    status: AmbientStatus,
    plan: RoutePlan,
    selectedInput: AmbientInput,
    permission: MicPermission,
    actions: AmbientActions,
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    val c = Studio.colors
    val active = status.isActive
    val shownInput = if (active) status.input else selectedInput
    val availability = if (shownInput == AmbientInput.PHONE_MIC) plan.phoneMic else plan.headsetMic

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.screen)
            .padding(bottom = Space.xl + LocalContentBottomInset.current),
    ) {
        ScreenHeader(overline = "Ambient", title = "Ambient")
        Notice("Not hearing protection.", tone = Tone.WARNING, glyph = GlyphKind.WARNING)

        val canStart = availability is Availability.Available && permission != MicPermission.BLOCKED
        val (stateLabel, stateTone) = when (status.phase) {
            AmbientPhase.IDLE -> "Off" to Tone.NEUTRAL
            AmbientPhase.STARTING -> "Starting" to Tone.NEUTRAL
            AmbientPhase.RUNNING -> (if (status.muted) "Muted" else "Live") to Tone.POSITIVE
            AmbientPhase.ERROR -> "Failed" to Tone.ERROR
        }

        Column(Modifier.fillMaxWidth().padding(top = Space.m), horizontalAlignment = Alignment.CenterHorizontally) {
            val heroEnabled = active || canStart
            // The ring follows the live mic level (already measured by the engine), so the button shows what it hears.
            val level by animateFloatAsState(
                if (status.phase == AmbientPhase.RUNNING && !status.muted) ((status.inputPeakDbfs + 60f) / 60f).coerceIn(0f, 1f) else 0f,
                animationSpec = tween(durationMillis = 110),
                label = "heroLevel",
            )
            val ringColor = accent ?: c.accent
            Box(
                Modifier
                    .size(248.dp)
                    .drawBehind {
                        val base = 84.dp.toPx()
                        val reach = 38.dp.toPx()
                        if (active) {
                            drawCircle(ringColor.copy(alpha = 0.10f + 0.14f * level), radius = base + reach * level, style = Stroke(width = 2.dp.toPx()))
                            drawCircle(ringColor.copy(alpha = 0.05f + 0.08f * level), radius = base + reach * 1.7f * level, style = Stroke(width = 1.dp.toPx()))
                        } else {
                            drawCircle(ringColor.copy(alpha = 0.10f), radius = base + 14.dp.toPx(), style = Stroke(width = 1.dp.toPx()))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(168.dp)
                        .clip(CircleShape)
                        .background(if (active) c.surfaceHigh else c.accent)
                        .border(if (active) 2.dp else 0.dp, if (active) ringColor else Color.Transparent, CircleShape)
                        .clickable(enabled = heroEnabled, role = Role.Button) { if (active) actions.stop() else actions.start() }
                        .alpha(if (heroEnabled) 1f else 0.38f)
                        .semantics { contentDescription = if (active) "Stop ambient listening" else "Start ambient listening" },
                    contentAlignment = Alignment.Center,
                ) {
                    Glyph(if (active) GlyphKind.CLOSE else GlyphKind.MIC, if (active) ringColor else c.onAccent, size = 56.dp)
                }
            }
            Text(stateLabel, style = StudioType.label, color = toneColor(stateTone))
            status.message?.let {
                Spacer(Modifier.height(Space.xs))
                Text(it, style = StudioType.bodySmall, color = if (status.phase == AmbientPhase.ERROR) c.error else c.textMuted)
            }
            if (permission == MicPermission.BLOCKED) {
                StudioButton("Allow mic in Settings", actions.openAppSettings, style = ButtonStyle.GHOST)
            }
        }

        Spacer(Modifier.height(Space.xl))
        SegmentedChoice(
            options = AmbientInput.entries.toList(),
            selected = shownInput,
            label = { it.label },
            onSelect = { if (!active) actions.selectInput(it) },
        )
        if (availability is Availability.Unavailable && !active) {
            Spacer(Modifier.height(Space.s))
            Notice(availability.reason, tone = Tone.WARNING, glyph = GlyphKind.INFO)
        }

        Spacer(Modifier.height(Space.xl))
        GainCard(status, actions)

        Spacer(Modifier.height(Space.l))
        ExpandableRow("Details") {
            Column {
                if (permission != MicPermission.GRANTED) InfoRow("Microphone", permission.label, valueColor = c.textMuted)
                if (active) InfoRow("Input", status.inputDevice ?: "—")
                InfoRow("Output", status.outputDevice ?: plan.mediaOutput?.name ?: "None")
                if (status.phase == AmbientPhase.RUNNING) {
                    InfoRow("Phone delay", status.inPhoneLatencyMs?.let { "≈ $it ms" } ?: "measuring…", mono = true)
                }
                InfoRow("Peak limit", "−2 dBFS", mono = true)
                Spacer(Modifier.height(Space.s))
                Text(
                    "Live only, never recorded. Measure total delay: clap near the phone while another device records; the gap between the two clicks is the delay.",
                    style = StudioType.bodySmall,
                    color = c.textFaint,
                )
            }
        }
    }
}

@Composable
private fun GainCard(status: AmbientStatus, actions: AmbientActions) {
    val c = Studio.colors
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            SectionTitle("Gain", Modifier.weight(1f))
            Text(
                AmbientGain.label(status.gainDb, status.muted),
                style = StudioType.monoLarge,
                color = if (status.muted) c.textMuted else c.text,
            )
        }
        Spacer(Modifier.height(Space.m))
        StudioSlider(
            value = AmbientGain.dbToSlider(status.gainDb),
            onValueChange = { actions.setGain(AmbientGain.sliderToDb(it)) },
            contentDescription = "Ambient gain",
            stateText = AmbientGain.label(status.gainDb, status.muted),
        )
        Spacer(Modifier.height(Space.m))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            StudioButton(
                if (status.muted) "Unmute" else "Mute",
                { actions.setMuted(!status.muted) },
                glyph = GlyphKind.MUTE,
                modifier = Modifier.weight(1f),
            )
            StudioButton(
                "Reset",
                actions.resetGain,
                style = ButtonStyle.GHOST,
                glyph = GlyphKind.RESET,
                enabled = status.gainDb != AmbientGain.DEFAULT_DB,
            )
        }
        if (status.phase == AmbientPhase.RUNNING) {
            Spacer(Modifier.height(Space.l))
            LevelMeter("Mic level", status.inputPeakDbfs)
            Spacer(Modifier.height(Space.s))
            LevelMeter("To headphones", status.outputPeakDbfs)
        }
    }
}

/** Peak level from the running engine, -60..0 dBFS. */
@Composable
private fun LevelMeter(label: String, dbfs: Float) {
    val c = Studio.colors
    val fraction by animateFloatAsState(((dbfs + 60f) / 60f).coerceIn(0f, 1f), label = "meter")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(104.dp), style = StudioType.bodySmall, color = c.textMuted)
        Box(Modifier.weight(1f).height(6.dp).clip(Shapes.small).background(c.surfaceHigh)) {
            Box(Modifier.fillMaxWidth(fraction).height(6.dp).background(if (dbfs > -3f) c.error else c.accent))
        }
        Spacer(Modifier.width(Space.s))
        Text(
            if (dbfs <= AmbientProcessor.SILENCE_DB) "—" else "%.0f dB".format(java.util.Locale.ROOT, dbfs),
            Modifier.width(52.dp),
            style = StudioType.monoSmall,
            color = c.textFaint,
        )
    }
}
