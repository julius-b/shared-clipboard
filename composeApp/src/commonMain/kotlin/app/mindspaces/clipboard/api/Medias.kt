package app.mindspaces.clipboard.api

import app.mindspaces.clipboard.db.Media
import app.mindspaces.clipboard.db.MediaRequest
import app.mindspaces.clipboard.db.ThumbState
import io.ktor.resources.Resource

fun ApiMedia.toEntity() = Media(
    id,
    path,
    dir,
    cre,
    mod,
    size,
    ThumbState.fromRemote(hasThumb),
    hasFile,
    0,
    0,
    mediaType,
    installationId,
    createdAt,
    deletedAt
)

// never receive media requests from this installation from api
fun ApiMediaRequest.toEntity() = MediaRequest(id, false, mediaId, createdAt)

@Resource("/medias")
class Medias {
    @Resource("{id}/thumb")
    class Thumb(val parent: Medias = Medias(), val id: SUUID)

    @Resource("{id}/thumb/raw")
    class ThumbRaw(val parent: Medias = Medias(), val id: SUUID)

    @Resource("{id}/receipts")
    class Receipts(val parent: Medias = Medias(), val id: SUUID)
}
