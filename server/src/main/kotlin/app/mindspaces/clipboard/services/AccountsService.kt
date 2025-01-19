package app.mindspaces.clipboard.services

import app.mindspaces.clipboard.api.ApiAccount
import app.mindspaces.clipboard.api.ApiAccount.Auth
import app.mindspaces.clipboard.api.ApiAccount.Type
import app.mindspaces.clipboard.api.ApiAccountProperty
import app.mindspaces.clipboard.api.ApiError
import app.mindspaces.clipboard.api.KeyChallengeResponse
import app.mindspaces.clipboard.plugins.DatabaseSingleton.tx
import app.mindspaces.clipboard.routes.ValidationException
import io.ktor.util.logging.KtorSimpleLogger
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
const val HandleMaxLength = 36

object Accounts : UUIDTable() {
    val type = enumerationByName<Type>("type", 10).default(Type.Account)
    val auth = enumerationByName<Auth>("auth", 10).default(Auth.Default)
    val handle = varchar("handle", HandleMaxLength)
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
    private val log = KtorSimpleLogger("accounts-svc")

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

    suspend fun create(
        handle: String, name: String, secret: String, properties: MutableList<ApiAccountProperty>
    ): ApiAccount = tx {
        val account = AccountEntity.new {
            this.handle = handle.sanitizeHandle()
            this.name = name.sanitizeName()
            this.secret = secret.sanitizeSecret()
        }

        // ensure account.new tx only succeeds when properties are linked successfully
        for (i in 0 until properties.size) {
            // TODO use ownAndPrimarizeProperty, but nested suspend tx not possible
            properties[i] = AccountPropertyEntity.findByIdAndUpdate(properties[i].id) {
                it.accountId = account.id
                it.primary = true
            }?.toDTO() ?: throw ValidationException(
                KeyChallengeResponse, ApiError.Reference(properties[i].id.toString())
            )
        }

        // TODO use createSecretUpdate, but nested suspend tx not possible
        val secretUpdate = SecretUpdateEntity.new {
            this.accountId = account.id
            this.secret = secret.sanitizeSecret()
        }
        log.info("create - secret-update: $secretUpdate")

        account.toDTO()
    }

    // TODO Entity syntax
    suspend fun delete(id: UUID): Boolean = tx {
        Accounts.deleteWhere { Accounts.id eq id } > 0
    }

    private suspend fun createSecretUpdate(accountId: UUID, secret: String): Unit = tx {
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

fun String.sanitizeHandle() = this.trim().lowercase()
fun String.sanitizeSecret() = this.trim().replaceFirstChar { it.lowercaseChar() }
fun String.sanitizeName() = this.trim()

val accountsService = AccountsService()
