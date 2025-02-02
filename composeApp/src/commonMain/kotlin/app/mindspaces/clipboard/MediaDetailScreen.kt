package app.mindspaces.clipboard

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import app.mindspaces.clipboard.MediaDetailScreen.Event.Back
import app.mindspaces.clipboard.components.SimpleScaffold
import app.mindspaces.clipboard.data.fileName
import app.mindspaces.clipboard.data.toFileModel
import app.mindspaces.clipboard.db.Media
import app.mindspaces.clipboard.parcel.CommonParcelize
import app.mindspaces.clipboard.repo.MediaRepository
import co.touchlab.kermit.Logger
import coil3.compose.AsyncImage
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.collectAsRetainedState
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
        val title = ""

        val media by mediaRepository.query(screen.mediaId).collectAsRetainedState(null)

        fun onEvent(event: MediaDetailScreen.Event) {
            when (event) {
                is Back -> navigator.pop()
            }
        }

        return when {
            media == null -> MediaDetailScreen.State.Loading(::onEvent)
            else -> MediaDetailScreen.State.View(media!!, ::onEvent)
        }
    }
}

@CircuitInject(MediaDetailScreen::class, AppScope::class)
@Composable
fun MediaDetailView(state: MediaDetailScreen.State, modifier: Modifier = Modifier) {
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
    AsyncImage(
        modifier = Modifier.fillMaxSize(),
        model = state.media.toFileModel(),
        contentDescription = null,
        contentScale = ContentScale.Crop
    )
}

@Composable
fun DetailLoadingView(state: MediaDetailScreen.State.Loading) {

}
