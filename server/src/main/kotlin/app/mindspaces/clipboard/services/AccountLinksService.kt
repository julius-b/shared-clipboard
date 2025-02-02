package app.mindspaces.clipboard.services

import app.mindspaces.clipboard.api.ApiAccountLink
import app.mindspaces.clipboard.api.ApiAccountLink.Perm
import app.mindspaces.clipboard.plugins.DatabaseSingleton.tx
import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.util.UUID

object AccountLinks : UUIDTable() {
    val accountId = reference("account_id", Accounts)
    val peerId = reference("peer_id", Accounts)
    val perm = enumerationByName<Perm>("perm", 10).default(Perm.Invited)
    val createdAt = timestamp("created_at").clientDefault {
        Clock.System.now()
    }
    val createdBy = reference("created_by", Accounts)
    val deletedAt = timestamp("deleted_at").nullable()

    init {
        uniqueIndex(accountId, peerId) { deletedAt eq null }
    }
}

class AccountLinkEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<AccountLinkEntity>(AccountLinks)

    var accountId by AccountLinks.accountId
    var peerId by AccountLinks.peerId
    var perm by AccountLinks.perm
    var createdAt by AccountLinks.createdAt
    var createdBy by AccountLinks.createdBy
    var deletedAt by AccountLinks.deletedAt
}

class AccountLinksService {
    suspend fun all(): List<ApiAccountLink> = tx {
        AccountLinkEntity.all().map(AccountLinkEntity::toDTO)
    }

    suspend fun listByAccount(accountId: UUID): List<ApiAccountLink> = tx {
        AccountLinkEntity.find { (AccountLinks.accountId eq accountId) and (AccountLinks.deletedAt eq null) }
            .map(AccountLinkEntity::toDTO)
    }

    suspend fun get(id: UUID): ApiAccountLink? = tx {
        AccountLinkEntity.findById(id)?.toDTO()
    }

    suspend fun create(accountId: UUID, peerId: UUID): ApiAccountLink = tx {
        AccountLinkEntity.new {
            this.accountId = EntityID(accountId, Accounts)
            this.peerId = EntityID(peerId, Accounts)
        }.toDTO()
    }
}

fun AccountLinkEntity.toDTO() =
    ApiAccountLink(accountId.value, peerId.value, perm, createdAt, createdBy.value, deletedAt)

val accountLinksService = AccountLinksService()
