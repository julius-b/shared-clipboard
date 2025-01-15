package app.mindspaces.clipboard

import androidx.compose.runtime.Composable
import app.mindspaces.clipboard.parcel.CommonParcelize
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import java.util.UUID

@CommonParcelize
data class DeviceScreen(
    val installationID: UUID
) : Screen {
    data class State(
        val eventSink: (Event) -> Unit
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
    }
}

@CircuitInject(DeviceScreen::class, AppScope::class)
@Inject
class DevicePresenter(
    @Assisted private val screen: DeviceScreen,
    @Assisted private val navigator: Navigator,
) : Presenter<DeviceScreen.State> {

    @Composable
    override fun present(): DeviceScreen.State {
        return DeviceScreen.State { }
    }
}
