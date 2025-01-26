package app.mindspaces.clipboard.api

import app.mindspaces.clipboard.db.Installation
import app.mindspaces.clipboard.db.InstallationLink
import io.ktor.resources.Resource

fun ApiInstallation.toEntity(self: Boolean) =
    Installation(self, id, name, desc, os, client, createdAt)

fun ApiInstallationLink.toEntity() =
    InstallationLink(id, name, accountId, installationId, createdAt, deletedAt)

@Resource("/installations")
class Installations {
    @Resource("links")
    class Links(val parent: Installations = Installations()) {
        @Resource("{id}/name")
        class UpdateName(val id: SUUID, val parent: Links = Links())
    }
}
