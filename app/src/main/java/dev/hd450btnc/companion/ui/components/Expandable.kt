package dev.hd450btnc.companion.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.hd450btnc.companion.ui.theme.Space
import dev.hd450btnc.companion.ui.theme.Studio
import dev.hd450btnc.companion.ui.theme.StudioType

/** A divider-separated row that expands to reveal secondary detail, so long text stays out of the way. */
@Composable
fun ExpandableRow(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = Studio.colors
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier.animateContentSize()) {
        StudioDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { open = !open }
                .padding(vertical = Space.m)
                .semantics { stateDescription = if (open) "Expanded" else "Collapsed" },
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(title, Modifier.weight(1f), style = StudioType.label, color = c.text)
            val rot by animateFloatAsState(if (open) 180f else 0f, label = "expand")
            Glyph(GlyphKind.CHEVRON_DOWN, c.textMuted, Modifier.rotate(rot), size = 18.dp)
        }
        AnimatedVisibility(open) { Column(Modifier.padding(bottom = Space.m)) { content() } }
    }
}
