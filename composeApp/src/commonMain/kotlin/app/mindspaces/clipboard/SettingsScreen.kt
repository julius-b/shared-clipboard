package app.mindspaces.clipboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import app.mindspaces.clipboard.SettingsScreen.Event.Back
import app.mindspaces.clipboard.parcel.CommonParcelize
import app.mindspaces.clipboard.repo.AuthRepository
import co.touchlab.kermit.Logger
import com.slack.circuit.codegen.annotations.CircuitInject
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
data object SettingsScreen : Screen {
    data class State(
        val eventSink: (Event) -> Unit
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
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
        return SettingsScreen.State { event ->
            when (event) {
                is Back -> navigator.pop()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@CircuitInject(SettingsScreen::class, AppScope::class)
@Composable
fun SettingsView(state: SettingsScreen.State, modifier: Modifier = Modifier) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings".uppercase(),
                        fontSize = 24.sp,
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
        Column(modifier = Modifier.padding(innerPadding)) {
            AccountBox()
        }
    }
}

@Composable
fun AccountBox() {
    Text("Account Box")
}
