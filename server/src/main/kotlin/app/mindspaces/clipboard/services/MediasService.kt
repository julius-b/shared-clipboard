package app.mindspaces.clipboard.services

import app.mindspaces.clipboard.api.ApiMedia
import app.mindspaces.clipboard.api.ApiMediaReceipt
import app.mindspaces.clipboard.plugins.DatabaseSingleton.tx
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.CompositeEntity
import org.jetbrains.exposed.dao.CompositeEntityClass
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

object Medias : UUIDTable() {
    val path = varchar("path", 1024)
    val dir = varchar("dir", 1024)

    // not supported by all filesystems
    val cre = long("cre").nullable()

    // ms
    val mod = long("mod")
    val size = long("size")
    val hasThumb = bool("has_thumb").default(false)
    val hasFile = bool("has_file").default(false)
    val installationId = reference("installation_id", Installations)
    val createdAt = timestamp("created_at").clientDefault {
        Clock.System.now()
    }
    val deletedAt = timestamp("deleted_at").nullable()
}

class MediaEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<MediaEntity>(Medias)

    var path by Medias.path
    var dir by Medias.dir
    var cre by Medias.cre
    var mod by Medias.mod
    var size by Medias.size
    var hasThumb by Medias.hasThumb
    var hasFile by Medias.hasFile
    var installationId by Medias.installationId
    val createdAt by Medias.createdAt
    val deletedAt by Medias.deletedAt
}

// TODO add MediaType to server-side api
fun MediaEntity.toDTO() =
    ApiMedia(
        id.value,
        path,
        dir,
        cre,
        mod,
        size,
        hasThumb,
        hasFile,
        null,
        installationId.value,
        createdAt,
        deletedAt
    )

object MediaReceipts : CompositeIdTable() {
    val mediaId = reference("media_id", Medias)
    val installationLinkId = reference("installation_link_id", InstallationLinks)
    val hasThumb = bool("has_thumb")
    val hasFile = bool("has_file")
    val createdAt = timestamp("created_at").clientDefault {
        Clock.System.now()
    }
    val deletedAt = timestamp("deleted_at").nullable()

    init {
        addIdColumn(mediaId)
        addIdColumn(installationLinkId)
    }

    override val primaryKey = PrimaryKey(mediaId, installationLinkId)
}

class MediaReceiptEntity(id: EntityID<CompositeID>) : CompositeEntity(id) {
    companion object : CompositeEntityClass<MediaReceiptEntity>(MediaReceipts)

    var mediaId by MediaReceipts.mediaId
    var installationLinkId by MediaReceipts.installationLinkId
    var hasThumb by MediaReceipts.hasThumb
    var hasFile by MediaReceipts.hasFile
    var createdAt by MediaReceipts.createdAt
    var deletedAt by MediaReceipts.deletedAt
}

fun MediaReceiptEntity.toDTO() =
    ApiMediaReceipt(mediaId.value, installationLinkId.value, hasThumb, hasFile)

// it's a word: https://en.wiktionary.org/wiki/medias#English
class MediasService {
    val log = KtorSimpleLogger("medias-svc")

    // don't return media uploaded by this device (TODO while prev link might be deleted, iid remains...)
    // - alt: for every upload create media-receipt of link_id -> needless dup
    suspend fun all(accountId: UUID? = null, installationLinkId: UUID? = null): List<ApiMedia> =
        tx {
            val installationLink = installationLinkId?.let { InstallationLinkEntity.findById(it) }

            MediaEntity.wrapRows(
                // TODO custom all for root, don't only return media files with active installation-link
                Medias
                    // 1-to-1
                    //.innerJoin(Installations)
                    // can't ensure 1-to-1 linking (also if using this: already apply accountId here)
                    //.innerJoin(InstallationLinks)
                    // 1-to-1 even with multiple links, left-join for root case
                    // media is accessible, through installation-links, to all these accounts
                    .leftJoin(
                        Accounts,
                        additionalConstraint = {
                            Accounts.id inSubQuery (
                                    InstallationLinks.select(InstallationLinks.accountId)
                                        .where {
                                            (InstallationLinks.installationId eq Medias.installationId) and (InstallationLinks.deletedAt eq null)
                                        }
                                    )
                        }
                    )
                    .leftJoin(
                        MediaReceipts,
                        onColumn = { Medias.id },
                        otherColumn = { mediaId },
                        additionalConstraint = { MediaReceipts.installationLinkId eq installationLinkId }
                    )
                    .selectAll()
                    // either both true or both false, media-receipts left-join is ignored if no accountId is submitted,
                    // therefore so is installationId
                    .apply {
                        if (accountId != null) where {
                            // NOTE Exposed does `eq` syntax needs all these parenthesis
                            //(InstallationLinks.accountId eq accountId) and
                            (Accounts.id eq accountId) and
                                    (MediaReceipts.mediaId eq null or
                                            (MediaReceipts.hasThumb neq Medias.hasThumb) or
                                            (MediaReceipts.hasFile neq Medias.hasFile)) and
                                    (Medias.installationId neq installationLink?.installationId?.value)
                        }
                    }
            ).map(MediaEntity::toDTO)
        }

    suspend fun get(id: UUID): ApiMedia? = tx {
        MediaEntity.findById(id)?.toDTO()
    }

    suspend fun noThumbOrNoFile() = tx {
        MediaEntity.find { Medias.hasThumb eq false or (Medias.hasFile eq false) }
            .map(MediaEntity::toDTO)
    }

    // TODO randomize
    suspend fun random(accountId: UUID): ApiMedia? = tx {
        MediaEntity.wrapRows(
            Medias.innerJoin(InstallationLinks).select(Medias.columns)
                .where { InstallationLinks.accountId eq accountId }
                .orderBy(Medias.path, SortOrder.DESC)
                .limit(1)
        ).firstOrNull()?.toDTO()
    }

    suspend fun randomByInstallation(installationId: UUID): ApiMedia? = tx {
        MediaEntity.find { Medias.installationId eq installationId and (Medias.hasFile eq false) }
            .firstOrNull()
            ?.toDTO()
    }

    // TODO what if submitted id/mod doesn't match current id/mod
    suspend fun dataAdded(
        id: UUID,
        path: String,
        dir: String,
        cre: Long?,
        mod: Long,
        size: Long,
        installationId: UUID,
        isFile: Boolean
    ): ApiMedia =
        tx {
            // we might return a mediaId: conflict error once, but after retries the client will get the 'success'
            // file might've been created as a blank (just the path)
            // or thumb upload response might've gotten lost
            // only update the value actually updated
            val updated = MediaEntity.findByIdAndUpdate(id) { media ->
                if (!isFile) media.hasThumb = true
                if (isFile) media.hasFile = true
                return@findByIdAndUpdate
            }?.toDTO()

            log.info("data-added - id: $id, isFile: $isFile (update: $updated)")
            if (updated != null) return@tx updated

            return@tx MediaEntity.new(id) {
                this.path = path
                this.dir = dir
                this.cre = cre
                this.mod = mod
                this.size = size
                this.hasThumb = !isFile
                this.hasFile = isFile
                this.installationId = EntityID(installationId, Installations)
            }.toDTO()
        }

    suspend fun allReceipts(): List<ApiMediaReceipt> = tx {
        MediaReceiptEntity.all().map(MediaReceiptEntity::toDTO)
    }

    suspend fun saveReceipt(
        mediaId: UUID,
        installationLinkId: UUID,
        hasThumb: Boolean? = null,
        hasFile: Boolean? = null
    ): ApiMediaReceipt =
        tx {
            val receiptId = CompositeID {
                it[MediaReceipts.mediaId] = mediaId
                it[MediaReceipts.installationLinkId] = installationLinkId
            }

            val updated = MediaReceiptEntity.findByIdAndUpdate(receiptId) { receipt ->
                if (hasThumb != null) receipt.hasThumb = hasThumb
                if (hasFile != null) receipt.hasFile = hasFile
            }?.toDTO()

            if (updated != null) return@tx updated

            return@tx MediaReceiptEntity.new(receiptId) {
                this.hasThumb = hasThumb ?: false
                this.hasFile = hasFile ?: false
            }.toDTO()
        }
}

val mediasService = MediasService()
