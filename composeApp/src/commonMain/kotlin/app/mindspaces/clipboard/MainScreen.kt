@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.mindspaces.clipboard

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mindspaces.clipboard.MainScreen.Event.AddAccount
import app.mindspaces.clipboard.MainScreen.Event.CreateAccount
import app.mindspaces.clipboard.MainScreen.Event.CreateNote
import app.mindspaces.clipboard.MainScreen.Event.RequestStoragePermission
import app.mindspaces.clipboard.MainScreen.Event.ToggleAddNote
import app.mindspaces.clipboard.MainScreen.Event.ViewAllMedia
import app.mindspaces.clipboard.data.Permission
import app.mindspaces.clipboard.data.PermissionState
import app.mindspaces.clipboard.data.ThumbFetcher
import app.mindspaces.clipboard.data.ThumbFetcherCoilModel
import app.mindspaces.clipboard.data.isGranted
import app.mindspaces.clipboard.data.rememberPermissionState
import app.mindspaces.clipboard.db.Account
import app.mindspaces.clipboard.db.Clip
import app.mindspaces.clipboard.db.InstallationLink
import app.mindspaces.clipboard.db.Media
import app.mindspaces.clipboard.db.Note
import app.mindspaces.clipboard.parcel.CommonParcelize
import app.mindspaces.clipboard.repo.AuthRepository
import app.mindspaces.clipboard.repo.InstallationRepository
import app.mindspaces.clipboard.repo.MediaRepository
import app.mindspaces.clipboard.repo.NoteRepository
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
import com.slack.circuit.runtime.internal.rememberStableCoroutineScope
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import org.jetbrains.compose.resources.Font
import sharedclipboard.composeapp.generated.resources.Anton_Regular
import sharedclipboard.composeapp.generated.resources.Res
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import java.util.UUID

const val minSecretSize = 8

@CommonParcelize
data object MainScreen : Screen {
    data class State(
        val self: Account?,
        val notes: List<Note>,
        val devices: List<InstallationLink>,
        val recents: List<Media>,
        val storagePermission: PermissionState,
        val addNote: AddNoteModal?,
        val getAppDirs: () -> AppDirs,
        val eventSink: (Event) -> Unit
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data object ToggleAddNote : Event
        data class CreateNote(val text: String) : Event
        data object AddAccount : Event
        data class CreateAccount(val name: String, val email: String, val secret: String) : Event
        data object RequestStoragePermission : Event
        data object ViewAllMedia : Event
    }
}

data class AddNoteModal(
    val id: UUID? = null,
    val initial: String
) {
    companion object {
        fun fromClip(clip: Clip) = AddNoteModal(clip.id, clip.text)
    }
}

@CircuitInject(MainScreen::class, AppScope::class)
@Inject
class MainPresenter(
    @Assisted private val navigator: Navigator,
    private val authRepository: AuthRepository,
    private val installationRepository: InstallationRepository,
    private val noteRepository: NoteRepository,
    private val mediaRepository: MediaRepository,
    private val appDirs: AppDirs
) : Presenter<MainScreen.State> {
    private val log = Logger.withTag("MainScreen")

    @Composable
    override fun present(): MainScreen.State {
        val scope = rememberStableCoroutineScope()

        var addNote by rememberRetained { mutableStateOf<AddNoteModal?>(null) }

        val self by authRepository.self().collectAsRetainedState(null)
        val devices by installationRepository.allLinks().collectAsRetainedState(listOf())
        val notes by noteRepository.list().collectAsRetainedState(listOf())
        val recents by mediaRepository.recents(getPlatform()).collectAsRetainedState(listOf())

        val storagePermission = rememberPermissionState(Permission.Storage) {
            log.i { "storage-permission-cb: $it" }
        }

        // TODO
        // val addNote by noteRepository.latestClip().map { it?.let { AddNoteModal.fromClip(it) } }
        //            .collectAsRetainedState(null)
        LaunchedEffect(Unit) {
            scope.launch {
                noteRepository.latestClip().collect { clip ->
                    if (clip == null) return@collect
                    log.i { "got share intent: $clip" }
                    addNote = AddNoteModal.fromClip(clip)
                }
            }
        }

        return MainScreen.State(
            self,
            notes,
            devices,
            recents,
            storagePermission,
            addNote,
            { appDirs }
        ) { event ->
            when (event) {
                is ToggleAddNote -> {
                    if (addNote == null) addNote = AddNoteModal(initial = "")
                    else {
                        addNote!!.id?.let { noteRepository.deleteClip(it) }
                        addNote = null
                    }
                }

                is CreateNote -> {
                    noteRepository.clipToNote(event.text, addNote!!.id)
                    addNote = null
                }

                is AddAccount -> {
                    navigator.goTo(AuthScreen)
                }

                is CreateAccount -> {

                }

                is RequestStoragePermission -> {
                    // TODO can probably wait
                    //val ok = requestStoragePermission.tryEmit(Unit)
                    //log.i { "RequestStoragePermission - ok: $ok" }
                    storagePermission.launchPermissionRequest()
                }

                is ViewAllMedia -> {
                    navigator.goTo(MediaScreen)
                }
            }
        }
    }
}

@CircuitInject(MainScreen::class, AppScope::class)
@Composable
fun MainView(state: MainScreen.State, modifier: Modifier = Modifier) {
    AddNoteOverlay(state)

    //if (state.shared != null) {
    //    AddNoteOverlay(state, initial = state.shared)
    //}

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.padding(top = 18.dp, start = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Access Anywhere".uppercase(),
                    //color = Color.Black,
                    fontSize = 24.sp,
                    //fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily(Font(Res.font.Anton_Regular))
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                state.eventSink(ToggleAddNote)
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { innerPadding ->
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
        Column(
            modifier = modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.width(960.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LazyColumn(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    // fab covers right side (action buttons)
                    contentPadding = PaddingValues(bottom = (56 + 8).dp)
                    // modifier = Modifier.fillMaxSize().padding(20.dp)
                ) {
                    item {
                        when {
                            !state.storagePermission.status.isGranted -> {
                                InfoCard {
                                    // TODO maybe image
                                    Text(
                                        "Storage Permission Required",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text("In order to access your files across all your devices, please grant storage access.")
                                    Spacer(Modifier.height(8.dp))
                                    TextButton(
                                        onClick = {
                                            state.eventSink(RequestStoragePermission)
                                        },
                                        shape = ShapeDefaults.Medium,
                                        modifier = Modifier.align(Alignment.End)
                                    ) { Text("Grant Access") }
                                }
                            }

                            state.self == null -> {
                                InfoCard {
                                    Text(
                                        "No Account",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text("Create an account to share your clipboard entries with other devices")
                                    Spacer(Modifier.height(8.dp))
                                    TextButton(
                                        onClick = {
                                            state.eventSink(AddAccount)
                                        },
                                        shape = ShapeDefaults.Medium,
                                        modifier = Modifier.align(Alignment.End)
                                    ) { Text("Create Account") }
                                }
                            }

                            state.devices.size < 2 -> {
                                InfoCard {
                                    Text(
                                        "No devices connected",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(2.dp))
                                }
                            }

                            else -> {
                                Text("TODO check if no devices added yet")
                            }
                        }
                    }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {}
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(
                                    "Recent Media",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))
                                // exactly 2 rows, not a grid
                                /*for (i in 0..1) {
                                    Row(
                                        modifier = Modifier.height(64.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        for (k in 0..4) {
                                            // TODO accepts Bitmap
                                            Image(
                                                painter = painterResource(resource = Res.drawable.compose_multiplatform),
                                                contentDescription = ""
                                            )
                                        }
                                    }
                                }
                                LazyHorizontalGrid(
                                    modifier = Modifier.height(240.dp).fillMaxWidth(),
                                    rows = GridCells.Fixed(2),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(8) {
                                        Image(
                                            painter = painterResource(resource = Res.drawable.compose_multiplatform),
                                            contentDescription = ""
                                        )
                                    }
                                }*/
                                // 8x2 max elements required from flow
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 2,
                                    maxItemsInEachRow = 8,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    println("state.recents: (${state.recents.size}) ${state.recents}")
                                    for (media in state.recents) {
                                        // doesn't apply to android
                                        //if (!node.thumbState.hasLocalThumb()) continue

                                        //if (getPlatform() == ApiInstallation.Platform.Android) { }
                                        /*val thumb = getThumbPath(state.getAppDirs(), node.id)
                                        println("thumb: $thumb (${node.path}, state: ${node.thumbState})")
                                        // caches path, only provide path once it exists
                                        AsyncImage(
                                            modifier = Modifier.size(64.dp)
                                                //.sizeIn(48.dp, 48.dp, 150.dp, 150.dp)
                                                .border(4.dp, Color.LightGray, RoundedCornerShape(8.dp))
                                                .clip(RoundedCornerShape(8.dp)),
                                            model = thumb,
                                            contentScale = ContentScale.Crop,
                                            contentDescription = ""
                                        )*/
                                        // hangs on videos
                                        /*val bmp = getThumbBitmap(state.getAppDirs(), media)
                                        if (bmp == null) {
                                            println("got no thumb: $media")
                                            continue
                                        }
                                        Image(
                                            modifier = Modifier.size(64.dp)
                                                .border(4.dp, Color.LightGray, RoundedCornerShape(8.dp))
                                                .clip(RoundedCornerShape(8.dp)),
                                            //bitmap = bmp, TODO get this back
                                            painter = bmp.asPainter(LocalPlatformContext.current),
                                            contentScale = ContentScale.Crop,
                                            contentDescription = ""
                                        )*/

                                        val imageRequest = rememberRetained {
                                            ImageRequest.Builder(platformContext).data(
                                                ThumbFetcherCoilModel(state.getAppDirs(), media)
                                            ).build()
                                        }

                                        AsyncImage(
                                            modifier = Modifier.size(64.dp)
                                                .border(
                                                    4.dp,
                                                    Color.LightGray,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clip(RoundedCornerShape(8.dp)),
                                            model = imageRequest,
                                            imageLoader = imageLoader,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                                //HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 4.dp)
                                TextButton(
                                    onClick = {
                                        state.eventSink(ViewAllMedia)
                                    },
                                    shape = ShapeDefaults.Medium,
                                    modifier = Modifier.align(Alignment.End)
                                ) { Text("View All") }
                            }
                        }
                    }
                    // TODO move somewhere else idk
                    items(state.notes) { note ->
                        NoteItem(note)
                    }
                }
            }
        }
    }
}

@Composable
fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            content()
        }
    }
}

@Composable
fun NoteItem(note: Note) {
    val dateFormat = LocalDateTime.Format {
        monthNumber(padding = Padding.SPACE)
        char('-')
        dayOfMonth()
        char('-')
        year()
    }

    //val c1 = Color(0xFF5C00FF)
    val c1 = Color(0x4c5a99ff)
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            //containerColor = c1//.copy(alpha = 0.2f),
            //containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            //verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Favorite, contentDescription = "")
            Column(modifier = Modifier.fillMaxWidth().padding(start = 8.dp)) {
                Text(
                    note.text,
                    //color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    // TODO link to all notes on that date
                    SuggestionChip(
                        onClick = {},
                        label = {
                            val localDateTime =
                                note.created_at.toLocalDateTime(TimeZone.currentSystemDefault())
                            Text(localDateTime.format(dateFormat))
                        }
                    )
                    Spacer(Modifier.width(6.dp))
                    // TODO enable filter (on this screen) for only by this device
                    SuggestionChip(
                        onClick = { },
                        // TODO remove black border
                        enabled = true,
                        label = {
                            if (note.installation_id == null)
                                Text("This device")
                            else Text("(device name)")
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            //containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                        //modifier = Modifier.align(Alignment.End)
                    )
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = {}
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddNoteOverlay(
    state: MainScreen.State,
    modifier: Modifier = Modifier
) {
    if (state.addNote == null) return
    BasicAlertDialog(onDismissRequest = {
        state.eventSink(ToggleAddNote)
    }) {
        Surface(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Create Note",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                key(state.addNote) {
                    // (key1 = state.addNote)
                    var text by remember { mutableStateOf(state.addNote.initial) }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Text") }
                    )

                    Spacer(Modifier.width(24.dp))

                    Row(Modifier.align(Alignment.End)) {
                        TextButton(onClick = {
                            state.eventSink(ToggleAddNote)
                        }) {
                            Text("Cancel")
                        }
                        Spacer(Modifier.width(16.dp))
                        TextButton(
                            enabled = text.isNotBlank(),
                            onClick = {
                                state.eventSink(CreateNote(text))
                                //onDismiss(AddNoteResult.Data(text))
                            },
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}
