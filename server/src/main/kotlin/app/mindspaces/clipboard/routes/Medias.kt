package app.mindspaces.clipboard.routes

import app.mindspaces.clipboard.api.ApiError
import app.mindspaces.clipboard.api.ApiSuccessResponse
import app.mindspaces.clipboard.api.KeyInstallationID
import app.mindspaces.clipboard.api.MediaReceiptParams
import app.mindspaces.clipboard.api.MediaType
import app.mindspaces.clipboard.api.SUUID
import app.mindspaces.clipboard.services.mediasService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

const val ThumbMaxSize: Long = 20 * 1024 * 1024

// TODO implement chunk receiver, client (read stream from byte 500 to 1000, eg.)
const val FileMaxSize: Long = 500 * 1024 * 1024

@Serializable
enum class UploadType {
    File, Thumb
}

data class MediaMeta(
    var path: String? = null,
    var dir: String? = null,
    var cre: Long? = null,
    var mod: Long? = null,
    var size: Long? = null,
    var mediaType: MediaType? = null
) {
    fun isComplete() = path != null && dir != null && mod != null && size != null
}

@Serializable
data class MediaLock(
    val id: SUUID,
    val type: UploadType
)

@Serializable
data class ActiveMedia(
    val active: Map<MediaLock, Long>
)

fun Route.mediasApi() {
    val log = KtorSimpleLogger("medias-api")

    val active = ConcurrentHashMap<MediaLock, Long>()

    route("medias") {
        // not REST, but need id before parsing FileItem
        // media can be uploaded without file or thumb (meta only)
        post("{mediaId}/{type}") {
            val mediaId = UUID.fromString(call.parameters["mediaId"])
            val typeParam = call.parameters["type"]
            val installationId = UUID.fromString(call.request.headerOrFail(KeyInstallationID))

            val type = when (typeParam) {
                "file" -> UploadType.File
                "thumb" -> UploadType.Thumb
                else -> throw ValidationException("type", ApiError.Schema())
            }

            File("uploads/media/$type").mkdirs()
            val localPath = "uploads/media/$type/$mediaId"
            val file = File(localPath)

            val lockKey = MediaLock(mediaId, type)
            val lock = active.putIfAbsent(lockKey, System.currentTimeMillis())
            if (lock != null) {
                log.warn("[m:$lockKey] failed to acquire media lock!")
                return@post
            }
            log.info("[m:$lockKey] lock acquired")
            try {
                val contentLength =
                    call.request.header(HttpHeaders.ContentLength)?.toDouble()
                        ?: throw ValidationException(HttpHeaders.ContentLength, ApiError.Required())
                val maxFileSize =
                    if (type == UploadType.Thumb) ThumbMaxSize else FileMaxSize
                if (contentLength > maxFileSize) throw ValidationException(
                    HttpHeaders.ContentLength,
                    ApiError.Constraint("$contentLength", max = maxFileSize)
                )

                // should query current hasFile/Thumb state after acquiring lock
                if (file.exists()) {
                    throw ValidationException("id", ApiError.Conflict())
                }

                val meta = MediaMeta()
                var fileSize: Long? = null

                // default is 50MB: https://youtrack.jetbrains.com/issue/KTOR-7987/ktor-server-receive-MultiPartData-error
                // TODO allow maxFileSize + overhead
                val multipartData =
                    call.receiveMultipart(formFieldLimit = maxFileSize)
                multipartData.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            log.info("receiving form-item ($type/$mediaId) - ${part.name}=${part.value}")
                            when (part.name) {
                                "path" -> meta.path = part.value
                                "dir" -> meta.dir = part.value
                                "media-type" -> meta.mediaType = MediaType.valueOf(part.value)
                                "size" -> {
                                    try {
                                        meta.size = part.value.toLong()
                                    } catch (e: NumberFormatException) {
                                        // TODO test
                                        throw ValidationException(
                                            "size", ApiError.Schema(part.value, schema = "number")
                                        )
                                    }
                                }

                                "cre" -> {
                                    try {
                                        meta.cre = part.value.toLong()
                                    } catch (e: NumberFormatException) {
                                        throw ValidationException(
                                            "cre", ApiError.Schema(part.value, schema = "number")
                                        )
                                    }
                                }

                                "mod" -> {
                                    try {
                                        meta.mod = part.value.toLong()
                                    } catch (e: NumberFormatException) {
                                        throw ValidationException(
                                            "mod", ApiError.Schema(part.value, schema = "number")
                                        )
                                    }
                                }

                                else -> {
                                    log.warn("received unknown media attribute: ${part.name}=${part.value}")
                                }
                            }
                        }

                        is PartData.FileItem -> {
                            val formFieldName = part.name
                            val formFileName = part.originalFileName
                            log.info("receiving file-item - type: $type, mediaId: $mediaId, form(field-name: '$formFieldName', file-name: '$formFileName')")

                            part.provider().toInputStream().use { inStream ->
                                file.outputStream().buffered().use { outStream ->
                                    inStream.copyTo(outStream)
                                    //throw IOException("testing :P")
                                }
                            }
                            fileSize = file.length()
                            log.info("file-item received - type: $type, mediaId: $mediaId, form(field-name: '$formFieldName', file-name: '$formFileName') (fileSize: $fileSize)")

                            /*val fileType = part.contentType?.contentSubtype
                            val ACCEPTED_IMAGE_TYPES = listOf("PNG", "JPG", "JPEG")
                            if (fileType?.uppercase() !in ACCEPTED_IMAGE_TYPES) {
                                call.respond(HttpStatusCode.BadRequest, "Invalid file type")
                            }*/
                        }

                        else -> {}
                    }
                    part.dispose()
                }

                if (meta.path == null) throw ValidationException("path", ApiError.Required())
                if (meta.dir == null) throw ValidationException("dir", ApiError.Required())
                if (meta.size == null || meta.size == 0L)
                    throw ValidationException("size", ApiError.Required())
                if (meta.size != fileSize) throw ValidationException(
                    "size", ApiError.Constraint("$fileSize", equal = "${meta.size}")
                )
                //if (cre == null) throw ValidationException("cre", ErrorStatus.Required())
                if (meta.mod == null) throw ValidationException("mod", ApiError.Required())

                log.info("saving - $type, $mediaId (path: ${meta.path})")
                val media = mediasService.dataAdded(
                    mediaId,
                    meta.path!!,
                    meta.dir!!,
                    meta.cre,
                    meta.mod!!,
                    meta.size!!,
                    meta.mediaType,
                    installationId,
                    isFile = type == UploadType.File
                )

                call.respond(HttpStatusCode.Created, ApiSuccessResponse(data = media))
            } catch (e: Throwable) {
                if (file.delete())
                    log.info("[m:$lockKey] deleted partial file")

                // rethrow
                if (e is ValidationException) throw e
                log.error("[m:$lockKey] receiving failed: $e", e)
                call.respond(HttpStatusCode.InternalServerError)
            } finally {
                val removed = active.remove(lockKey)
                if (removed == null) {
                    log.error("[m:$lockKey] illegal state - mediaId was not locked!")
                } else {
                    log.info("[m:$lockKey] lock cleared")
                }
            }
        }
        authenticate("auth-jwt") {
            // TODO list all with files, include href in response for browser click
            get("{mediaId}/file/raw") {
                val mediaId = UUID.fromString(call.parameters["mediaId"])
                val file = File("uploads/media/${UploadType.File}/$mediaId")
                if (!file.exists()) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                call.respondFile(file)
            }
            get("{mediaId}/thumb/raw") {
                val mediaId = UUID.fromString(call.parameters["mediaId"])
                val file = File("uploads/media/${UploadType.Thumb}/$mediaId")
                if (!file.exists()) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                // TODO get file path from db
                //call.response.header("Content-Disposition", "attachment; filename=\"${file.name}\"")
                call.respondFile(file)
            }
            get {
                val principal = call.principal<JWTPrincipal>()!!
                val selfId = UUID.fromString(principal.payload.getClaim("account_id").asString())
                val selfInstallationLinkId =
                    UUID.fromString(principal.payload.getClaim("link_id").asString())
                // TODO && account.auth != Default
                val all = call.queryParameters["all"] != null

                val accountId = if (all) null else selfId
                val installationLinkId = if (all) null else selfInstallationLinkId
                val medias = mediasService.all(accountId, installationLinkId)
                call.respond(ApiSuccessResponse(medias.size, medias))
            }
            post("{mediaId}/receipts") {
                val mediaId = UUID.fromString(call.parameters["mediaId"])
                val principal = call.principal<JWTPrincipal>()!!
                val selfLinkId =
                    UUID.fromString(principal.payload.getClaim("link_id").asString())
                val req = call.receive<MediaReceiptParams>()

                val receipt = mediasService.saveReceipt(
                    mediaId, selfLinkId, hasThumb = req.hasThumb, hasFile = req.hasFile
                )
                call.respond(ApiSuccessResponse(data = receipt))
            }
        }
        get("active") {
            call.respond(HttpStatusCode.OK, ApiSuccessResponse(data = ActiveMedia(active.toMap())))
        }
    }
    authenticate("auth-jwt") {
        route("media_receipts") {
            get {
                val receipts = mediasService.allReceipts()
                call.respond(ApiSuccessResponse(receipts.size, receipts))
            }
        }
    }
}

fun genRandomAlphaNum(length: Int): String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
    return (1..length)
        .map { allowedChars.random() }
        .joinToString("")
}
