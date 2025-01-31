package app.mindspaces.clipboard.data

import app.mindspaces.clipboard.db.Media
import ca.gosyer.appdirs.AppDirs
import coil3.BitmapImage
import coil3.asImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skiko.toBitmap
import java.awt.image.BufferedImage
import java.io.File
import java.util.UUID
import javax.imageio.ImageIO

actual fun getThumbPath(appDirs: AppDirs, mediaId: UUID): File {
    val thumbsDir = File(appDirs.getUserDataDir(), "thumbs")
    val thumb = File(thumbsDir, "${mediaId}.jpg")
    return thumb
}

actual suspend fun getThumbBitmap(appDirs: AppDirs, media: Media): BitmapImage? {
    try {
        val bufferedImage: BufferedImage =
            withContext(Dispatchers.IO) {
                ImageIO.read(getThumbPath(appDirs, media.id))
            } ?: return null
        //return bufferedImage.toComposeImageBitmap()
        return bufferedImage.toBitmap().asImage()
    } catch (e: Throwable) {
        println("failed to load bitmap (${media.path}): $e")
        return null
    }
}
