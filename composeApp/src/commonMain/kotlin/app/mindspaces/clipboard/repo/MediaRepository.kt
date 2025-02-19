package app.mindspaces.clipboard.repo

import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.mindspaces.clipboard.api.ApiDataNotification.Target
import app.mindspaces.clipboard.api.ApiErrorResponse
import app.mindspaces.clipboard.api.ApiInstallation
import app.mindspaces.clipboard.api.ApiMedia
import app.mindspaces.clipboard.api.ApiSuccessResponse
import app.mindspaces.clipboard.api.MediaType
import app.mindspaces.clipboard.api.Medias
import app.mindspaces.clipboard.api.toEntity
import app.mindspaces.clipboard.db.DataNotification
import app.mindspaces.clipboard.db.Database
import app.mindspaces.clipboard.db.Media
import app.mindspaces.clipboard.db.MediaReceipt
import app.mindspaces.clipboard.db.MediaRequest
import app.mindspaces.clipboard.db.ThumbState
import ca.gosyer.appdirs.AppDirs
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.prepareGet
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.InputProvider
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.streams.asInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.Clock
import kotlinx.io.IOException
import kotlinx.io.readByteArray
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

@Inject
@SingleIn(AppScope::class)
class MediaRepository(
    private val db: Database,
    private val client: HttpClient,
    private val installationRepository: InstallationRepository,
    private val appDirs: AppDirs
) {
    private val log = Logger.withTag("MediaRepo")

    private val mediaQueries = db.mediaQueries
    private val mediaReceiptQueries = db.mediaReceiptQueries
    private val mediaRequestQueries = db.mediaRequestQueries
    private val dataNotificationQueries = db.dataNotificationQueries

    fun sync() {
        log.d { "sync - querying roots..." }
        val roots = File.listRoots()
        for (root in roots) {
            log.i { "sync - found root: $root" }
        }
    }

    private fun get(id: UUID): Media? {
        return mediaQueries.get(id).executeAsOneOrNull()
    }

    fun query(id: UUID) =
        mediaQueries.get(id).asFlow().mapToOneOrNull(Dispatchers.IO).distinctUntilChanged()

    // conflate / buffer(capacity = 1)
    // NOTE: only apply distinctUntilChanged() to the element (ie. after map), not the flow
    fun pendingThumb() =
        mediaQueries.byThumbState(ThumbState.Pending).asFlow().conflate()
            .mapToOneOrNull(Dispatchers.IO)
            .distinctUntilChanged()

    // on desktop syncThumbnails (if implemented) might wait for ThumbState.Generated
    // but on android, thumbnails are generated in the moment (-> upload Pending or Generated)
    fun unsyncedThumb() =
        mediaQueries.byThumbStates(listOf(ThumbState.Pending, ThumbState.Generated)).asFlow()
            .conflate()
            .mapToOneOrNull(Dispatchers.IO)
            .distinctUntilChanged()

    fun increaseThumbRetry(media: Media) {
        mediaQueries.increaseThumbRetry(media.path, media.cre, media.mod, media.size)
    }

    fun markAsThumbGenerated(media: Media) {
        mediaQueries.markThumb(ThumbState.Generated, media.path, media.cre, media.mod, media.size)
    }

    fun markAsThumbSynced(media: Media) {
        mediaQueries.markThumb(ThumbState.Synced, media.path, media.cre, media.mod, media.size)
        mediaQueries.resetThumbRetryCounter(media.path, media.cre, media.mod, media.size)
    }

    fun markAsThumbGenerationFailed(media: Media) {
        mediaQueries.markThumb(ThumbState.GenFailed, media.path, media.cre, media.mod, media.size)
    }

    fun resetThumbGenerationFailed() {
        mediaQueries.resetThumbGenerationFailed(ThumbState.Pending, ThumbState.GenFailed)
    }

    fun saveLocal(path: String, mediaType: MediaType?, cre: Long?, mod: Long, size: Long): Media {
        // TODO cross platform compat!
        val dir = path.substringBeforeLast('/')
        val media = new(path, dir, cre, mod, size, mediaType)
        // TODO returning
        mediaQueries.insert(media)
        return media
    }

    // TODO accept num
    fun recents(platform: ApiInstallation.Platform): Flow<List<Media>> {
        // android UI creates thumbnails ad-hoc
        if (platform == ApiInstallation.Platform.Android) {
            return mediaQueries.recentsNotFailed(ThumbState.GenFailed).asFlow()
                .mapToList(Dispatchers.IO)
                .distinctUntilChanged()
        }
        // desktop UI saves thumbnail files
        return mediaQueries.recents(ThumbState.Generated, ThumbState.Synced).asFlow()
            .mapToList(Dispatchers.IO)
            .distinctUntilChanged()
    }

    fun all(): Flow<List<Media>> {
        return mediaQueries.list().asFlow().mapToList(Dispatchers.IO)
    }

    fun list(dir: String?, installationId: UUID?): Flow<List<Media>> {
        if (dir == null) {
            return directories()
        }
        println("returning files")
        return files(dir, installationId)
    }

    // installation_id is null for self
    fun files(dir: String, installationId: UUID?): Flow<List<Media>> {
        return mediaQueries.files(dir, installationId).asFlow().mapToList(Dispatchers.IO)
            .distinctUntilChanged()
    }

    // TODO return MediaWithCount
    fun directories(): Flow<List<Media>> {
        return mediaQueries.directories().asFlow().mapToList(Dispatchers.IO).distinctUntilChanged()
    }

    private fun outstandingRequest(): Flow<MediaRequest?> {
        return mediaRequestQueries.latest().asFlow().mapToOneOrNull(Dispatchers.IO)
            .distinctUntilChanged()
    }

    // outstanding media notification
    private fun dataNotifications(): Flow<DataNotification?> {
        return dataNotificationQueries.latest(Target.Media).asFlow().mapToOneOrNull(Dispatchers.IO)
            .distinctUntilChanged()
    }

    private fun deleteRequest(reqId: UUID) {
        mediaRequestQueries.delete(reqId)
    }

    fun allRequests(): Flow<List<MediaRequest>> {
        return mediaRequestQueries.all().asFlow().mapToList(Dispatchers.IO).distinctUntilChanged()
    }

    // local uses null, server just shouldn't return files from this installation
    private suspend fun load(): Boolean {
        try {
            val resp = client.get(Medias())
            if (!resp.status.isSuccess()) return false
            val success = resp.body<ApiSuccessResponse<List<ApiMedia>>>()
            val medias = success.data.map(ApiMedia::toEntity)
            val self = installationRepository.selfAsOne() ?: return false
            db.transaction {
                for (media in medias) {
                    if (media.installation_id == self.id) {
                        log.e { "load - remote should not return self: ${media.id}" }
                        continue
                    }
                    mediaQueries.insert(media)
                    mediaReceiptQueries.insert(MediaReceipt(media.id))
                }
            }
            return true
        } catch (e: Throwable) {
            log.e(e) { "load - unexpected resp: $e" }
            return false
        }
    }

    suspend fun uploadData(
        media: Media,
        isFile: Boolean,
        reader: InputStream,
        size: Long
    ): Boolean {
        // form field names are ignored by the server, only relevant for path
        val partName = if (isFile) "file" else "thumb"
        try {
            // handle closing
            reader.use { handledReader ->
                val parts = formData {
                    append("path", media.path)
                    append("dir", media.dir)
                    media.cre?.let { append("cre", it) }
                    append("mod", media.mod)
                    media.mediaType?.name?.let { append("media-type", it) }
                    append("size", size)
                    // `InputProvider` adds Content-Length for some streams
                    // file variant: `InputProvider(file.length()) { file.inputStream().asInput() }`
                    append(
                        partName,
                        InputProvider(size) { handledReader.asInput() },
                        Headers.build {
                            // TODO form-data; dup in body
                            append(
                                HttpHeaders.ContentDisposition,
                                "form-data; name=\"$partName\"; filename=\"$partName\""
                            )
                        }
                    )
                }

                val resp =
                    client.submitFormWithBinaryData("/api/v1/medias/${media.id}/$partName", parts) {
                        timeout {
                            // effectively prevents HttpRequestTimeoutException for large (even 144MB) uploads
                            requestTimeoutMillis = 5 * 60 * 1000
                        }
                    }
                if (!resp.status.isSuccess()) {
                    log.e { "upload-data(stream,$partName) - failed: $resp" }
                    val err = resp.body<ApiErrorResponse>()
                    log.e { "upload-data(stream,$partName) - errors: $err" }
                    return false
                }
                log.i { "upload-data(stream,$partName) - success: ${media.id}" }
                return true
            }
        } catch (e: Throwable) {
            log.e(e) { "upload-data(stream,$partName) - unexpected resp: $e" }
            return false
        }
    }

    // handles retries, which might happen when upload fails while rt stays active
    suspend fun handleRequests() {
        outstandingRequest().collect { req ->
            if (req == null) {
                log.i { "handle-requests - out of work" }
                return@collect
            }
            while (true) {
                log.i { "handle-requests - req: $req" }
                val media = get(req.media_id)
                if (media == null) {
                    log.e { "handle-requests - unknown media-id: ${req.media_id}, ignoring..." }
                    deleteRequest(req.id)
                    break
                }

                // ignore synced state, if a request was issued, re-upload is fine
                if (media.installation_id != null) {
                    log.e { "handle-requests - received illegal media request ${req.id} for ${req.media_id}, ignoring..." }
                    deleteRequest(req.id)
                    break
                }

                log.i { "handle-requests - initiating upload for media-id: ${req.media_id}..." }
                val file = File(media.path)
                val inStream: InputStream
                try {
                    inStream = file.inputStream()
                } catch (e: Throwable) {
                    log.e { "handle-requests - path not found: ${media.path}, ignoring..." }
                    // TODO maybe inform client, mark media as deleted
                    deleteRequest(req.id)
                    break
                }

                try {
                    // TODO save synced status...
                    if (!uploadData(media, true, inStream, file.length())) {
                        // TODO what if too big for upload? don't loop
                        log.w { "handle-requests - upload failed, assuming temporary..." }
                        // TODO increase retry
                        delay(25.seconds)
                        continue
                    }
                } catch (e: Throwable) {
                    log.e(e) { "handle-requests - upload failed, assuming temporary..." }
                    delay(25.seconds)
                    continue
                }

                log.i { "handle-requests - done, deleting req: $req..." }
                deleteRequest(req.id)
                delay(10.seconds)
                break
            }
        }
    }

    // receive updates from remote
    suspend fun handleMediaUpdates() {
        dataNotifications().collect { notification ->
            if (notification == null) {
                log.i { "handle-media-updates - out of work" }
                return@collect
            }
            var cnt = 0
            while (true) {
                cnt++
                log.i { "handle-media-updates - notification: $notification (cnt: #$cnt)" }
                if (!load()) {
                    log.w { "handle-media-updates - load failed, timeout..." }
                    // TODO exponential
                    delay(20.seconds)
                    continue
                }
                log.d { "handle-media-updates - load successful, deleting notification..." }
                dataNotificationQueries.delete(notification.id)
                break
            }
        }
    }

    // TODO let VideoPlayer handle download using authenticated ktor client (?)
    // progress may never be called
    suspend fun downloadMedia(id: UUID, size: Long, progress: suspend (Int) -> Unit): String {
        // concurrent: save to -<uuid> (crdownload), remove all <uuid> on new request, then check if file exists
        val thumbsDir = File(appDirs.getUserDataDir(), "medias")
        thumbsDir.mkdirs()
        val file = File(thumbsDir, id.toString())
        if (file.exists()) {
            log.i { "download-media($id) - file exists: ${file.path}" }
            if (file.length() == size) return file.path
            log.w { "download-media($id) - size does not match: ${file.length()} != $size, deleting..." }
            if (!file.delete()) throw IOException("file size does not match: ${file.length()} != $size")
        }
        client.prepareGet(Medias.FileRaw(id = id)).execute { httpResponse ->
            val channel: ByteReadChannel = httpResponse.body()
            while (!channel.isClosedForRead) {
                val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                while (!packet.exhausted()) {
                    val bytes = packet.readByteArray()
                    file.appendBytes(bytes)
                    httpResponse.contentLength()?.let {
                        val percentage = file.length().toFloat() / it * 100L
                        // TODO context switch slows down download significantly... need to decouple?
                        progress(percentage.toInt())
                    }
                }
            }
            log.i { "download-media($id) - saved: ${file.path}" }
        }
        return file.path
    }

    suspend fun monitor() {
        mediaQueries.stats().asFlow().mapToList(Dispatchers.IO).distinctUntilChanged()
            .collect { stats ->
                log.i { "monitor - stats: $stats" }
            }
    }

    fun localFile(path: String, cre: Long?, mod: Long, size: Long) =
        mediaQueries.getLocal(path, cre, mod, size).executeAsOneOrNull()

    fun saveRequest(req: MediaRequest) = mediaRequestQueries.insert(req)

    fun saveDataNotification(notification: DataNotification) =
        dataNotificationQueries.insert(notification)

    fun tx(block: TransactionWithoutReturn.() -> Unit) {
        mediaQueries.transaction(body = block)
    }

    // for locally newly discovered files
    private fun new(
        path: String,
        dir: String,
        cre: Long?,
        mod: Long,
        size: Long,
        mediaType: MediaType?
    ) = Media(
        UUID.randomUUID(),
        path,
        dir,
        cre,
        mod,
        size,
        mediaType,
        ThumbState.Pending,
        false,
        0,
        0,
        null,
        Clock.System.now(),
        null
    )
}
