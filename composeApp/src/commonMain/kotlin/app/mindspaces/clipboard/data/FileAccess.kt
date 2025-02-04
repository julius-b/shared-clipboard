package app.mindspaces.clipboard.data

import app.mindspaces.clipboard.repo.MediaRepository
import ca.gosyer.appdirs.AppDirs
import co.touchlab.kermit.Logger
import coil3.BitmapImage
import kotlinx.coroutines.delay
import net.coobird.thumbnailator.Thumbnails
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

fun traverseFiles(dir: File, mediaRepository: MediaRepository, appDirs: AppDirs) {
    val log = Logger.withTag("traverseFiles")

    if (!dir.exists() || !dir.isDirectory) return

    dir.listFiles()?.forEach { file ->
        if (file.isDirectory) {
            traverseFiles(file, mediaRepository, appDirs)
        } else {
            val path = file.absolutePath
            log.i { "file found: ${file.absolutePath}" }

            var cre: Long? = null
            try {
                //val attr = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
                //val fileTime = attr.creationTime()
                cre = (Files.getAttribute(file.toPath(), "creationTime") as FileTime).toMillis()
            } catch (e: Throwable) {
                log.w(e) { "failed to query file creation time: ${file.path}" }
            }
            val mod = file.lastModified()
            val size = file.length()

            mediaRepository.tx {
                val curr = mediaRepository.localFile(path, cre, mod, size)
                if (curr != null) {
                    log.i { "nop: $path (mod: $mod)" }
                    return@tx
                }

                val saved = mediaRepository.saveLocal(path, null, cre, mod, size)
                log.i { "save - new: $path (mod: $mod, dir: $saved.dir)" }
            }
        }
    }
}

suspend fun generateThumbnails(mediaRepository: MediaRepository, appDirs: AppDirs) {
    val log = Logger.withTag("generateThumbnails")

    mediaRepository.pendingThumb().collect { file ->
        if (file == null) {
            log.w { "out of work" }
            return@collect
        }

        try {
            val thumbsDir = File(appDirs.getUserDataDir(), "thumbs")
            thumbsDir.mkdirs()
            val thumb = File(thumbsDir, file.id.toString())

            log.i { "generating thumb: ${file.path}..." }
            // library always adds extension
            Thumbnails
                .of(file.path)
                .size(640, 640)
                .outputFormat("jpg")
                .toFile(thumb)

            log.i { "marking: ${file.path}..." }
            mediaRepository.markAsThumbGenerated(file)
            log.i { "marked: ${file.path}" }
            delay(100.milliseconds)
        } catch (e: Throwable) {
            // TODO test if file exists, mark as gone otherwise
            log.w(e) { "failed to generate thumbnail: ${file.path}" }
            mediaRepository.markAsThumbGenerationFailed(file)
        }
    }
}

// TODO cross platform
fun String.fileName() = this.substringAfterLast('/')

expect fun getThumbPath(appDirs: AppDirs, mediaId: UUID): File

expect suspend fun getThumbBitmap(appDirs: AppDirs, media: MediaFetcherModel): BitmapImage?

expect suspend fun getFileBitmap(media: MediaFetcherModel): BitmapImage?
