package app.mindspaces.clipboard.plugins

import app.mindspaces.clipboard.api.ApiMediaRequest
import app.mindspaces.clipboard.api.Message
import app.mindspaces.clipboard.api.Message.Devices
import app.mindspaces.clipboard.api.Message.MediaRequest
import app.mindspaces.clipboard.api.Message.Notice
import app.mindspaces.clipboard.api.SUUID
import app.mindspaces.clipboard.services.installationsService
import app.mindspaces.clipboard.services.mediasService
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.time.Duration.Companion.seconds

data class AccountConnectionState(
    // writable
    val send: MutableSharedFlow<Message>,
    // read-only
    val recv: SharedFlow<Message>,
    var cnt: Int = 1
)

fun Application.configureSockets() {
    val log = KtorSimpleLogger("sockets")

    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        contentConverter = KotlinxWebsocketSerializationConverter(Json {
            encodeDefaults = true
            isLenient = true
        })
    }
    routing {
        // TODO does UUID WORK? otherwise use string!!
        // one per actor, each device gets the same one
        // TODO move to one service, it just calls basic hashmap methods
        // TODO once sharedflow is not collected anymore... called might get stuck if it is accessed after client connection closes but before removed from map
        val clients = ConcurrentHashMap<UUID, AccountConnectionState>()

        // NOTE: broadcast?
        //val messageResponseFlow = MutableSharedFlow<Message>()
        //val sharedFlow = messageResponseFlow.asSharedFlow()

        authenticate("auth-jwt") {
            webSocket("/ws") {
                msg(Notice("connection established"))

                val principal = call.principal<JWTPrincipal>()
                val handle = principal!!.payload.getClaim("handle").asString()
                val accountId = UUID.fromString(principal.payload.getClaim("account_id").asString())
                val installationId =
                    UUID.fromString(principal.payload.getClaim("installation_id").asString())
                val ttl = principal.expiresAt!!.time.minus(System.currentTimeMillis())

                msg(Notice("authenticated - accountId=$accountId, handle=$handle, ttl=$ttl"))

                //val accountChan = clients.getOrPut(accountId) {
                //    val responseFlow = MutableSharedFlow<Message>()
                //    AccountConnectionState(responseFlow, responseFlow.asSharedFlow(), 1)
                //}
                val accountChan = clients.compute(accountId) { _, v ->
                    if (v == null) {
                        log.info("[$accountId/$handle]: initializing new account channel...")
                        val responseFlow = MutableSharedFlow<Message>()
                        return@compute AccountConnectionState(
                            responseFlow, responseFlow.asSharedFlow()
                        )
                    }
                    log.info("[$accountId/$handle]: current active account connections: ${v.cnt}+1")
                    v.cnt++
                    v
                }!!
                msg(Notice("config - accountChat=$accountChan"))

                val links = installationsService.listLinks(accountId)
                log.info("[$accountId/$handle]: links: #${links.size}")
                msg(Devices(links))

                // dev
                val reqMedia = mediasService.randomByInstallation(installationId)

                log.info("[$accountId/$handle]: requesting random media: $reqMedia")
                reqMedia?.let {
                    val req = ApiMediaRequest(
                        UUID.randomUUID(), reqMedia.id, installationId, Clock.System.now()
                    )
                    msg(MediaRequest(req))
                }

                val job = launch {
                    // TODO publish received media requests to account chan, on connect send currently existing (might be from before server start, etc.)
                    accountChan.recv.collect { message ->
                        sendSerialized(message)
                    }
                }

                runCatching {
                    while (isActive) {
                        val msg = receiveDeserialized<Message>()
                        accountChan.send.emit(msg)
                    }
                }.onFailure { exception ->
                    log.info("[$accountId/$handle]: socket err: ${exception.localizedMessage}")
                }.also {
                    log.info("[$accountId/$handle]: cancelling")
                    job.cancel()
                    log.info("[$accountId/$handle]: cancelled")
                    close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, "Receive failed"))
                    clients.computeIfPresent(accountId) { _, v ->
                        v.cnt--
                        if (v.cnt <= 0) {
                            log.info("[$accountId/$handle]: last connection lost, dropping channel")
                            return@computeIfPresent null
                        }
                        log.info("[$accountId/$handle]: remaining connections: ${v.cnt}")
                        v
                    }
                }
                log.info("[$accountId/$handle]: done")
            }
        }
        get("/stats") {
            val connections = mutableMapOf<UUID, Int>()
            for ((accountId, state) in clients) {
                connections[accountId] = state.cnt
            }
            call.respond(HttpStatusCode.OK, Stats(connections))
        }
    }
}

suspend fun WebSocketServerSession.msg(msg: Message) {
    sendSerialized<Message>(msg)
}

@Serializable
data class Stats(
    val connections: Map<SUUID, Int>
)
