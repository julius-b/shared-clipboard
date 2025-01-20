package app.mindspaces.clipboard

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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mindspaces.clipboard.DeviceScreen.Event.Back
import app.mindspaces.clipboard.components.SimpleInputField
import app.mindspaces.clipboard.db.AllLinks
import app.mindspaces.clipboard.parcel.CommonParcelize
import app.mindspaces.clipboard.repo.AuthRepository
import app.mindspaces.clipboard.repo.InstallationRepository
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.collectAsRetainedState
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import org.jetbrains.compose.resources.Font
import sharedclipboard.composeapp.generated.resources.Anton_Regular
import sharedclipboard.composeapp.generated.resources.Res
import software.amazon.lastmile.kotlin.inject.anvil.AppScope

@CommonParcelize
data object DeviceScreen : Screen {
    data class State(
        val devices: List<AllLinks>,
        val eventSink: (Event) -> Unit
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data object Back : Event
    }
}

@CircuitInject(DeviceScreen::class, AppScope::class)
@Inject
class DevicePresenter(
    @Assisted private val navigator: Navigator,
    private val authRepository: AuthRepository,
    private val installationRepository: InstallationRepository
) : Presenter<DeviceScreen.State> {

    @Composable
    override fun present(): DeviceScreen.State {
        val self by authRepository.self().collectAsRetainedState(null)
        val devices by installationRepository.allLinks().collectAsRetainedState(listOf())

        return DeviceScreen.State(
            devices
        ) { event ->
            when (event) {
                is Back -> navigator.pop()
            }
        }
    }
}

// TODO collapsible card, no detail view necessary
// TODO change device name route, save response locally to update db
@OptIn(ExperimentalMaterial3Api::class)
@CircuitInject(DeviceScreen::class, AppScope::class)
@Composable
fun DeviceView(state: DeviceScreen.State, modifier: Modifier = Modifier) {
    Scaffold(
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
            LazyColumn {
                items(state.devices) { device ->
                    DeviceCard(device, updateName = {

                    })
                }
            }
        }
    }
}

@Composable
fun DeviceCard(device: AllLinks, updateName: (String) -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            var editing by rememberRetained { mutableStateOf(false) }
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // TODO ensure new value is visible in text input after saving the first time
                var deviceName by rememberRetained { mutableStateOf(device.deviceName()) }
                if (editing) {
                    SimpleInputField(
                        label = "Device Name",
                        value = deviceName,
                        valid = deviceName.isNotBlank(),
                        onValueChange = {
                            deviceName = it
                        },
                        modifier = Modifier.weight(1f)
                    )
                    // TODO cancel button when editing, keep old value
                } else {
                    Text(
                        deviceName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = {
                        if (editing) {
                            // TODO only set edit status to done
                            // TODO --->> edit status needs to be part of device (-> DisplayDevice)
                            updateName(deviceName)
                        }
                        editing = !editing
                    },
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Icon(
                        imageVector = if (editing) Icons.Default.CheckCircle else Icons.Default.Edit,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

fun AllLinks.deviceName() = name ?: name_
