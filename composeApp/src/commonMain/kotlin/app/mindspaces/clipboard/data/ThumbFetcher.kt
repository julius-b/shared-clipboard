package app.mindspaces.clipboard.data

import app.mindspaces.clipboard.api.Host
import app.mindspaces.clipboard.api.Port
import app.mindspaces.clipboard.api.Proto
import app.mindspaces.clipboard.db.Media
import ca.gosyer.appdirs.AppDirs
import co.touchlab.kermit.Logger
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

// TODO trigger request to download from remote?
// TODO "MediaFetcher", model should be UUID/Type
class ThumbFetcher(private val media: Media, private val appDirs: AppDirs) : Fetcher {
    private val log = Logger.withTag("thumb-fetcher")

    override suspend fun fetch(): FetchResult? {
        log.i { "fetching: ${media.path}..." }
        try {
            // withContext(IO) breaks for large video directories, no context switch (and Main) freezes the app -> Default
            val bmp = withContext(Default) {
                withTimeout(10.seconds) {
                    getThumbBitmap(appDirs, media)
                }
            }
            if (bmp == null) log.w { "failed to fetch: ${media.path}" }
            //else log.d { "got thumb: ${model.media.path}" }

            if (bmp == null) {
                log.w { "got null: ${media.path}" }
                return null
            }

            return ImageFetchResult(
                image = bmp,
                isSampled = true,
                // TODO effect of this value?
                dataSource = DataSource.DISK
            )
        } catch (e: Throwable) {
            // eg. LeftCompositionCancellationException
            log.e(e) { "failed to fetch path: ${media.path}" }
            return null
        }
    }
}

// TODO use http-client host...
fun Media.toThumbModel(): Any =
    if (installation_id == null) this else "${Proto.name}://$Host:$Port/api/v1/medias/$id/thumb/raw"

fun Media.toFileModel(): Any =
    if (installation_id == null) this else "${Proto.name}://$Host:$Port/api/v1/medias/$id/file/raw"
