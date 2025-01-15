package app.mindspaces.clipboard.services

import app.mindspaces.clipboard.api.ApiAuthSession
import app.mindspaces.clipboard.plugins.DatabaseSingleton.tx
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.util.UUID

object AuthSessions : UUIDTable() {
    val accountId = reference("account_id", Accounts)
    val installationId = reference("installation_id", Installations)

    // TODO ilid
    val ioid = reference("ioid", InstallationLinks)
    val secretUpdateId = reference("secret_update_id", SecretUpdates)
    val refreshToken = varchar("refresh_token", 100)
    val createdAt = timestamp("created_at").clientDefault {
        Clock.System.now()
    }
    val deletedAt = timestamp("deleted_at").nullable()
}

class AuthSessionEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<AuthSessionEntity>(AuthSessions)

    var accountId by AuthSessions.accountId
    var installationId by AuthSessions.installationId
    var ioid by AuthSessions.ioid
    var secretUpdateId by AuthSessions.secretUpdateId
    var refreshToken by AuthSessions.refreshToken
    var createdAt by AuthSessions.createdAt
    var deletedAt by AuthSessions.deletedAt
}

data class AuthSessionDAO(
    val id: UUID,
    val accountId: UUID,
    val installationId: UUID,
    val ioid: UUID,
    val secretUpdateId: UUID,
    val refreshToken: String,
    val createdAt: Instant,
    val deletedAt: Instant?
)

fun AuthSessionEntity.toDAO() = AuthSessionDAO(
    id.value,
    accountId.value,
    installationId.value,
    ioid.value,
    secretUpdateId.value,
    refreshToken,
    createdAt,
    deletedAt
)

fun AuthSessionDAO.toAuthSession(accessToken: String) = ApiAuthSession(
    id,
    accountId,
    installationId,
    ioid,
    secretUpdateId,
    refreshToken,
    accessToken,
    createdAt,
    deletedAt
)

class AuthSessionsService {
    suspend fun all(): List<AuthSessionDAO> = tx {
        AuthSessionEntity.all().map { it.toDAO() }
    }

    suspend fun get(id: UUID): AuthSessionDAO? = tx {
        AuthSessionEntity.findById(id)?.toDAO()
    }

    suspend fun getByRefreshToken(refreshToken: String, installationId: UUID): AuthSessionDAO? =
        tx {
            AuthSessionEntity.find { (AuthSessions.refreshToken eq refreshToken) and (AuthSessions.installationId eq installationId) }
                .firstOrNull()?.toDAO()
        }

    suspend fun create(
        accountId: UUID,
        installationId: UUID,
        ioid: UUID,
        secretUpdateId: UUID,
        refreshToken: String
    ): AuthSessionDAO = tx {
        AuthSessionEntity.new {
            this.accountId = EntityID(accountId, Accounts)
            this.installationId = EntityID(installationId, Installations)
            this.ioid = EntityID(ioid, InstallationLinks)
            this.secretUpdateId = EntityID(secretUpdateId, SecretUpdates)
            this.refreshToken = refreshToken
        }.toDAO()
    }
}

val authSessionsService = AuthSessionsService()
