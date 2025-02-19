package app.mindspaces.clipboard.data

import androidx.compose.ui.platform.UriHandler
import ca.gosyer.appdirs.AppDirs
import coil3.BitmapImage
import coil3.asImage
import io.ktor.http.Url
import io.ktor.http.toURI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject
import org.jetbrains.skiko.toBitmap
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.awt.image.BufferedImage
import java.io.File
import java.util.UUID
import javax.imageio.ImageIO

// desktop thumbnail is always jpg
actual fun getThumbPath(appDirs: AppDirs, mediaId: UUID): File {
    val thumbsDir = File(appDirs.getUserDataDir(), "thumbs")
    val thumb = File(thumbsDir, "${mediaId}.jpg")
    return thumb
}

actual suspend fun getThumbBitmap(appDirs: AppDirs, media: MediaFetcherModel): BitmapImage? {
    try {
        val bufferedImage: BufferedImage =
            withContext(Dispatchers.IO) {
                ImageIO.read(getThumbPath(appDirs, media.id))
            } ?: return null
        //return bufferedImage.toComposeImageBitmap()
        return bufferedImage.toBitmap().asImage()
    } catch (e: Throwable) {
        println("failed to load thumb-bitmap (${media.path}): $e")
        return null
    }
}

actual suspend fun getFileBitmap(media: MediaFetcherModel): BitmapImage? {
    try {
        val bufferedImage: BufferedImage = withContext(Dispatchers.IO) {
            ImageIO.read(File(media.path))
        }
        return bufferedImage.toBitmap().asImage()
    } catch (e: Throwable) {
        println("failed to load file-bitmap (${media.path}): $e")
        return null
    }
}

@SingleIn(AppScope::class)
@Inject
actual class PlatformIO {
    actual fun shareFile(uriHandler: UriHandler, path: String) {
        val uri = Url(File(path).toURI()).toURI().toString()
        uriHandler.openUri(uri)
    }
}
