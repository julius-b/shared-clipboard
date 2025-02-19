package app.mindspaces.clipboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mindspaces.clipboard.DeviceScreen.Event.Back
import app.mindspaces.clipboard.DeviceScreen.Event.CancelEditing
import app.mindspaces.clipboard.DeviceScreen.Event.Edit
import app.mindspaces.clipboard.DeviceScreen.Event.UpdateName
import app.mindspaces.clipboard.components.SimpleInputField
import app.mindspaces.clipboard.db.AllLinks
import app.mindspaces.clipboard.parcel.CommonParcelize
import app.mindspaces.clipboard.repo.AuthRepository
import app.mindspaces.clipboard.repo.InstallationRepository
import app.mindspaces.clipboard.repo.RepoResult
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import sharedclipboard.composeapp.generated.resources.Anton_Regular
import sharedclipboard.composeapp.generated.resources.Res
import sharedclipboard.composeapp.generated.resources.computer
import sharedclipboard.composeapp.generated.resources.smartphone
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import java.util.UUID

@CommonParcelize
data object DeviceScreen : Screen {
    data class State(
        val devices: List<AllLinks>,
        val states: Map<UUID, DeviceState>,
        val snackbarHostState: SnackbarHostState,
        val eventSink: (Event) -> Unit
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data class UpdateName(val id: UUID, val name: String) : Event
        data class Edit(val id: UUID) : Event
        data class CancelEditing(val id: UUID) : Event
        data object Back : Event
    }
}

enum class DeviceState {
    Editing, Loading
}

@CircuitInject(DeviceScreen::class, AppScope::class)
@Inject
class DevicePresenter(
    @Assisted private val navigator: Navigator,
    private val authRepository: AuthRepository,
    private val installationRepository: InstallationRepository
) : Presenter<DeviceScreen.State> {
    private val log = Logger.withTag("DeviceScreen")

    @Composable
    override fun present(): DeviceScreen.State {
        val scope = rememberStableCoroutineScope()

        val self by authRepository.selfResult()
            .collectAsRetainedState(RepoResult.Empty(loading = true))

        // TODO shouldn't be handled per screen
        if (self is RepoResult.Empty && !self.loading) {
            navigator.pop()
        }

        val devices by installationRepository.allLinks().collectAsRetainedState(listOf())
        val states = rememberRetained { mutableStateMapOf<UUID, DeviceState>() }

        val snackbarHostState = remember { SnackbarHostState() }

        fun showSnackbar(text: String) {
            scope.launch(Dispatchers.Main) {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(text, duration = SnackbarDuration.Short)
            }
        }

        return DeviceScreen.State(
            devices,
            states,
            snackbarHostState
        ) { event ->
            when (event) {
                is UpdateName -> {
                    if (states[event.id] != DeviceState.Editing) {
                        log.e { "update - illegal state: ${states[event.id]}..." }
                        return@State
                    }
                    states[event.id] = DeviceState.Loading
                    showSnackbar("Updating...")
                    scope.launch {
                        val link = installationRepository.updateLinkName(event.id, event.name)
                        log.i { "link: $link" }
                        when (link) {
                            is RepoResult.Data -> states.remove(event.id)
                            is RepoResult.ValidationError -> {
                                showSnackbar("Please check your inputs")
                                states[event.id] = DeviceState.Editing
                            }

                            else -> {
                                showSnackbar("No internet connection, please try again later")
                                states[event.id] = DeviceState.Editing
                            }
                        }
                    }.invokeOnCompletion { e ->
                        if (e != null) states[event.id] = DeviceState.Editing
                    }
                }

                is Edit -> {
                    if (states[event.id] != null) {
                        log.e { "edit - illegal state: ${states[event.id]}..." }
                        return@State
                    }
                    states[event.id] = DeviceState.Editing
                }

                is CancelEditing -> {
                    if (states[event.id] != DeviceState.Editing) {
                        log.e { "cancel-edit - illegal state: ${states[event.id]}..." }
                        return@State
                    }
                    states -= event.id
                }

                is Back -> navigator.pop()
            }
        }
    }
}

// TODO collapsible card, no detail view necessary
@OptIn(ExperimentalMaterial3Api::class)
@CircuitInject(DeviceScreen::class, AppScope::class)
@Composable
fun DeviceView(state: DeviceScreen.State, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxWidth(),
        snackbarHost = { SnackbarHost(hostState = state.snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Devices".uppercase(),
                        fontFamily = FontFamily(Font(Res.font.Anton_Regular))
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { state.eventSink(Back) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.KeyboardArrowLeft,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).padding(horizontal = 12.dp).fillMaxWidth()
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.devices) { device ->
                    DeviceCard(
                        device,
                        state.states[device.link_id],
                        onUpdate = {
                            state.eventSink(UpdateName(device.link_id, it))
                        },
                        onEdit = {
                            state.eventSink(Edit(device.link_id))
                        },
                        onCancel = {
                            state.eventSink(CancelEditing(device.link_id))
                        }
                    )
                }
            }
        }
    }
}

// TODO does state remain when device updates?
@Composable
fun DeviceCard(
    device: AllLinks,
    state: DeviceState?,
    onUpdate: (String) -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            //var editing by rememberRetained { mutableStateOf(false) }
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state != null) {
                    // todo maybe key (device)
                    var deviceName by rememberRetained { mutableStateOf(device.deviceName()) }
                    SimpleInputField(
                        label = "Device Name",
                        value = deviceName,
                        valid = deviceName.isNotBlank(),
                        enabled = state == DeviceState.Editing,
                        onValueChange = {
                            deviceName = it
                        },
                        modifier = Modifier.weight(1f),
                    )
                    // revert to old value
                    IconButton(
                        enabled = state == DeviceState.Editing,
                        onClick = {
                            //deviceName = device.deviceName()
                            onCancel()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel editing",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        enabled = state == DeviceState.Editing,
                        onClick = {
                            onUpdate(deviceName)
                        },
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Update device name",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Text(
                        device.deviceName(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = {
                            onEdit()
                        },
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Update device name",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Row {
                Text(
                    "Platform: ${device.os}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (device.os.isMobile) {
                    Icon(
                        modifier = Modifier.padding(horizontal = 6.dp),
                        painter = painterResource(Res.drawable.smartphone),
                        contentDescription = ""
                    )
                } else {
                    Icon(
                        modifier = Modifier.padding(horizontal = 6.dp),
                        painter = painterResource(Res.drawable.computer),
                        contentDescription = ""
                    )
                }
            }
            if (device.self) {
                Text(
                    "This device",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

fun AllLinks.deviceName() = link_name ?: installation_name
