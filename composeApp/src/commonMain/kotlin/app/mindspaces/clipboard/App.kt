package app.mindspaces.clipboard

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.rememberCircuitNavigator
import com.slack.circuitx.gesturenavigation.GestureNavigationDecoration
import me.tatarka.inject.annotations.Inject
import org.jetbrains.compose.ui.tooling.preview.Preview

typealias ClipboardApp = @Composable () -> Unit

@Inject
@Composable
@Preview
fun ClipboardApp(circuit: Circuit) {
    MaterialTheme {
        val backStack = rememberSaveableBackStack(MainScreen)
        val navigator = rememberCircuitNavigator(backStack) { }

        CircuitCompositionLocals(circuit) {
            NavigableCircuitContent(
                navigator = navigator,
                backStack = backStack,
                decoration = GestureNavigationDecoration(
                    onBackInvoked = navigator::pop
                )
            )
        }
    }
}
