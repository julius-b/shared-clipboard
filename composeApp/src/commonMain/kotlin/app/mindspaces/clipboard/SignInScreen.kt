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
import app.mindspaces.clipboard.SignInScreen.Event.Authenticate
import app.mindspaces.clipboard.SignInScreen.Event.Back
import app.mindspaces.clipboard.api.ApiError
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
data object SignInScreen : Screen {
    data class State(
        val loading: Boolean,
        val snackbarHostState: SnackbarHostState,
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

        val snackbarHostState = remember { SnackbarHostState() }

        fun showSnackbar(text: String) {
            scope.launch(Dispatchers.Main) {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(text, duration = SnackbarDuration.Short)
            }
        }

        return SignInScreen.State(loading, snackbarHostState) { event ->
            when (event) {
                is Authenticate -> {
                    loading = true
                    scope.launch(Dispatchers.IO) {
                        val session = authRepository.login(event.unique, event.secret)
                        when (session) {
                            is RepoResult.Data -> {
                                log.i { "session created - name=${session.data.account.name}" }
                                withContext(Dispatchers.Main) {
                                    navigator.pop()
                                    navigator.pop()
                                }
                            }

                            is RepoResult.ValidationError -> {
                                log.w { "validation errors: ${session.errors}" }
                                session.errors?.forEach { err ->
                                    log.w { "${err.key}: ${err.value.contentDeepToString()}" }
                                    when (err.key) {
                                        "unique" -> {
                                            if (err.value.any { it is ApiError.Reference }) {
                                                showSnackbar("unknown e-mail")
                                                return@launch
                                            }
                                        }

                                        "secret" -> {
                                            if (err.value.any { it is ApiError.Forbidden }) {
                                                showSnackbar("wrong password")
                                                return@launch
                                            }
                                        }
                                    }
                                }

                                showSnackbar("Please check your inputs")
                            }

                            else -> {
                                log.w { "network error: $session" }
                                showSnackbar("Network error")
                            }
                        }
                    }.invokeOnCompletion {
                        loading = false
                    }
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
    // TODO share snachbarHostState across app?
    Scaffold(
        modifier = Modifier.fillMaxWidth(),
        snackbarHost = { SnackbarHost(hostState = state.snackbarHostState) }
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
                    fontSize = 24.sp,
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

                var secret by rememberRetained { mutableStateOf("") }
                val secretValid = secret.length >= MinSecretSize
                SecretInputField(
                    label = "Password",
                    value = secret,
                    valid = secretValid,
                    onValueChanged = { secret = it },
                    errorText = "minimum $MinSecretSize characters"
                )

                Spacer(Modifier.height(16.dp))

                val ok = uniqueValid && secretValid

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
                    TextButton(
                        enabled = ok && !state.loading,
                        onClick = {
                            state.eventSink(Authenticate(unique, secret))
                        },
                    ) {
                        Text("Login")
                    }
                }
            }
        }
    }
}
