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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mindspaces.clipboard.AuthScreen.Event.Authenticate
import app.mindspaces.clipboard.AuthScreen.Event.Back
import app.mindspaces.clipboard.AuthScreen.Event.Login
import app.mindspaces.clipboard.api.MinSecretSize
import app.mindspaces.clipboard.components.SecretInputField
import app.mindspaces.clipboard.components.SimpleInputField
import app.mindspaces.clipboard.parcel.CommonParcelize
import app.mindspaces.clipboard.repo.AuthRepository
import app.mindspaces.clipboard.repo.RepoResult
import co.touchlab.kermit.Logger
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.internal.rememberStableCoroutineScope
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import org.jetbrains.compose.resources.Font
import sharedclipboard.composeapp.generated.resources.Anton_Regular
import sharedclipboard.composeapp.generated.resources.Res
import software.amazon.lastmile.kotlin.inject.anvil.AppScope

@CommonParcelize
data object AuthScreen : Screen {
    data class State(
        val loading: Boolean,
        val snackbarHostState: SnackbarHostState,
        val eventSink: (Event) -> Unit
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data class Authenticate(val name: String, val email: String, val secret: String) : Event
        data object Login : Event
        data object Back : Event
    }
}

@CircuitInject(AuthScreen::class, AppScope::class)
@Inject
class AuthPresenter(
    @Assisted private val navigator: Navigator,
    private val authRepository: AuthRepository
) : Presenter<AuthScreen.State> {
    private val log = Logger.withTag("AuthScreen")

    @Composable
    override fun present(): AuthScreen.State {
        val scope = rememberStableCoroutineScope()

        var loading by rememberRetained { mutableStateOf(false) }

        val snackbarHostState = remember { SnackbarHostState() }

        fun showSnackbar(text: String) {
            scope.launch(Dispatchers.Main) {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(text, duration = SnackbarDuration.Short)
            }
        }

        return AuthScreen.State(loading, snackbarHostState) { event ->
            when (event) {
                is Authenticate -> {
                    loading = true
                    scope.launch(Dispatchers.IO) {
                        val account = authRepository.signup(event.name, event.secret, event.email)
                        when (account) {
                            is RepoResult.Data -> {
                                log.i { "signup successful - name=${account.data.account.name}" }
                                withContext(Dispatchers.Main) {
                                    navigator.pop()
                                }
                            }

                            is RepoResult.ValidationError -> {
                                log.w { "validation errors: ${account.errors}" }
                                account.errors.forEach { err ->
                                    log.w { "${err.key}: ${err.value.contentDeepToString()}" }
                                }
                                showSnackbar("Please check your inputs")
                            }

                            else -> showSnackbar("No internet connection")
                        }
                    }.invokeOnCompletion {
                        loading = false
                    }
                }

                is Login -> navigator.goTo(SignInScreen)
                is Back -> navigator.pop()
            }
        }
    }
}

@CircuitInject(AuthScreen::class, AppScope::class)
@Composable
fun AuthView(state: AuthScreen.State, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxWidth(),
        snackbarHost = { SnackbarHost(hostState = state.snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = modifier.fillMaxSize().padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            /*Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().background(Color.Green)
            ) {
                IconButton(
                    onClick = {
                        state.eventSink(Back)
                    },
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Back"
                    )
                }

                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Red),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Create Account".uppercase(),
                        //modifier = Modifier.fillMaxWidth(),
                        //color = Color.Black,
                        fontSize = 24.sp,
                        //fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily(Font(Res.font.Anton_Regular))
                    )
                }
            }*/

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Create Account".uppercase(),
                    fontSize = 24.sp,
                    fontFamily = FontFamily(Font(Res.font.Anton_Regular))
                )
            }

            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier.width(960.dp).imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                /*Text(
                    text = "Enter a name to start using the Shared Clipboard",
                    modifier = Modifier.fillMaxWidth()
                )*/

                var name by rememberRetained { mutableStateOf("") }
                val nameValid = name.isNotBlank()
                SimpleInputField(
                    label = "Name",
                    value = name,
                    valid = nameValid,
                    onValueChange = { name = it }
                )

                var email by rememberRetained { mutableStateOf("") }
                val emailValid = email.isNotBlank() && email.contains("@")
                SimpleInputField(
                    label = "E-Mail",
                    value = email,
                    valid = emailValid,
                    onValueChange = { email = it },
                    errorText = "valid email required"
                )

                var secret by rememberRetained { mutableStateOf("") }
                val secretValid = secret.length >= MinSecretSize
                SecretInputField(
                    label = "Password",
                    value = secret,
                    valid = secretValid,
                    onValueChanged = { secret = it },
                    errorText = "minimum $MinSecretSize characters"
                )

                Spacer(Modifier.height(24.dp))

                val ok = nameValid && emailValid && secretValid //&& secret2Valid

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
                    TextButton(onClick = {
                        state.eventSink(Login)
                    }) {
                        Text("Login instead?")
                    }
                    Spacer(Modifier.width(16.dp))
                    TextButton(
                        enabled = ok && !state.loading,
                        onClick = {
                            // TODO hide keyboard
                            // TODO always enable button? and show snackbar when still in error case?
                            state.eventSink(Authenticate(name, email, secret))
                        },
                    ) {
                        Text("Create Account")
                    }
                }
                Row(Modifier.align(Alignment.End)) {
                    Spacer(Modifier.weight(1f))

                    TextButton(onClick = {
                        state.eventSink(Back)
                    }) {
                        Text("")
                    }
                }

                /*Button(
                    onClick = {
                        state.eventSink(Authenticate(name, email, secret))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Create Account")
                }

                TextButton(
                    onClick = {},
                ) {
                    Text("I already have an account / other device")
                }*/
            }
        }
    }
}
