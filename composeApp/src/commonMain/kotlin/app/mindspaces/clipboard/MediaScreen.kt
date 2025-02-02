package app.mindspaces.clipboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.mindspaces.clipboard.MediaScreen.Event.Back
import app.mindspaces.clipboard.MediaScreen.Event.MediaClicked
import app.mindspaces.clipboard.MediaScreen.Event.ToggleDevice
import app.mindspaces.clipboard.components.SimpleScaffold
import app.mindspaces.clipboard.data.fileName
import app.mindspaces.clipboard.data.toThumbModel
import app.mindspaces.clipboard.db.AllLinks
import app.mindspaces.clipboard.db.Media
import app.mindspaces.clipboard.parcel.CommonParcelable
import app.mindspaces.clipboard.parcel.CommonParcelize
import app.mindspaces.clipboard.repo.InstallationRepository
import app.mindspaces.clipboard.repo.MediaRepository
import co.touchlab.kermit.Logger
import coil3.compose.AsyncImage
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.collectAsRetainedState
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import java.util.UUID

@CommonParcelize
data class MediaScreen(
    val dir: Directory? = null
) : Screen {
    data class State(
        // NOTE: properties might be outdated
        val title: String,
        val dir: String?,
        val devices: List<AllLinks>,
        val deselected: Set<UUID>,
        val medias: List<DisplayMedia>,
        val eventSink: (Event) -> Unit
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data class MediaClicked(val media: DisplayMedia) : Event
        data class ToggleDevice(val device: DeviceFilter) : Event
        data object Back : Event
    }
}

@CommonParcelize
data class Directory(
    val path: String,
    val installationId: UUID?
) : CommonParcelable

data class DisplayMedia(
    val media: Media,
    val name: String
) {
    fun toDirectory() = Directory(media.dir, media.installation_id)
}

data class DeviceFilter(
    val installationId: UUID,
    val self: Boolean
)

fun AllLinks.toFilter() = DeviceFilter(installation_id, self)

@CircuitInject(MediaScreen::class, AppScope::class)
@Inject
class MediaPresenter(
    @Assisted private val screen: MediaScreen,
    @Assisted private val navigator: Navigator,
    private val installationRepository: InstallationRepository,
    private val mediaRepository: MediaRepository
) : Presenter<MediaScreen.State> {
    private val log = Logger.withTag("MediaScreen")

    init {
        log.i { "init - dir: ${screen.dir}" }
    }

    @Composable
    override fun present(): MediaScreen.State {
        val title = screen.dir?.path?.substringAfterLast('/') ?: "Gallery"

        //val installation by installationRepository.self().collectAsRetainedState(null)
        val devices by installationRepository.allLinks().collectAsRetainedState(listOf())
        val deselected = rememberRetained { mutableStateMapOf<UUID, Boolean>() }

        // TODO move deselected into sql? remember(deselected)
        val medias by mediaRepository.list(screen.dir?.path, screen.dir?.installationId)
            .map { medias ->
                log.i { "medias: $medias" }
                withContext(IO) {
                    medias.map {
                        val name =
                            if (screen.dir == null) it.dir.fileName()
                            else it.path.fileName()

                        DisplayMedia(it, name)
                    }
                }
            }.collectAsRetainedState(listOf())

        val filteredMedias by rememberRetained(medias, deselected) {
            derivedStateOf {
                // not necessary in detail view, installation_id handled db-side
                if (screen.dir != null) return@derivedStateOf medias
                medias
                    .filter { it.media.installation_id !in deselected }
                    // if self is deselected, any media with iid = null is filtered out
                    .filter { deselected.none { it.value } || it.media.installation_id != null }
                //.toImmutableList()
            }
        }

        // UI: app freeze
        //val all by mediaRepository.all().collectAsRetainedState(listOf())

        return MediaScreen.State(
            title,
            screen.dir?.path,
            devices,
            deselected.keys,
            filteredMedias
        ) { event ->
            when (event) {
                is MediaClicked -> {
                    if (screen.dir == null) {
                        log.i { "entering dir: ${event.media}" }
                        navigator.goTo(MediaScreen(event.media.toDirectory()))
                        return@State
                    }
                    navigator.goTo(MediaDetailScreen(event.media.media.id))
                }

                is ToggleDevice -> {
                    if (deselected.containsKey(event.device.installationId)) deselected -= event.device.installationId
                    else deselected[event.device.installationId] = event.device.self
                }

                is Back -> navigator.pop()
            }
        }
    }
}

@CircuitInject(MediaScreen::class, AppScope::class)
@Composable
fun MediaView(state: MediaScreen.State, modifier: Modifier = Modifier) {
    SimpleScaffold(modifier, state.title, onBack = { state.eventSink(Back) }) {
        if (state.dir == null) {
            LazyRow {
                // link_is guaranteed to be unique, TODO ensure installation_id is also
                items(state.devices, key = { it.installation_id }) { device ->
                    val name =
                        device.deviceName() + if (device.self) " (this device)" else " (${device.os})"
                    val selected = device.installation_id !in state.deselected
                    FilterChip(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        selected = selected,
                        leadingIcon = {
                            // displaying an icon either way prevents resize
                            if (selected) Icon(
                                Icons.Filled.CheckCircle,
                                tint = MaterialTheme.colorScheme.primary,
                                contentDescription = null
                            )
                            else Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            state.eventSink(ToggleDevice(device.toFilter()))
                        },
                        label = { Text(name) }
                    )
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp)
        ) {
            // TODO contentType, in flow insert header?
            items(state.medias, { it.media.id }) { media ->
                Box(modifier = Modifier.clickable {
                    state.eventSink(MediaClicked(media))
                }) {
                    // loading the thumbnail is slow, don't do it in the flow
                    // lazy load images, don't load all before showing the list
                    AsyncImage(
                        modifier = Modifier.aspectRatio(1f)
                            .fillMaxSize()
                            // TODO no border to the outside
                            //.border(1.dp, Color.White)
                            .drawWithCache {
                                val gradient = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black),
                                    startY = size.height / 3,
                                    endY = size.height
                                )
                                onDrawWithContent {
                                    drawContent()
                                    drawRect(gradient, blendMode = BlendMode.Multiply)
                                }
                            },
                        model = media.media.toThumbModel(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop
                    )
                    Text(
                        modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                        text = media.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun DevicesDropdown() {
    var expanded by rememberRetained { mutableStateOf(false) }

    Column(
        modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth()
    ) {
        TextButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { expanded = !expanded }
        ) {
            Text("Devices selected: ...")
        }
        DropdownMenu(
            modifier = Modifier.fillMaxWidth(),
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            var selected by rememberRetained { mutableStateOf(false) }
            DropdownMenuItem(
                modifier = Modifier.fillMaxWidth(),
                text = { Text("Device 1") },
                onClick = {
                    selected = !selected
                },
                leadingIcon = {
                    if (selected) Icon(Icons.Outlined.Check, contentDescription = null)
                },
            )
        }
    }
}

fun <E> MutableSet<E>.toggle(e: E) {
    if (e in this) this -= e
    else this += e
}

fun <K> MutableMap<K, Unit>.toggle(key: K) {
    if (key in this) this -= key
    else this[key] = Unit
}
