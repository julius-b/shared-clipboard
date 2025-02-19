package app.mindspaces.clipboard

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import app.mindspaces.clipboard.MediaDetailScreen.Event.Back
import app.mindspaces.clipboard.MediaDetailScreen.Event.Share
import app.mindspaces.clipboard.MediaDetailScreen.State
import app.mindspaces.clipboard.api.MediaType
import app.mindspaces.clipboard.components.SimpleScaffold
import app.mindspaces.clipboard.data.PlatformIO
import app.mindspaces.clipboard.data.toFileModel
import app.mindspaces.clipboard.db.Media
import app.mindspaces.clipboard.parcel.CommonParcelize
import app.mindspaces.clipboard.repo.MediaRepository
import app.mindspaces.clipboard.utils.mediaName
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
import kotlinx.coroutines.CancellationException
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
            // whether file is available on server
            val synced: Boolean?,
            val percentage: Int?,
            override val eventSink: (Event) -> Unit
        ) : State {
            override val title = "Loading"
        }

        data class Image(
            val media: Media,
            override val eventSink: (Event) -> Unit
        ) : State {
            override val title = media.path.mediaName
        }

        data class Video(
            val media: Media,
            val video: String,
            override val eventSink: (Event) -> Unit
        ) : State {
            override val title = media.path.mediaName
        }

        data class Error(
            override val eventSink: (Event) -> Unit
        ) : State {
            override val title = "Error"
        }
    }

    sealed interface Event : CircuitUiEvent {
        data class Share(val path: String) : Event
        data object Back : Event
    }
}

@CircuitInject(MediaDetailScreen::class, AppScope::class)
@Inject
class MediaDetailPresenter(
    @Assisted private val screen: MediaDetailScreen,
    @Assisted private val navigator: Navigator,
    private val mediaRepository: MediaRepository,
    private val platformIO: PlatformIO
) : Presenter<State> {
    private val log = Logger.withTag("MediaDetailScreen")

    init {
        log.i { "init - mediaId: ${screen.mediaId}" }
    }

    @Composable
    override fun present(): State {
        val scope = rememberStableCoroutineScope()
        val uriHandler = LocalUriHandler.current

        val media by mediaRepository.query(screen.mediaId).collectAsRetainedState(null)

        // TODO allow user to delete local copies to save space
        var video by rememberRetained { mutableStateOf<String?>(null) }
        var percentage by rememberRetained { mutableStateOf<Int?>(0) }
        var error by rememberRetained { mutableStateOf(false) }
        // TODO don't re-run when media meta changes? except maybe it makes sense
        LaunchedEffect(media) {
            video = null
            percentage = 0
            error = false
            media?.let { m ->
                if (m.mediaType != MediaType.Video) {
                    log.i { "not downloading media type: ${m.mediaType}" }
                    return@LaunchedEffect
                }
                if (m.installation_id == null) {
                    video = m.path
                    return@LaunchedEffect
                }
                scope.launch(Dispatchers.IO) {
                    log.i { "downloading: ${m.id}..." }
                    val path = mediaRepository.downloadMedia(m.id, m.size) {
                        withContext(Dispatchers.Main) {
                            percentage = it
                        }
                    }
                    withContext(Dispatchers.Main) {
                        video = path
                    }
                }.invokeOnCompletion { e ->
                    if (e is CancellationException) {
                        log.w { "download cancelled: ${m.id}" }
                        return@invokeOnCompletion
                    }
                    if (e != null) {
                        log.e(e) { "load failed: ${m.id}" }
                        error = true
                    }
                }
            }
        }

        fun onEvent(event: MediaDetailScreen.Event) {
            when (event) {
                is Back -> navigator.pop()
                is Share -> platformIO.shareFile(uriHandler, event.path)
            }
        }

        if (error) return State.Error(::onEvent)

        return media?.let { m ->
            // TODO if m.hasFile == false -> state "NoData" (UI: button to request, see if origin device is online)
            //      - if request is already stored server-side: show "waiting for device to come online" (or instruct to open app on origin device)
            //      - sharing origin device upload progress? server doesn't currently track this, but would also be useful for general monitoring
            if (m.installation_id != null && !m.synced) {
                // TODO show "request file" button
                return@let State.Loading(false, null, ::onEvent)
            }
            if (m.mediaType == MediaType.Video) {
                return@let video?.let { v ->
                    State.Video(m, v, ::onEvent)
                } ?: State.Loading(m.synced, percentage, ::onEvent)
            }
            State.Image(m, ::onEvent)
        } ?: State.Loading(null, null, ::onEvent)
    }
}

@CircuitInject(MediaDetailScreen::class, AppScope::class)
@Composable
fun MediaDetailView(state: State, modifier: Modifier = Modifier) {
    SimpleScaffold(modifier, state.title, onBack = {
        state.eventSink(Back)
    }, onAction = {
        // TODO (desktop) open directory media is in, with file highlight
        when (state) {
            is State.Video -> {
                // toUri creates real uri (file:// %20)
                // modal: open in vs share, download on desktop and xdg-open
                state.eventSink(Share(state.video))
            }

            is State.Image -> {
                // TODO download locally (save with extension) or share binary data from coil cache
                if (state.media.installation_id == null)
                    state.eventSink(Share(state.media.path))
            }

            else -> {}
        }
    }, actionIcon = {
        Icon(
            imageVector = Icons.Filled.Share,
            contentDescription = "Share"
        )
    }) {
        when (state) {
            is State.Loading -> DetailLoadingView(state)
            is State.Image -> DetailImageView(state)
            is State.Video -> DetailVideoView(state)
            is State.Error -> DetailErrorView(state)
        }
    }
}

@Composable
fun DetailVideoView(state: State.Video) {
    val playerHost = remember {
        VideoPlayerHost(
            url = state.video
        )
    }

    // TODO handle correct rotation
    VideoPlayerComposable(
        modifier = Modifier.fillMaxSize(),
        playerHost = playerHost
    )
}

@Composable
fun ColumnScope.DetailImageView(state: State.Image) {
    AsyncImage(
        modifier = Modifier.fillMaxSize(),
        model = state.media.toFileModel(),
        contentDescription = null,
        contentScale = ContentScale.Crop
    )
}

@Composable
fun DetailLoadingView(state: State.Loading) {
    // TODO progress bar
    Text("Loading (${state.percentage}%)...")
}

@Composable
fun DetailErrorView(state: State.Error) {
    Text("An error occurred while loading the media, please try again later.")
}
