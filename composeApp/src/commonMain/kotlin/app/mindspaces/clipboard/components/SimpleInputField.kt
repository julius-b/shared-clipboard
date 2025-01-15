package app.mindspaces.clipboard.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextAlign
import com.slack.circuit.retained.rememberRetained

@Composable
fun SimpleInputField(
    label: String,
    value: String,
    valid: Boolean,
    onValueChange: (String) -> Unit,
    errorText: String = "required"
) {
    var wasFocused by rememberRetained { mutableStateOf(false) }
    var touched by rememberRetained { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = {
            onValueChange(it)
            // NOTE: consider only setting error after leaving focus
            touched = true
        },
        modifier = Modifier.fillMaxWidth().onFocusChanged { focusState ->
            if (focusState.isFocused) wasFocused = true
            if (!focusState.isFocused && wasFocused) touched = true
        },
        supportingText = {
            if (touched && !valid) {
                Text(
                    errorText, modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                )
            }
        },
        label = { Text(label) },
        isError = touched && !valid,
        singleLine = true
    )
}
