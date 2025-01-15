package app.mindspaces.clipboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mindspaces.clipboard.SignInScreen.Event.Authenticate
import app.mindspaces.clipboard.SignInScreen.Event.Back
import app.mindspaces.clipboard.components.SimpleInputField
import app.mindspaces.clipboard.parcel.CommonParcelize
import app.mindspaces.clipboard.repo.AuthRepository
import co.touchlab.kermit.Logger
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.internal.rememberStableCoroutineScope
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import org.jetbrains.compose.resources.Font
import sharedclipboard.composeapp.generated.resources.Anton_Regular
import sharedclipboard.composeapp.generated.resources.Res
import software.amazon.lastmile.kotlin.inject.anvil.AppScope

@CommonParcelize
data object SignInScreen : Screen {
    data class State(
        val loading: Boolean,
        val eventSink: (Event) -> Unit
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data class Authenticate(val unique: String, val secret: String) : Event
        data object Back : Event
    }
}

@CircuitInject(SignInScreen::class, AppScope::class)
@Inject
class SignInPresenter(
    @Assisted private val navigator: Navigator,
    private val authRepository: AuthRepository
) : Presenter<SignInScreen.State> {
    private val log = Logger.withTag("SignInScreen")

    @Composable
    override fun present(): SignInScreen.State {
        val scope = rememberStableCoroutineScope()

        var loading by rememberRetained { mutableStateOf(false) }

        return SignInScreen.State(loading) { event ->
            when (event) {
                is Authenticate -> {
                    loading = true
                    // TODO ...
                }

                is Back -> {
                    navigator.pop()
                }
            }
        }
    }
}

@CircuitInject(SignInScreen::class, AppScope::class)
@Composable
fun SignInView(state: SignInScreen.State, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = Modifier.fillMaxWidth()
    ) { innerPadding ->
        Column(
            modifier = modifier.fillMaxSize().padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Login".uppercase(),
                    //modifier = Modifier.fillMaxWidth(),
                    //color = Color.Black,
                    fontSize = 24.sp,
                    //fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily(Font(Res.font.Anton_Regular))
                )
            }

            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier.width(960.dp).imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                var unique by rememberRetained { mutableStateOf("") }
                val uniqueValid = unique.isNotBlank()
                SimpleInputField(
                    label = "E-Mail",
                    value = unique,
                    valid = uniqueValid,
                    onValueChange = { unique = it }
                )

                Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        state.eventSink(Back)
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
