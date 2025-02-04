package app.mindspaces.clipboard

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import app.mindspaces.clipboard.MediaDetailScreen.Event.Back
import app.mindspaces.clipboard.api.MediaType
import app.mindspaces.clipboard.components.SimpleScaffold
import app.mindspaces.clipboard.data.fileName
import app.mindspaces.clipboard.data.toFileModel
import app.mindspaces.clipboard.db.Media
import app.mindspaces.clipboard.parcel.CommonParcelize
import app.mindspaces.clipboard.repo.MediaRepository
import chaintech.videoplayer.host.VideoPlayerHost
import chaintech.videoplayer.ui.video.VideoPlayerComposable
import co.touchlab.kermit.Logger
import coil3.compose.AsyncImage
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
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import java.util.UUID

@CommonParcelize
data class MediaDetailScreen(
    val mediaId: UUID
) : Screen {
    sealed interface State : CircuitUiState {
        val title: String
        val eventSink: (Event) -> Unit

        // loading type: requesting from client, waiting for client upload, downloading from server
        data class Loading(
            override val eventSink: (Event) -> Unit
        ) : State {
            override val title = "Loading"
        }

        data class View(
            val media: Media,
            val video: String?,
            override val eventSink: (Event) -> Unit
        ) : State {
            override val title = media.path.fileName()
        }
    }

    sealed interface Event : CircuitUiEvent {
        data object Back : Event
    }
}

@CircuitInject(MediaDetailScreen::class, AppScope::class)
@Inject
class MediaDetailPresenter(
    @Assisted private val screen: MediaDetailScreen,
    @Assisted private val navigator: Navigator,
    private val mediaRepository: MediaRepository
) : Presenter<MediaDetailScreen.State> {
    private val log = Logger.withTag("MediaDetailScreen")

    init {
        log.i { "init - mediaId: ${screen.mediaId}" }
    }

    @Composable
    override fun present(): MediaDetailScreen.State {
        val scope = rememberStableCoroutineScope()
        val title = ""

        // TODO allow user to delete local copies to save space
        var video by rememberRetained { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) {
            scope.launch(Dispatchers.IO) {
                // TODO only if video!
                log.i { "downloading: ${screen.mediaId}..." }
                val path = mediaRepository.downloadMedia(screen.mediaId)
                withContext(Dispatchers.Main) {
                    video = path
                }
            }.invokeOnCompletion { e ->
                if (e != null) {
                    log.e(e) { "load failed: ${screen.mediaId}" }
                    // TODO show error view
                }
            }
        }

        val media by mediaRepository.query(screen.mediaId).collectAsRetainedState(null)

        fun onEvent(event: MediaDetailScreen.Event) {
            when (event) {
                is Back -> navigator.pop()
            }
        }

        return media?.let { m ->
            if (m.mediaType == MediaType.Video) {
                if (video != null) {
                    println("loaded :)")
                    MediaDetailScreen.State.View(m, video, ::onEvent)
                } else MediaDetailScreen.State.Loading(::onEvent)
                // TODO launch download, show progress in LoadingUI
            }
            MediaDetailScreen.State.View(m, video, ::onEvent)
        } ?: MediaDetailScreen.State.Loading(::onEvent)
    }
}

@CircuitInject(MediaDetailScreen::class, AppScope::class)
@Composable
fun MediaDetailView(state: MediaDetailScreen.State, modifier: Modifier = Modifier) {
    // TODO share icon to open in another app (open in vs share, download on desktop and xdg-open)
    SimpleScaffold(modifier, state.title, onBack = {
        state.eventSink(Back)
    }) {
        when (state) {
            is MediaDetailScreen.State.Loading -> DetailLoadingView(state)
            is MediaDetailScreen.State.View -> DetailView(state)
        }
    }
}

@Composable
fun ColumnScope.DetailView(state: MediaDetailScreen.State.View) {
    if (state.media.mediaType == MediaType.Video) {
        // TODO VideoLoadedState with video != null
        if (state.video == null) {
            Text("Loading...")
            return
        }
        val playerHost = remember {
            VideoPlayerHost(
                // TODO
                url = state.video
            )
        }

        VideoPlayerComposable(
            modifier = Modifier.fillMaxSize(),
            playerHost = playerHost
        )
    } else {
        AsyncImage(
            modifier = Modifier.fillMaxSize(),
            model = state.media.toFileModel(),
            contentDescription = null,
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun DetailLoadingView(state: MediaDetailScreen.State.Loading) {

}
