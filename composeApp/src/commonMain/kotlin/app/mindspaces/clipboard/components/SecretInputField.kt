package app.mindspaces.clipboard.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import com.slack.circuit.retained.rememberRetained

@Composable
fun SecretInputField(
    label: String,
    value: String,
    valid: Boolean,
    onValueChanged: (String) -> Unit,
    errorText: String
) {
    var wasFocused by rememberRetained { mutableStateOf(false) }
    var touched by rememberRetained { mutableStateOf(false) }
    var visible by rememberRetained { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = {
            onValueChanged(it)
            //touched = true
        },
        modifier = Modifier.fillMaxWidth().onFocusChanged { focusState ->
            if (focusState.isFocused) wasFocused = true
            if (!focusState.isFocused && wasFocused) touched = true
        },
        label = { Text(label) },
        supportingText = {
            Text(
                errorText, modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
            )
        },
        isError = touched && !valid,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            // TODO icon visibility/Off
            val image = if (visible) Icons.Outlined.Lock else Icons.Outlined.Info

            val description = if (visible) "Hide password" else "Show password"

            IconButton(onClick = { visible = !visible }) {
                Icon(imageVector = image, description)
            }
        },
        singleLine = true
    )
}
