package app.mindspaces.clipboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mindspaces.clipboard.MediaScreen.Event.Back
import app.mindspaces.clipboard.MediaScreen.Event.MediaClicked
import app.mindspaces.clipboard.data.ThumbFetcher
import app.mindspaces.clipboard.data.ThumbFetcherCoilModel
import app.mindspaces.clipboard.db.Media
import app.mindspaces.clipboard.parcel.CommonParcelize
import app.mindspaces.clipboard.repo.MediaRepository
import ca.gosyer.appdirs.AppDirs
import co.touchlab.kermit.Logger
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.fetch.Fetcher
import coil3.request.ImageRequest
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
import org.jetbrains.compose.resources.Font
import sharedclipboard.composeapp.generated.resources.Anton_Regular
import sharedclipboard.composeapp.generated.resources.Res
import software.amazon.lastmile.kotlin.inject.anvil.AppScope

@CommonParcelize
data class MediaScreen(
    val dir: String? = null
) : Screen {
    data class State(
        // NOTE: properties might be outdated
        val title: String,
        val medias: List<DisplayMedia>,
        val getAppDirs: () -> AppDirs,
        val eventSink: (Event) -> Unit
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data class MediaClicked(val media: DisplayMedia) : Event
        data object Back : Event
    }
}

enum class MediaType {
    Directory, File
}

data class MediaWithThumb(
    val media: Media,
    val type: MediaType,
    val name: String,
    val thumb: ImageBitmap?
)

data class DisplayMedia(
    val media: Media,
    val type: MediaType,
    val name: String,
)

@CircuitInject(MediaScreen::class, AppScope::class)
@Inject
class MediaPresenter(
    @Assisted private val screen: MediaScreen,
    @Assisted private val navigator: Navigator,
    private val mediaRepository: MediaRepository,
    private val appDirs: AppDirs
) : Presenter<MediaScreen.State> {
    val log = Logger.withTag("MediaScreen")

    @Composable
    override fun present(): MediaScreen.State {
        val title = screen.dir?.substringAfterLast('/') ?: "Gallery"
        val medias by mediaRepository.list(screen.dir).map { medias ->
            log.i { "medias: $medias" }
            withContext(IO) {
                medias.map {
                    // TODO cross platform
                    val name =
                        if (screen.dir == null) it.dir.substringAfterLast('/')
                        else it.path.substringAfterLast('/')
                    val type = if (screen.dir == null) MediaType.Directory else MediaType.File

                    // TODO this generates thumbs for every file beforehand!! should be lazy
                    //val bmp = getThumbBitmap(appDirs, it)
                    //MediaWithThumb(it, type, name, bmp)

                    DisplayMedia(it, type, name)
                }
            }
        }.collectAsRetainedState(listOf())

        // UI: app freeze
        //val all by mediaRepository.all().collectAsRetainedState(listOf())

        return MediaScreen.State(
            title,
            medias,
            { appDirs }
        ) { event ->
            when (event) {
                is MediaClicked -> {
                    if (event.media.type == MediaType.Directory) {
                        log.i { "entering dir: ${event.media}" }
                        navigator.goTo(MediaScreen(event.media.media.dir))
                        return@State
                    }
                }

                is Back -> navigator.pop()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@CircuitInject(MediaScreen::class, AppScope::class)
@Composable
fun MediaView(state: MediaScreen.State, modifier: Modifier = Modifier) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.title.uppercase(),
                        //color = Color.Black,
                        fontSize = 24.sp,
                        //fontWeight = FontWeight.Bold,
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
    ) {
        // TODO DI (LocalPlatformContext...)
        val platformContext = LocalPlatformContext.current
        val imageLoader = rememberRetained {
            ImageLoader.Builder(platformContext).components {
                add(Fetcher.Factory<ThumbFetcherCoilModel> { data, options, imageLoader ->
                    ThumbFetcher(
                        data
                    )
                })
            }.build()
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp)
        ) {
            // TODO contentType, in flow insert header?
            items(state.medias, { it.media.id }, contentType = { it.type }) { media ->
                //val bmp = getThumbBitmap(state.getAppDirs(), media)
                /*if (media.thumb == null) {
                    println("got no thumb: $media")
                    return@items
                }*/
                Box(modifier = Modifier.clickable {
                    state.eventSink(MediaClicked(media))
                }) {
                    // loading the thumbnail is slow, don't do it in the flow
                    // lazy load images, don't load all before showing the list
                    val imageRequest = rememberRetained {
                        ImageRequest.Builder(platformContext)
                            .data(ThumbFetcherCoilModel(state.getAppDirs(), media.media))
                            .build()
                    }
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
                        model = imageRequest,
                        imageLoader = imageLoader,
                        contentDescription = null,
                        contentScale = ContentScale.Crop
                    )
                    /*Image(
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
                        bitmap = idk!!,
                        //bitmap = media.thumb,
                        contentScale = ContentScale.Crop,
                        contentDescription = ""
                    )*/
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
