package app.mindspaces.clipboard.services

import app.mindspaces.clipboard.api.ApiAccount
import app.mindspaces.clipboard.api.ApiAccount.Auth
import app.mindspaces.clipboard.api.ApiAccount.Type
import app.mindspaces.clipboard.plugins.DatabaseSingleton.tx
import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.util.UUID

// max db width
const val handleWidth = 36

object Accounts : UUIDTable() {
    val type = enumerationByName<Type>("type", 10).default(Type.Account)
    val auth = enumerationByName<Auth>("auth", 10).default(Auth.Default)
    val handle = varchar("handle", handleWidth)
    val name = varchar("name", 50)
    val desc = varchar("desc", 250).nullable()
    val secret = varchar("secret", 1000)

    // TODO reference Media
    //val profileId = reference("profile_id", Medias).nullable()
    //val bannerId = reference("banner_id", Medias).nullable()
    val createdAt = timestamp("created_at").clientDefault {
        Clock.System.now()
    }
    val deletedAt = timestamp("deleted_at").nullable()

    init {
        uniqueIndex(handle) { deletedAt eq null }
    }
}

class AccountEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<AccountEntity>(Accounts)

    var type by Accounts.type
    var auth by Accounts.auth
    var handle by Accounts.handle
    var name by Accounts.name
    var desc by Accounts.desc
    var secret by Accounts.secret

    //var profileId by Accounts.profileId
    //var bannerId by Accounts.bannerId
    var createdAt by Accounts.createdAt
    var deletedAt by Accounts.deletedAt
}

fun AccountEntity.toDTO() =
    // profileId?.value, bannerId?.value
    ApiAccount(
        id.value,
        type,
        auth,
        handle,
        name,
        desc,
        secret,
        UUID.randomUUID(),
        UUID.randomUUID(),
        createdAt,
        deletedAt
    )

object SecretUpdates : UUIDTable() {
    val accountId = reference("account_id", Accounts)
    val secret = varchar("secret", 1000)
    val createdAt = timestamp("created_at").clientDefault {
        Clock.System.now()
    }
}

class SecretUpdateEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<SecretUpdateEntity>(SecretUpdates)

    var accountId by SecretUpdates.accountId
    var secret by SecretUpdates.secret
    var createdAt by SecretUpdates.createdAt
}

class AccountsService {
    // TODO filter deletedAt
    suspend fun all(): List<ApiAccount> = tx {
        AccountEntity.all().map(AccountEntity::toDTO)
    }

    // TODO filter deletedAt
    suspend fun get(id: UUID): ApiAccount? = tx {
        AccountEntity.findById(id)?.toDTO()
    }

    suspend fun getByHandle(handle: String): ApiAccount? = tx {
        AccountEntity.find { (Accounts.handle eq handle.sanitizeHandle()) and (Accounts.deletedAt eq null) }
            .firstOrNull()
            ?.toDTO()
    }

    suspend fun create(handle: String, name: String, secret: String): ApiAccount = tx {
        AccountEntity.new {
            this.handle = handle.sanitizeHandle()
            this.name = name.sanitizeName()
            this.secret = secret.sanitizeSecret()
        }.toDTO()
    }

    // TODO Entity syntax
    suspend fun delete(id: UUID): Boolean = tx {
        Accounts.deleteWhere { Accounts.id eq id } > 0
    }

    suspend fun createSecretUpdate(accountId: UUID, secret: String): Unit = tx {
        SecretUpdateEntity.new {
            this.accountId = EntityID(accountId, Accounts)
            this.secret = secret.sanitizeSecret()
        }
    }

    // TODO ensure latest created_at
    // secret: hash
    // TODO maybe delete old
    suspend fun getLatestSecretUpdate(accountId: UUID, secret: String): SecretUpdateEntity? = tx {
        SecretUpdateEntity.find { (SecretUpdates.accountId eq accountId) and (SecretUpdates.secret eq secret) }
            .firstOrNull()
    }
}

fun String.sanitizeUnique() = this.sanitizeHandle()
fun String.sanitizeHandle() = this.trim().lowercase()
fun String.sanitizeSecret() = this.trim().replaceFirstChar { it.lowercaseChar() }
fun String.sanitizeName() = this.trim()

val accountsService = AccountsService()
