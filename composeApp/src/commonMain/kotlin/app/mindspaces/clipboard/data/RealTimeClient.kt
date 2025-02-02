package app.mindspaces.clipboard.data

import app.mindspaces.clipboard.api.Message
import app.mindspaces.clipboard.api.toEntity
import app.mindspaces.clipboard.repo.AuthRepository
import app.mindspaces.clipboard.repo.InstallationRepository
import app.mindspaces.clipboard.repo.MediaRepository
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.webSocket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import java.net.ConnectException
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class RealTimeClient(
    private val client: HttpClient,
    private val installationRepository: InstallationRepository,
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository
) {
    private val log = Logger.withTag("rt-client")

    suspend fun connect() {
        log.i { "launching..." }
        authRepository.self().map { it?.id }.collectLatest { self ->
            if (self == null) {
                log.w { "standby (reason: not authenticated)..." }
                return@collectLatest
            }

            while (true) {
                log.i { "attempting to establish a connection (self: $self)..." }
                try {
                    client.webSocket(path = "/ws") {
                        log.i { "connection established (self: $self)" }

                        while (isActive) {
                            val msg = receiveDeserialized<Message>()
                            log.d { "recv: $msg" }
                            if (!handleMessage(self, msg)) {
                                log.e { "failed to handle message: $msg, ignoring..." }
                            }
                        }
                    }
                } catch (e: Throwable) {
                    if (e is CancellationException) {
                        log.w { "cancelled" }
                        break
                    } else if (e is ConnectException || e is ClosedReceiveChannelException) {
                        // ConnectTimeoutException : ConnectException
                        // don't log the whole stacktrace
                        log.w { "encountered network issue (assuming temporary): $e" }
                    } else {
                        // possibly auth related (outdated tokens -> hope for refresh at some point)
                        log.e(e) { "encountered unexpected exception (assuming temporary): $e" }
                    }
                } finally {
                    // TODO observe network state instead
                    // TODO instead of multiple nested loops, collectAsRetainedState might be nice
                    log.i { "standby (reason: reconnect timeout)..." }
                    delay(10.seconds)
                }
            }
        }
    }

    // TODO if parallelizing, ensure a) limits on concurrent uploads b) all launches are stopped on disconnect
    //      (which might not be a cancel per-se, so lauch in same scope and join that in finally)
    private fun handleMessage(self: UUID, msg: Message): Boolean {
        when (msg) {
            is Message.Notice -> log.i { "received srv notice: ${msg.txt}" }
            is Message.MediaRequest -> {
                val req = msg.req
                log.i { "received media-request, saving: $req..." }
                mediaRepository.saveRequest(req.toEntity())
            }

            is Message.Devices -> {
                val devices = msg.devices
                log.i { "received devices list, saving (${devices.size}): $devices" }
                for (device in devices) {
                    log.i { "- device: $device" }
                }
                installationRepository.saveLinks(devices)
            }

            is Message.DataNotification -> {
                val notification = msg.notification
                val id = UUID.randomUUID()
                log.i { "received data-notification, saving: $notification with id: $id" }
                mediaRepository.saveDataNotification(notification.toEntity(id))
            }
        }

        return true
    }
}
