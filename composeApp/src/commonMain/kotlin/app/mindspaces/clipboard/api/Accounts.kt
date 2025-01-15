package app.mindspaces.clipboard.api

import app.mindspaces.clipboard.db.Account
import app.mindspaces.clipboard.db.AccountProperty
import io.ktor.resources.Resource

fun ApiAccount.toEntity() =
    Account(id, type, auth, handle, name, desc, profileId, bannerId, createdAt, deletedAt)

// expect accountId not to be null
fun ApiAccountProperty.toEntity() = AccountProperty(
    id,
    accountId!!,
    installationId,
    type,
    content,
    verificationCode,
    valid,
    primary,
    createdAt,
    deletedAt
)

@Resource("/accounts")
class Accounts {
    @Resource("{id}")
    class Id(val id: SUUID, val parent: Accounts = Accounts())

    @Resource("by-handle/{handle}")
    class ByHandle(val handle: String, val parent: Accounts = Accounts())

    @Resource("properties")
    class Properties(val parent: Accounts = Accounts())

    @Resource("links")
    class Links(val parent: Accounts = Accounts())

    @Resource("{id}/installations")
    class Installations(val id: SUUID, val parent: Accounts = Accounts())
}
