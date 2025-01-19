package app.mindspaces.clipboard.services

import app.mindspaces.clipboard.api.ApiAccountProperty
import app.mindspaces.clipboard.api.ApiAccountProperty.Type
import app.mindspaces.clipboard.plugins.DatabaseSingleton.tx
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
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.trim
import java.security.SecureRandom
import java.util.UUID

object AccountProperties : UUIDTable() {
    // nullable during signup
    val accountId = reference("account_id", Accounts).nullable()
    val installationId = reference("installation_id", Installations)
    val type = enumerationByName<Type>("type", 10)
    val content = varchar("content", 125)
    val verificationCode = varchar("verification_code", 25)
    val valid = bool("valid").default(false)
    val primary = bool("primary").nullable()
    val createdAt = timestamp("created_at").clientDefault {
        Clock.System.now()
    }
    val deletedAt = timestamp("deleted_at").nullable()

    init {
        uniqueIndex(
            functions = listOf(content.trim().lowerCase())
        ) { (primary eq true) and (deletedAt eq null) }
        uniqueIndex(accountId, type) { (primary eq true) and (deletedAt eq null) }
    }
}

class AccountPropertyEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<AccountPropertyEntity>(AccountProperties)

    var accountId by AccountProperties.accountId
    var installationId by AccountProperties.installationId
    var type by AccountProperties.type
    var content by AccountProperties.content
    var verificationCode by AccountProperties.verificationCode
    var valid by AccountProperties.valid
    var primary by AccountProperties.primary
    var createdAt by AccountProperties.createdAt
    var deletedAt by AccountProperties.deletedAt
}

fun AccountPropertyEntity.toDTO() = ApiAccountProperty(
    id.value,
    accountId?.value,
    installationId.value,
    type,
    content,
    verificationCode,
    valid,
    primary,
    createdAt,
    deletedAt
)

class AccountPropertiesService {
    private val log = KtorSimpleLogger("account-props-svc")

    suspend fun all(): List<ApiAccountProperty> = tx {
        AccountPropertyEntity.all().map(AccountPropertyEntity::toDTO)
    }

    suspend fun get(id: UUID): ApiAccountProperty? = tx {
        AccountPropertyEntity.find { (AccountProperties.id eq id) and (AccountProperties.deletedAt eq null) }
            .firstOrNull()
            ?.toDTO()
    }

    suspend fun list(accountId: UUID): List<ApiAccountProperty> = tx {
        AccountPropertyEntity.find { (AccountProperties.accountId eq accountId) and (AccountProperties.deletedAt eq null) }
            .map(AccountPropertyEntity::toDTO)
    }

    suspend fun getPrimaryByContent(content: String): ApiAccountProperty? = tx {
        // compare as lowercase, TODO use db lowerCase
        val c = content.trim().lowercase()
        AccountPropertyEntity.find { (AccountProperties.primary eq true) and (AccountProperties.content.lowerCase() eq c) and (AccountProperties.deletedAt eq null) }
            .firstOrNull()?.toDTO()
    }

    suspend fun create(
        installationId: UUID,
        type: Type,
        content: String
    ): ApiAccountProperty = tx {
        val c = content.trim()
        // [min, max)
        val verificationCode = SecureRandom.getInstanceStrong().nextInt(100_000, 1_000_000)
        log.info("create - content: $c, verificationCode: $verificationCode")

        AccountPropertyEntity.new {
            this.installationId = EntityID(installationId, Installations)
            this.type = type
            this.content = c
            this.verificationCode = verificationCode.toString()
        }.toDTO()
    }

    suspend fun validateProperty(id: UUID): ApiAccountProperty? = tx {
        AccountPropertyEntity.findByIdAndUpdate(id) {
            it.valid = true
        }?.toDTO()
    }

    suspend fun ownAndPrimarizeProperty(id: UUID, accountId: UUID): ApiAccountProperty? = tx {
        AccountPropertyEntity.findByIdAndUpdate(id) {
            it.accountId = EntityID(accountId, Accounts)
            it.primary = true
        }?.toDTO()
    }

    // TODO Entity syntax
    suspend fun delete(id: UUID): Boolean = tx {
        AccountProperties.deleteWhere { AccountProperties.id eq id } > 0
    }
}

val accountPropertiesService = AccountPropertiesService()
