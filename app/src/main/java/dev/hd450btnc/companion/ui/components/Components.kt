package dev.hd450btnc.companion.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hd450btnc.companion.ui.theme.Shapes
import dev.hd450btnc.companion.ui.theme.Space
import dev.hd450btnc.companion.ui.theme.Studio
import dev.hd450btnc.companion.ui.theme.StudioType

enum class Tone { POSITIVE, NEUTRAL, WARNING, ERROR }

@Composable
fun toneColor(tone: Tone): Color = when (tone) {
    Tone.POSITIVE -> Studio.colors.accent
    Tone.NEUTRAL -> Studio.colors.textMuted
    Tone.WARNING -> Studio.colors.warning
    Tone.ERROR -> Studio.colors.error
}

@Composable
fun StudioCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(Space.l),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(Shapes.card)
            .background(Studio.colors.surfaceRaised)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = Studio.colors.textMuted) {
    // Quiet sentence-case caption (no all-caps).
    Text(text, modifier.semantics { heading() }, style = StudioType.overline, color = color)
}

/** Section heading in the style of a music app: plain, bold, sentence case, no box around what follows. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.semantics { heading() }, style = StudioType.title, color = Studio.colors.text)
}

@Composable
fun ScreenHeader(overline: String, title: String, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = Space.l, bottom = Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, Modifier.semantics { heading() }, style = StudioType.display, color = Studio.colors.text)
        }
        trailing?.invoke(this)
    }
}

@Composable
fun StatusPill(label: String, tone: Tone, modifier: Modifier = Modifier, pulsing: Boolean = false, onClick: (() -> Unit)? = null) {
    val c = toneColor(tone)
    val dotAlpha = if (pulsing) {
        val t = rememberInfiniteTransition(label = "pulse")
        t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dot").value
    } else 1f
    Row(
        modifier
            .clip(Shapes.control)
            .background(Studio.colors.surfaceRaised)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = Space.m, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).alpha(dotAlpha).clip(CircleShape).background(c))
        Spacer(Modifier.width(Space.s))
        Text(label, style = StudioType.label, color = if (tone == Tone.NEUTRAL) Studio.colors.textMuted else Studio.colors.text, maxLines = 1)
    }
}

@Composable
fun Badge(text: String, tone: Tone) {
    // A small status dot and plain sentence-case text, no pill. Only ERROR keeps its own colour.
    val colors = Studio.colors
    val fg = if (tone == Tone.ERROR) colors.error else colors.badgeText
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(fg))
        Spacer(Modifier.width(6.dp))
        Text(text, style = StudioType.bodySmall, color = fg, maxLines = 1)
    }
}

@Composable
fun PresetChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Studio.colors
    val bg by animateColorAsState(if (selected) c.accentSoft else c.surfaceRaised, label = "chipBg")
    // Unselected chips rely on surface brightness alone; only the active one gets an accent outline.
    val border by animateColorAsState(if (selected) c.accent else Color.Transparent, label = "chipBorder")
    val fg by animateColorAsState(if (selected) c.accent else c.textMuted, label = "chipFg")
    Box(
        modifier
            .heightIn(min = 40.dp)
            .minimumInteractiveComponentSize()
            .clip(Shapes.control)
            .background(bg)
            .border(1.dp, border, Shapes.control)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = Space.l),
        contentAlignment = Alignment.Center,
    ) {
        // A check mark marks the active preset, so selection never relies on colour alone.
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Glyph(GlyphKind.CHECK, fg, size = 14.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text(label, style = StudioType.label, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

enum class ButtonStyle { FILLED, OUTLINE, GHOST }

@Composable
fun StudioButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.OUTLINE,
    glyph: GlyphKind? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val c = Studio.colors
    val fg = when {
        style == ButtonStyle.FILLED -> c.onAccent
        destructive -> c.error
        style == ButtonStyle.GHOST -> c.textMuted
        else -> c.text
    }
    val bg = if (style == ButtonStyle.FILLED) c.accent else Color.Transparent
    val border = when (style) {
        ButtonStyle.FILLED, ButtonStyle.GHOST -> Color.Transparent
        ButtonStyle.OUTLINE -> if (destructive) c.error.copy(alpha = 0.5f) else c.borderStrong
    }
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 40.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(Shapes.control)
            .background(bg)
            .border(1.dp, border, Shapes.control)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (style == ButtonStyle.GHOST) Space.s else Space.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (glyph != null) {
            Glyph(glyph, fg, size = 18.dp)
            Spacer(Modifier.width(Space.s))
        }
        Text(text, style = StudioType.label, color = fg, maxLines = 1)
    }
}

/** Square icon-only button with a 48dp touch target and an explicit accessibility label. */
@Composable
fun IconAction(
    glyph: GlyphKind,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Studio.colors.text,
    enabled: Boolean = true,
    filled: Boolean = false,
) {
    val c = Studio.colors
    Box(
        modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(if (filled) CircleShape else Shapes.control)
            .background(if (filled) c.accent else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Glyph(glyph, if (filled) c.onAccent else tint, size = if (filled) 22.dp else 20.dp)
    }
}

@Composable
fun ToggleRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = Space.l)) {
            Text(title, style = StudioType.label, color = Studio.colors.text)
            if (description != null) {
                Spacer(Modifier.size(2.dp))
                Text(description, style = StudioType.bodySmall, color = Studio.colors.textMuted)
            }
        }
        StudioSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun StudioSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean = true) {
    val c = Studio.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.onAccent,
            checkedTrackColor = c.accent,
            checkedBorderColor = c.accent,
            uncheckedThumbColor = c.textMuted,
            uncheckedTrackColor = c.surfaceHigh,
            uncheckedBorderColor = c.borderStrong,
        ),
    )
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color = Studio.colors.text, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = StudioType.bodySmall, color = Studio.colors.textMuted)
        Text(value, style = if (mono) StudioType.monoValue else StudioType.label, color = valueColor)
    }
}

@Composable
fun StudioDivider() = HorizontalDivider(thickness = 1.dp, color = Studio.colors.border)

@Composable
fun <T> SegmentedChoice(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val c = Studio.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Shapes.control)
            .background(c.surfaceRaised)
            .border(1.dp, c.border, Shapes.control)
            .padding(3.dp),
    ) {
        options.forEach { option ->
            val isSel = option == selected
            val bg by animateColorAsState(if (isSel) c.surfaceHigh else Color.Transparent, label = "segBg")
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp)
                    .clip(Shapes.small)
                    .background(bg)
                    .selectable(selected = isSel, role = Role.RadioButton) { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label(option), style = StudioType.label, color = if (isSel) c.text else c.textMuted)
            }
        }
    }
}

/** Quiet inline note for scope statements, limitations and warnings: a small glyph and muted text, no box. */
@Composable
fun Notice(text: String, tone: Tone = Tone.NEUTRAL, glyph: GlyphKind = GlyphKind.INFO, modifier: Modifier = Modifier) {
    val c = Studio.colors
    val tint = if (tone == Tone.NEUTRAL) c.textFaint else toneColor(tone)
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = Space.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Glyph(glyph, tint, size = 16.dp, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(Space.s))
        Text(text, style = StudioType.bodySmall, color = c.textMuted)
    }
}
