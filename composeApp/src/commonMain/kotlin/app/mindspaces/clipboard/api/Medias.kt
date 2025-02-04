package app.mindspaces.clipboard.api

import app.mindspaces.clipboard.db.DataNotification
import app.mindspaces.clipboard.db.Media
import app.mindspaces.clipboard.db.MediaRequest
import app.mindspaces.clipboard.db.ThumbState
import io.ktor.resources.Resource
import java.util.UUID

fun ApiMedia.toEntity() = Media(
    id,
    path,
    dir,
    cre,
    mod,
    size,
    mediaType,
    ThumbState.fromRemote(hasThumb),
    hasFile,
    0,
    0,
    installationId,
    createdAt,
    deletedAt
)

// never receive media requests from this installation from api
fun ApiMediaRequest.toEntity() = MediaRequest(id, false, mediaId, createdAt)

fun ApiDataNotification.toEntity(id: UUID) = DataNotification(id, target)

@Resource("/medias")
class Medias {
    @Resource("{id}/thumb")
    class Thumb(val parent: Medias = Medias(), val id: SUUID)

    @Resource("{id}/thumb/raw")
    class ThumbRaw(val parent: Medias = Medias(), val id: SUUID)

    @Resource("{id}/file/raw")
    class FileRaw(val parent: Medias = Medias(), val id: SUUID)

    @Resource("{id}/receipts")
    class Receipts(val parent: Medias = Medias(), val id: SUUID)
}
