package dev.aftertune.ui.sound

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.aftertune.systemeq.AttachedSession
import dev.aftertune.systemeq.GlobalMixState
import dev.aftertune.systemeq.SystemEqState
import dev.aftertune.ui.components.Badge
import dev.aftertune.ui.components.ButtonStyle
import dev.aftertune.ui.components.GlyphKind
import dev.aftertune.ui.components.InfoRow
import dev.aftertune.ui.components.Notice
import dev.aftertune.ui.components.SectionTitle
import dev.aftertune.ui.components.ExpandableRow
import dev.aftertune.ui.components.StudioButton
import dev.aftertune.ui.components.StudioCard
import dev.aftertune.ui.components.StudioDivider
import dev.aftertune.ui.components.ToggleRow
import dev.aftertune.ui.components.Tone
import dev.aftertune.ui.theme.Space
import dev.aftertune.ui.theme.Studio
import dev.aftertune.ui.theme.StudioType

data class SystemEqActions(
    val setEnabled: (Boolean) -> Unit,
    val setGlobalMix: (Boolean) -> Unit,
    val dismissPanelRequest: () -> Unit,
)

/**
 * Scope of the EQ, shown truthfully: always this app's player; optionally other apps that announce
 * their audio session. Status lines come only from what the backend actually observed.
 */
@Composable
fun SystemEqCard(state: SystemEqState, globalMix: Boolean, panelRequest: String?, actions: SystemEqActions) {
    val c = Studio.colors
    Column(Modifier.animateContentSize()) {
        SectionTitle("Other apps")

        if (panelRequest != null) {
            Spacer(Modifier.height(Space.s))
            Notice(
                if (state.requested) "Opened as the equalizer for $panelRequest."
                else "Opened as the equalizer for $panelRequest. Turn on “Apply to other apps”.",
                tone = Tone.NEUTRAL,
            )
            StudioButton("Dismiss", actions.dismissPanelRequest, style = ButtonStyle.GHOST)
        }

        ToggleRow(
            "Apply to other apps",
            "Players that share audio sessions.",
            state.requested,
            actions.setEnabled,
        )
        if (state.requested) {
            Text(
                state.summary,
                Modifier.padding(bottom = Space.s),
                style = StudioType.bodySmall,
                color = when {
                    state.workingCount > 0 -> c.accent
                    else -> c.textMuted
                },
            )
        }

        AnimatedVisibility(state.requested) {
            Column {
                state.sessions.sortedBy { it.packageName }.forEach { SessionRow(it) }
                ExpandableRow("Advanced") {
                    ToggleRow(
                        "Global output mix",
                        "Legacy. Most phones ignore it.",
                        globalMix,
                        actions.setGlobalMix,
                    )
                    when (state.global) {
                        GlobalMixState.OFF -> Unit
                        GlobalMixState.CREATED_UNVERIFIED -> Notice(state.globalMessage ?: "", tone = Tone.WARNING, glyph = GlyphKind.WARNING)
                        GlobalMixState.REJECTED -> Notice(state.globalMessage ?: "Rejected by this phone.", tone = Tone.NEUTRAL)
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(s: AttachedSession) {
    val c = Studio.colors
    val value = when {
        s.error != null -> "Failed: ${s.error}"
        s.mechanism == null -> "Not attached"
        !s.hasControl -> "Another effect has control"
        else -> "Attached · ${s.mechanism.label}"
    }
    InfoRow(
        label = "${s.packageName} · #${s.sessionId}",
        value = value,
        valueColor = if (s.working) c.accent else c.textMuted,
    )
}

