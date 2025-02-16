package app.mindspaces.clipboard.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.webkit.MimeTypeMap
import androidx.compose.ui.platform.UriHandler
import androidx.core.content.FileProvider
import app.mindspaces.clipboard.api.MediaType
import app.mindspaces.clipboard.di.AppContext
import app.mindspaces.clipboard.repo.MediaRepository
import ca.gosyer.appdirs.AppDirs
import co.touchlab.kermit.Logger
import coil3.BitmapImage
import coil3.asImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds


// on android don't save thumbnails locally, always queries data-store
// TODO consider saving media-store-id in Media
suspend fun syncThumbnails(
    mediaRepository: MediaRepository,
    contentResolver: ContentResolver,
    appDirs: AppDirs
) {
    val log = Logger.withTag("syncThumbnails")

    // one file at a time, ensures it's a current state
    mediaRepository.unsyncedThumb().collect { media ->
        // TODO probably need a loop any only return when null or markAsThumbSynced, otherwise no retry
        if (media == null) {
            log.w { "out of work" }
            return@collect
        }
        log.i { "pending: $media" }

        try {
            if (media.mediaType == null) {
                log.e { "expected media type to be available: $media" }
                mediaRepository.markAsThumbGenerationFailed(media)
                return@collect
            }

            // NOTE: since we're not duplicating the android media-store cache in appdata,
            //       we're constantly re-"generating" thumbnails, but Android should cache
            val bitmap: Bitmap? = getThumbBitmap(media.mediaType, media.path)
            if (bitmap == null) {
                // TODO just throw
                log.e { "generation failed: $media" }
                mediaRepository.markAsThumbGenerationFailed(media)
                return@collect
            }

            val bos = ByteArrayOutputStream()
            // bad compression
            //bitmap.compress(Bitmap.CompressFormat.PNG, 0 /*ignored for PNG*/, bos);
            // quality=0 truly bad
            bitmap.compress(Bitmap.CompressFormat.JPEG, 40, bos);
            val bitmapData = bos.toByteArray()
            bitmap.recycle()
            val inputStream = ByteArrayInputStream(bitmapData)

            if (!mediaRepository.uploadData(media, false, inputStream, bitmapData.size.toLong())) {
                log.i { "failed to upload thumb, standby..." }
                delay(25.seconds)
                // update retry counter on file to trigger collect
                mediaRepository.increaseThumbRetry(media)
                return@collect
            }

            mediaRepository.markAsThumbSynced(media)

            /*val thumbsDir = File(appDirs.getUserDataDir(), "thumbs")
            thumbsDir.mkdirs()
            val thumb = File(thumbsDir, file.id.toString())
            saveBitmapToFile(bitmap, thumb, Bitmap.CompressFormat.JPEG, 100)

            mediaRepository.markAsThumbGenerated(file)
            log.i { "marked as generated: $file" }*/
            delay(100.milliseconds)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            // TODO ensure this is never called by uploadThumb (should by fine)
            // TODO / NOTE: worker cancellation can cancel the thumb generation
            //              "generation failed: JobCancellationException"
            //              -> `is CancellationException`, alt: only update retry counter
            log.e(e) { "generation failed: $media - increasing retry counter without setting a bad mark" }
            mediaRepository.markAsThumbGenerationFailed(media)
        }
    }
}

// throws IOException
fun getThumbBitmap(
    mediaType: MediaType,
    path: String,
    signal: CancellationSignal? = null
): Bitmap? {
    // ThumbnailUtils -> contentResolver.loadThumbnail()
    return if (mediaType == MediaType.Image) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ThumbnailUtils.createImageThumbnail(File(path), Size(512, 384), signal)
        else ThumbnailUtils.createImageThumbnail(path, MediaStore.Images.Thumbnails.MINI_KIND)
    } else {
        //println("video thumbnail... $path")
        val thumb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ThumbnailUtils.createVideoThumbnail(File(path), Size(512, 384), signal)
        else ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND)
        //println("got video thumbnail $path ($thumb)")
        thumb
    }
}

actual suspend fun getThumbBitmap(appDirs: AppDirs, media: MediaFetcherModel): BitmapImage? =
    suspendCancellableCoroutine { cont ->
        val signal = CancellationSignal()
        cont.invokeOnCancellation {
            println("I: scc done, cancelling signal")
            signal.cancel()
        }
        signal.setOnCancelListener {
            println("W: we got cancelled :p (path: ${media.path})")
        }
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            delay(10.seconds)
            println("timeout reached, cancelling signal...")
            signal.cancel()
        }

        try {
            if (media.type == null) return@suspendCancellableCoroutine
            val bitmap =
                getThumbBitmap(media.type, media.path) ?: return@suspendCancellableCoroutine
            //println("got thumb ${media.mediaType}, ${media.path}")
            //return bitmap.asImageBitmap()
            cont.resumeWith(Result.success(bitmap.asImage()))
        } catch (e: Throwable) {
            println("failed to load bitmap (${media.path}): $e")
        } finally {
            scope.cancel()
        }
    }

actual suspend fun getFileBitmap(media: MediaFetcherModel): BitmapImage? {
    try {
        val bmOptions = BitmapFactory.Options()
        val bitmap = BitmapFactory.decodeFile(media.path, bmOptions)
        return bitmap.asImage()
    } catch (e: Throwable) {
        println("failed to load file-bitmap (${media.path}): $e")
        return null
    }
}

fun listAllMedia(mediaRepository: MediaRepository, contentResolver: ContentResolver) {
    // TODO DATE_TAKEN
    // RELATIVE_PATH: eg. 'DCIM/Camera/', 'Pictures/Screenshots/'
    val imageProjection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.TITLE,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.DATA,
        //MediaStore.Images.Media.RELATIVE_PATH
    )
    val imageUri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.getContentUri(
            MediaStore.VOLUME_EXTERNAL
        )
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    println("mediastore - querying images...")
    queryMedia(mediaRepository, contentResolver, imageUri, imageProjection, MediaType.Image)

    val videoProjection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.TITLE,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DATE_ADDED,
        MediaStore.Video.Media.DATE_MODIFIED,
        MediaStore.Video.Media.DATA,
        //MediaStore.Video.Media.RELATIVE_PATH,
        MediaStore.Video.Media.DURATION
    )
    val videoUri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Video.Media.getContentUri(
            MediaStore.VOLUME_EXTERNAL
        )
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    println("mediastore - querying videos...")
    queryMedia(mediaRepository, contentResolver, videoUri, videoProjection, MediaType.Video)

    // TODO run in parallel?
    // TODO Audio
    // TODO Downloads
}

// TODO.... use one projection it's the same keys anyway
private fun queryMedia(
    mediaRepository: MediaRepository,
    contentResolver: ContentResolver,
    uri: Uri,
    projection: Array<String>,
    mediaType: MediaType
) {
    val log = Logger.withTag("queryMedia")

    // NOTE: can throw SecurityException: Permission Denial
    val cursor = contentResolver.query(
        uri,
        projection,
        null,
        null,
        "${MediaStore.MediaColumns.DATE_ADDED} DESC"
    )

    if (cursor == null) {
        log.e { "failed to acquire cursor" }
        return
    }

    val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
    val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
    val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.TITLE)
    val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
    val creIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
    val modIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
    val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
    //val relativePathIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
    val durationIndex =
        if (mediaType == MediaType.Video) cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION) else -1

    while (cursor.moveToNext()) {
        val id = cursor.getLong(idIndex)
        val size = cursor.getLong(sizeIndex)
        val title = cursor.getString(titleIndex)
        val name = cursor.getString(nameIndex)
        val cre = cursor.getLong(creIndex)
        val mod = cursor.getLong(modIndex)
        val path = cursor.getString(dataIndex)

        val duration = if (mediaType == MediaType.Video) cursor.getLong(durationIndex) else null
        val formattedDuration =
            if (mediaType == MediaType.Video) formatDuration(duration!!) else "N/A"

        //log.i { "ID: $id, Name: $name, cre: $cre, mod: $mod, Duration: ${duration}ms ($formattedDuration), size: $size, title: $title, Path: $path" }

        // Starting with Android Q (API 29), direct file paths (DATA) are no longer accessible for most apps. (not this app).
        // Use ContentUris.withAppendedId(uri, id) to construct the file's Uri.
        // eg: content://media/external/video/media/1000023722
        //val contentUri = ContentUris.withAppendedId(uri, id)
        //println("Content URI: $contentUri")

        if (path.contains("/node_modules/")) {
            continue
        }

        // basically just insert or ignore, not on primary key (new uuid) but instead path/cre/mod
        mediaRepository.tx {
            val curr = mediaRepository.localFile(path, cre, mod, size)
            if (curr != null) {
                //log.i { "nop: $path (mod: $mod)" }
                return@tx
            }
            val saved = mediaRepository.saveLocal(path, mediaType, cre, mod, size)
            //log.i { "save($mediaType) - new: $path (mod: $mod, dir: ${saved.dir})" }
        }
    }

    // TODO query?.use { cursor ->
    cursor.close()
}

// TODO try with resources
fun saveBitmapToFile(
    bitmap: Bitmap,
    file: File,
    format: Bitmap.CompressFormat,
    quality: Int
): Boolean {
    var fileOutputStream: FileOutputStream? = null
    try {
        fileOutputStream = FileOutputStream(file)
        bitmap.compress(format, quality, fileOutputStream)
        return true
    } catch (e: IOException) {
        e.printStackTrace()
        return false
    } finally {
        try {
            fileOutputStream?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}

fun Cursor.getColumnIndexOrNull(columnName: String): Int? {
    val idx = getColumnIndex(columnName)
    return if (idx == -1) null else idx
}

fun formatDuration(durationMillis: Long): String {
    val seconds = (durationMillis / 1000) % 60
    val minutes = (durationMillis / (1000 * 60)) % 60
    val hours = (durationMillis / (1000 * 60 * 60))
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

actual fun getThumbPath(appDirs: AppDirs, mediaId: UUID): File {
    val thumbsDir = File(appDirs.getUserDataDir(), "thumbs")
    val thumb = File(thumbsDir, mediaId.toString())
    return thumb
}

@SingleIn(AppScope::class)
@Inject
actual class PlatformIO(
    @AppContext val context: Context
) {
    actual fun shareFile(uriHandler: UriHandler, path: String) {
        val file = File(path)
        val fileShareUri = FileProvider.getUriForFile(
            context, context.applicationContext.packageName + ".provider", file
        )

        val mime = MimeTypeMap.getSingleton()
        val ext = file.extension
        val type = mime.getMimeTypeFromExtension(ext)
        if (type == null) {
            Log.e("PlatformIO", "failed to get mime type: $file ($ext)")
            return
        }

        //fileShareUri.path?.let { uriHandler.openUri(it) }
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(fileShareUri, "text/*")
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(intent)
    }
}

data class Folder(
    val uri: String,
    val id: Long,
    val name: String,
    val displayName: String,
    val size: String,
    val duration: String,
    val path: String,
    val dateAdded: String
)

data class Video(
    val uri: String,
    val name: String,
    val duration: String,
    val id: Long,
    val path: String,
    val size: String,
    val dateAdded: String
)
