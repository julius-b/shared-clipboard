package app.mindspaces.clipboard.services

import app.mindspaces.clipboard.api.ApiInstallation
import app.mindspaces.clipboard.api.ApiInstallation.Platform
import app.mindspaces.clipboard.api.ApiInstallationLink
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

object Installations : UUIDTable() {
    val name = varchar("name", 50)
    val desc = varchar("desc", 250)
    val os = enumerationByName<Platform>("os", 10)
    val client = varchar("client", 50)
    val createdAt = timestamp("created_at").clientDefault {
        Clock.System.now()
    }

    // NOTE: not supported by clients, only delete link
    val deletedAt = timestamp("deleted_at").nullable()
}

class InstallationEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<InstallationEntity>(Installations)

    var name by Installations.name
    var desc by Installations.desc
    var os by Installations.os
    var client by Installations.client
    var createdAt by Installations.createdAt
    var deletedAt by Installations.deletedAt
}

object InstallationLinks : UUIDTable() {
    val name = varchar("name", 50).nullable()
    val installationId = reference("installation_id", Installations)
    val accountId = reference("account_id", Accounts)
    val createdAt = timestamp("created_at").clientDefault {
        Clock.System.now()
    }
    val deletedAt = timestamp("deleted_at").nullable()

    init {
        uniqueIndex(installationId, accountId) { deletedAt eq null }
    }
}

class InstallationLinkEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<InstallationLinkEntity>(InstallationLinks)

    var name by InstallationLinks.name
    var installationId by InstallationLinks.installationId
    var installation by InstallationEntity referencedOn InstallationLinks.installationId
    var accountId by InstallationLinks.accountId
    var createdAt by InstallationLinks.createdAt
    var deletedAt by InstallationLinks.deletedAt
}

class InstallationsService {
    suspend fun all(): List<ApiInstallation> = tx {
        InstallationEntity.all().map(InstallationEntity::toDTO)
    }

    suspend fun get(id: UUID): ApiInstallation? = tx {
        InstallationEntity.findById(id)?.toDTO()
    }

    suspend fun create(
        id: UUID, name: String, desc: String, os: Platform, client: String
    ): ApiInstallation = tx {
        val curr = InstallationEntity.findByIdAndUpdate(id) {
            it.name = name
            it.desc = desc
            it.os = os
            it.client = client
        }
        if (curr != null) return@tx curr.toDTO()

        InstallationEntity.new(id) {
            this.name = name
            this.desc = desc
            this.os = os
            this.client = client
        }.toDTO()
    }

    suspend fun linkInstallation(accountId: UUID, installationId: UUID): ApiInstallationLink = tx {
        InstallationLinkEntity.new {
            this.installationId = EntityID(installationId, Installations)
            this.accountId = EntityID(accountId, Accounts)
        }.toDTO()
    }

    suspend fun listLinks(aid: UUID): List<ApiInstallationLink> = tx {
        InstallationLinkEntity.find { InstallationLinks.accountId eq aid }
            .map(InstallationLinkEntity::toDTO)
    }

    suspend fun allLinks(): List<ApiInstallationLink> = tx {
        InstallationLinkEntity.all().map(InstallationLinkEntity::toDTO)
    }

    suspend fun getLink(id: UUID): ApiInstallationLink? = tx {
        InstallationLinkEntity.findById(id)?.toDTO()
    }

    suspend fun updateLink(id: UUID, name: String): ApiInstallationLink? = tx {
        InstallationLinkEntity.findByIdAndUpdate(id) {
            it.name = name
        }?.toDTO()
    }

    suspend fun deleteLinks(accountId: UUID, installationId: UUID) = tx {
        InstallationLinkEntity.find { (InstallationLinks.accountId eq accountId) and (InstallationLinks.installationId eq installationId) }
            .forUpdate().forEach { it.deletedAt = Clock.System.now() }
    }

    // TODO Entity syntax
    suspend fun delete(id: UUID): Boolean = tx {
        Installations.deleteWhere { Installations.id eq id } > 0
    }
}

fun InstallationEntity.toDTO() =
    ApiInstallation(id.value, name, desc, os, client, createdAt, deletedAt)

fun InstallationLinkEntity.toDTO() = ApiInstallationLink(
    id.value,
    name,
    installationId.value,
    installation.toDTO(),
    accountId.value,
    createdAt,
    deletedAt
)

val installationsService = InstallationsService()
