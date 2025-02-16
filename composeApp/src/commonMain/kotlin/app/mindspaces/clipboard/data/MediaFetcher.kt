package app.mindspaces.clipboard.data

import app.mindspaces.clipboard.api.Host
import app.mindspaces.clipboard.api.MediaType
import app.mindspaces.clipboard.api.Port
import app.mindspaces.clipboard.api.Proto
import app.mindspaces.clipboard.db.Media
import ca.gosyer.appdirs.AppDirs
import co.touchlab.kermit.Logger
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

data class MediaFetcherModel(
    val id: UUID,
    val path: String,
    val type: MediaType?,
    val isFile: Boolean
)

// handles local media (image) requests
// TODO trigger request to download from remote?
class MediaFetcher(private val model: MediaFetcherModel, private val appDirs: AppDirs) : Fetcher {
    private val log = Logger.withTag("media-fetcher")

    override suspend fun fetch(): FetchResult? {
        log.i { "fetching (isFile: ${model.isFile}): ${model.path}..." }
        try {
            // withContext(IO) breaks for large video directories, no context switch (and Main) freezes the app -> Default
            val bmp = if (model.isFile) getFileBitmap(model)
            else withContext(Default) {
                withTimeout(10.seconds) {
                    getThumbBitmap(appDirs, model)
                }
            }
            if (bmp == null) log.w { "failed to fetch (isFile: ${model.isFile}): ${model.path}" }
            //else log.d { "got thumb: ${model.media.path}" }

            if (bmp == null) {
                log.w { "got null (isFile: ${model.isFile}): ${model.path}" }
                return null
            }

            return ImageFetchResult(
                image = bmp,
                // TODO effect of this value?
                isSampled = !model.isFile,
                // TODO effect of this value?
                dataSource = DataSource.DISK
            )
        } catch (e: Throwable) {
            // eg. LeftCompositionCancellationException
            log.e(e) { "failed to fetch path (isFile: ${model.isFile}): ${model.path}" }
            return null
        }
    }
}

class MediaFetcherModelKeyer : Keyer<MediaFetcherModel> {
    // MediaFetcherModel is only used for local requests, id is enough
    override fun key(data: MediaFetcherModel, options: Options): String {
        return "${data::class.simpleName}_${data.id}"
    }
}

// TODO use http-client host...
fun Media.toThumbModel(): Any =
    if (installation_id == null) MediaFetcherModel(id, path, mediaType, false)
    else "${Proto.name}://$Host:$Port/api/v1/medias/$id/thumb/raw"

// TODO handle 404 on url
// TODO handle possible missing path (outdated local cache) from fetcher in detail view
fun Media.toFileModel(): Any =
    if (installation_id == null) MediaFetcherModel(id, path, mediaType, true)
    else "${Proto.name}://$Host:$Port/api/v1/medias/$id/file/raw"
