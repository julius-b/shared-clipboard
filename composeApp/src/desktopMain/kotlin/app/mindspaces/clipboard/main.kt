package app.mindspaces.clipboard

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.mindspaces.clipboard.data.RealTimeClient
import app.mindspaces.clipboard.data.generateThumbnails
import app.mindspaces.clipboard.di.DesktopApplicationComponent
import app.mindspaces.clipboard.di.create
import coil3.SingletonImageLoader
import com.slack.circuit.runtime.internal.rememberStableCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

fun main() = application {
    //val app = remember { DesktopApplicationComponent::class.create() }
    val component = remember { DesktopApplicationComponent.create() }
    SingletonImageLoader.setSafe { component.imageLoader }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Access Anywhere",
        // In lieu of a global shortcut handler, we best-effort with this
        // https://github.com/slackhq/circuit/blob/0.25.0/samples/star/src/jvmMain/kotlin/com/slack/circuit/star/main.kt#L67
        // https://youtrack.jetbrains.com/issue/CMP-5337
        onKeyEvent = { event ->
            when {
                event.key == Key.Escape -> {
                    true
                }

                else -> false
            }
        }
    ) {
        val scope = rememberStableCoroutineScope()

        // TODO launch from menu bar thing
        LaunchedEffect(Unit) {
            // TODO supervisor-scope...
            scope.launch(Dispatchers.IO) {
                val rtClient = RealTimeClient(
                    component.httpClient,
                    component.installationRepository,
                    component.authRepository,
                    component.mediaRepository
                )
                rtClient.connect()
            }

            scope.launch(Dispatchers.IO) {
                component.mediaRepository.handleMediaUpdates()
            }

            scope.launch(Dispatchers.IO) {
                // TODO also consider drive roots, etc.
                // TODO allow user to pick more roots
                // NOTE: for now desktop is not supported (blocking: windows paths to "unix-y" paths)
                val homeDir = File(System.getProperty("user.home"))
                println("home-dir: $homeDir")
                //traverseFiles(
                //    File(homeDir, "Pictures"), component.mediaRepository, component.appDirs
                //)
            }

            scope.launch(Dispatchers.IO) {
                generateThumbnails(component.mediaRepository, component.appDirs)
            }
        }

        component.clipboardApp()
    }
}
