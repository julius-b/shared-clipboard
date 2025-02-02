package app.mindspaces.clipboard.repo

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.mindspaces.clipboard.api.ApiErrorResponse
import app.mindspaces.clipboard.api.ApiInstallation
import app.mindspaces.clipboard.api.ApiInstallationLink
import app.mindspaces.clipboard.api.ApiSuccessResponse
import app.mindspaces.clipboard.api.InstallationLinkNameParams
import app.mindspaces.clipboard.api.InstallationParams
import app.mindspaces.clipboard.api.Installations
import app.mindspaces.clipboard.api.KeyInstallationID
import app.mindspaces.clipboard.api.toEntity
import app.mindspaces.clipboard.db.AllLinks
import app.mindspaces.clipboard.db.Database
import app.mindspaces.clipboard.db.Installation
import app.mindspaces.clipboard.db.InstallationLink
import app.mindspaces.clipboard.getPlatform
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.plugins.resources.put
import io.ktor.client.request.headers
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.util.AttributeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.util.UUID

@Inject
@SingleIn(AppScope::class)
class InstallationRepository(db: Database, private val client: HttpClient) {
    private val log = Logger.withTag("InstallationRepo")

    private val installationQueries = db.installationQueries

    // sync lock
    private val mutex = Mutex()

    init {
        log.d { "init $this (db: $db, client: $client)" }

        client.plugin(HttpSend).intercept { req ->
            if (req.attributes.contains(InstallationCircuitBreaker)) {
                log.i { "req '${req.method} ${req.url}' is flagged as installation init, not attaching $KeyInstallationID..." }
                return@intercept execute(req)
            }
            // TODO only attach for verified Host
            log.i { "req '${req.method} ${req.url}'; attaching $KeyInstallationID" }
            val installationRes = sync()
            if (installationRes !is RepoResult.Data) {
                log.e { "req '${req.method} ${req.url}': failed to sync installation" }
                // simply caught as Throwable and treated like other NetworkErrors
                throw IllegalStateException("failed to sync installation")
                //return@intercept execute(req).apply {
                //    attributes.put(InstallationPutFailed, Unit)
                //}
            }
            val installationId = installationRes.data.id
            // set don't add (it's ; appended)
            //req.header(KeyInstallationId, installationId)
            req.headers {
                set(KeyInstallationID, "$installationId")
            }
            log.d { "req '${req.method} ${req.url}'; attached $KeyInstallationID=$installationId" }
            execute(req)
        }
    }

    fun self() =
        installationQueries.getSelf().asFlow().mapToOneOrNull(Dispatchers.IO).distinctUntilChanged()

    fun selfAsOne() = installationQueries.getSelf().executeAsOneOrNull()

    fun saveLinks(links: List<ApiInstallationLink>) {
        installationQueries.transaction {
            val self = installationQueries.getSelf().executeAsOne()

            for (link in links) {
                // link might have a new name
                installationQueries.insertLink(link.toEntity())
                // don't override self from server, local installation info is only _sent_
                if (self.id != link.installationId) {
                    installationQueries.insert(link.installation.toEntity(false))
                }
            }
        }
    }

    fun allLinks(): Flow<List<AllLinks>> {
        return installationQueries.allLinks().asFlow().mapToList(Dispatchers.IO)
            .distinctUntilChanged()
    }

    fun saveInstallation(installation: Installation) {
        installationQueries.insert(installation)
    }

    suspend fun updateLinkName(linkId: UUID, name: String): RepoResult<InstallationLink> {
        try {
            val resp = client.put(Installations.Links.UpdateName(linkId)) {
                contentType(ContentType.Application.Json)
                setBody(InstallationLinkNameParams(name))
            }
            if (!resp.status.isSuccess()) {
                log.e { "update-link-name(linkId=$linkId, name=$name) - failed: $resp" }
                val err = resp.body<ApiErrorResponse>()
                return RepoResult.ValidationError(err.errors)
            }
            val success = resp.body<ApiSuccessResponse<ApiInstallationLink>>()
            val link = success.data.toEntity()

            installationQueries.insertLink(link)
            return RepoResult.Data(link)
        } catch (e: Throwable) {
            log.e(e) { "update-link-name(linkId=$linkId, name=$name) - unexpected resp: $e" }
            return RepoResult.NetworkError
        }
    }

    private suspend fun sync(): RepoResult<Installation> = mutex.withLock {
        log.d { "sync - syncing..." }
        val cached = installationQueries.getSelf().executeAsOneOrNull()
        log.d { "sync - cached: $cached" }
        if (cached != null) return@withLock RepoResult.Data(cached, true)

        // NOTE: InetAddress causes exception on android, use expect/actual: withContext(Dispatchers.IO) { InetAddress.getLocalHost() }.hostName
        val hostname = "a name"
        val os = getPlatform()
        val params =
            InstallationParams(UUID.randomUUID(), hostname, "uh wee", os, "mindspaces_kt_mpp/1")
        log.d { "sync - generated: $params, synchronizing..." }
        try {
            val resp = client.put(Installations()) {
                contentType(ContentType.Application.Json)
                setBody(params)
                attributes.put(InstallationCircuitBreaker, Unit)
            }
            if (resp.status.isSuccess()) {
                val success = resp.body<ApiSuccessResponse<ApiInstallation>>()
                val installation = success.data.toEntity(true)

                installationQueries.insert(installation)
                log.d { "sync - saved: $installation" }

                return@withLock RepoResult.Data(installation)
            }
            log.e { "sync - unexpected resp: $resp" }
            val err = resp.body<ApiErrorResponse>()
            return@withLock RepoResult.ValidationError(err.errors)
        } catch (e: Throwable) {
            log.e(e) { "sync - unexpected resp: $e" }
            return@withLock RepoResult.NetworkError
        }
    }

    companion object {
        val InstallationCircuitBreaker: AttributeKey<Unit> = AttributeKey("installation-request")
        val InstallationPutFailed: AttributeKey<Unit> = AttributeKey("installation-put-failed")
    }
}
