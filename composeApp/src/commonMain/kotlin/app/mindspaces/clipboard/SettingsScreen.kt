package app.mindspaces.clipboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
 import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.mindspaces.clipboard.SettingsScreen.Event.Back
import app.mindspaces.clipboard.SettingsScreen.Event.ConfirmLogout
import app.mindspaces.clipboard.SettingsScreen.Event.ToggleLogout
import app.mindspaces.clipboard.components.SimpleScaffold
import app.mindspaces.clipboard.db.Account
import app.mindspaces.clipboard.db.AccountProperty
import app.mindspaces.clipboard.parcel.CommonParcelize
import app.mindspaces.clipboard.repo.AuthRepository
import co.touchlab.kermit.Logger
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.collectAsRetainedState
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.internal.rememberStableCoroutineScope
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope

@CommonParcelize
data object SettingsScreen : Screen {
    data class State(
        val self: Account?,
        val properties: List<AccountProperty>,
        // TODO enum {Logout, other modals} -> effectively only 1 (overlay: Overlay.Logout, .bla)
        val isLogout: Boolean,
        val eventSink: (Event) -> Unit
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data object ConfirmLogout : Event
        data object ToggleLogout : Event
        data object Back : Event
    }
}

@CircuitInject(SettingsScreen::class, AppScope::class)
@Inject
class SettingsPresenter(
    @Assisted private val navigator: Navigator,
    private val authRepository: AuthRepository
) : Presenter<SettingsScreen.State> {
    private val log = Logger.withTag("SettingsScreen")

    @Composable
    override fun present(): SettingsScreen.State {
        val scope = rememberStableCoroutineScope()

        val self by authRepository.self().collectAsRetainedState(null)
        val properties by authRepository.properties().collectAsRetainedState(emptyList())

        var isLogout by rememberRetained { mutableStateOf(false) }

        return SettingsScreen.State(self, properties, isLogout) { event ->
            when (event) {
                is ConfirmLogout -> {
                    scope.launch {
                        // TODO notify server
                        authRepository.logout()
                        isLogout = false
                    }
                }

                is ToggleLogout -> isLogout = !isLogout
                is Back -> navigator.pop()
            }
        }
    }
}

@CircuitInject(SettingsScreen::class, AppScope::class)
@Composable
fun SettingsView(state: SettingsScreen.State, modifier: Modifier = Modifier) {
    SimpleScaffold(modifier, "Settings", horizontal = 16.dp, onBack = { state.eventSink(Back) }) {
        if (state.isLogout) LogoutOverlay(
            onConfirm = { state.eventSink(ConfirmLogout) },
            onDismiss = { state.eventSink(ToggleLogout) }
        )

        AccountBox(state.self, state.properties) {
            state.eventSink(ToggleLogout)
        }
    }
}

@Composable
fun AccountBox(account: Account?, properties: List<AccountProperty>, onLogout: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            if (account == null) {
                Text("Not authenticated")
                return@Card
            }
            Text(
                "Account: ${account.name}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            for (property in properties) {
                Spacer(Modifier.height(4.dp))
                // TODO request validation button for invalid properties
                // TODO delete button (if alt available)
                // TODO add button, info (add alternative first before deleting, on delete)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("${property.type}")
                            if (property.primary == true) append(" (primary)")
                            append(": ")
                        }
                        append(property.content)
                    },
                    style = MaterialTheme.typography.titleSmall
                )
            }
            TextButton(
                modifier = Modifier.align(Alignment.End),
                onClick = {
                    onLogout()
                }
            ) {
                Text("Logout")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogoutOverlay(modifier: Modifier = Modifier, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Logout",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text("Are you sure you want to logout? You will lose access to all shared files.")

                Row(Modifier.align(Alignment.End)) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(16.dp))
                    TextButton(
                        onClick = onConfirm,
                    ) {
                        Text("Logout")
                    }
                }
            }
        }
    }
}
