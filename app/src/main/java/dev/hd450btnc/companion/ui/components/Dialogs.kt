package dev.hd450btnc.companion.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.hd450btnc.companion.eq.EqController
import dev.hd450btnc.companion.eq.EqState
import dev.hd450btnc.companion.ui.theme.Shapes
import dev.hd450btnc.companion.ui.theme.Studio
import dev.hd450btnc.companion.ui.theme.StudioType

@Composable
fun NameDialog(
    title: String,
    confirmLabel: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Studio.colors
    var text by rememberSaveable { mutableStateOf(initial) }
    val clean = EqController.cleanName(text)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = Shapes.card,
        containerColor = c.surfaceRaised,
        title = { Text(title, style = StudioType.title, color = c.text) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= EqState.MAX_NAME_LENGTH) text = it },
                singleLine = true,
                placeholder = { Text("e.g. Commute", color = c.textFaint) },
                supportingText = { Text("${text.length}/${EqState.MAX_NAME_LENGTH}", style = StudioType.monoSmall, color = c.textFaint) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (clean.isNotEmpty()) onConfirm(clean) }),
                shape = Shapes.control,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.accent,
                    unfocusedBorderColor = c.borderStrong,
                    cursorColor = c.accent,
                    focusedTextColor = c.text,
                    unfocusedTextColor = c.text,
                ),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(clean) }, enabled = clean.isNotEmpty()) {
                Text(confirmLabel, style = StudioType.label, color = if (clean.isNotEmpty()) c.accent else c.textFaint)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = StudioType.label, color = c.textMuted) }
        },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    val c = Studio.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = Shapes.card,
        containerColor = c.surfaceRaised,
        title = { Text(title, style = StudioType.title, color = c.text) },
        text = { Text(message, style = StudioType.body, color = c.textMuted) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, style = StudioType.label, color = if (destructive) c.error else c.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = StudioType.label, color = c.textMuted) }
        },
    )
}
