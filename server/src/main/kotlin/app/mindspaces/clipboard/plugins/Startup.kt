package app.mindspaces.clipboard.plugins

import app.mindspaces.clipboard.routes.UploadType
import app.mindspaces.clipboard.services.mediasService
import io.ktor.server.application.Application
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID

fun Application.configureStartup() {
    val log = KtorSimpleLogger("startup")

    runBlocking {
        // ensure no partial is lying around
        fun handleMedia(type: UploadType, id: UUID) {
            val file = File("uploads/media/$type/$id")
            if (file.exists()) {
                log.info("found unexpected $type, deleting: $file")
                if (file.delete()) log.info("deleted $type")
                else log.error("failed to delete $type: $file")
            }
        }

        for (media in mediasService.noThumbOrNoFile()) {
            if (!media.hasThumb) handleMedia(UploadType.Thumb, media.id)
            if (!media.hasFile) handleMedia(UploadType.File, media.id)
        }
    }
}
