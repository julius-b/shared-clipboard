package app.mindspaces.clipboard.api

import app.mindspaces.clipboard.db.Database
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.resources.post
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.isSuccess
import io.ktor.http.path
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

const val Host = "localhost"
const val Port = 8080
val Proto = URLProtocol.HTTP

fun newHttpClient(
    db: Database, onUnauthorized: () -> Unit
): HttpClient {
    val log = co.touchlab.kermit.Logger.withTag("http-client")
    log.d { "init (db: $db)" }

    val json = Json {
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
        isLenient = true
    }

    val wsJson = Json {
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    return HttpClient {
        install(Resources)
        install(WebSockets) {
            pingInterval = 15.seconds
            contentConverter = KotlinxWebsocketSerializationConverter(wsJson)
        }
        install(ContentNegotiation) {
            json(json)
        }
        install(UserAgent) {
            agent = "MindSpaces vX"
        }
        //expectSuccess = true
        // applies to http requests
        // for websocket, the protocol would be WS and path wouldn't contain api/v1
        defaultRequest {
            host = Host
            port = Port
            //host = Env.remote.host
            //port = Env.remote.port
            url {
                protocol = Proto
                //protocol = Env.remote.httpProto
                path("api/v1/")
            }
            header("X-MindSpaces-Client", "MindSpaces vX")
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    log.d { message }
                }
            }

            // TODO ALL except multipart body...
            level = LogLevel.HEADERS
            //sanitizeHeader { header -> header == HttpHeaders.Authorization }
        }
        install(Auth) {
            bearer {
                loadTokens {
                    log.d { "querying auth sessions..." }
                    val session = db.authSessionQueries.getLatest().executeAsOneOrNull()
                        ?: return@loadTokens null
                    log.i { "got auth session: $session" }
                    BearerTokens(session.access_token, session.refresh_token)
                }
                refreshTokens {
                    log.i { "refreshing..." }
                    val refreshToken = this.oldTokens?.refreshToken
                    if (refreshToken == null) {
                        // could occur when logout fails
                        log.e { "no previous tokens, forcing logout..." }
                        onUnauthorized()
                        return@refreshTokens null
                    }
                    log.d { "refresh token: $refreshToken" }
                    // TODO what happens if no network during this request?
                    val resp = client.post(AuthSessions.Refresh()) {
                        markAsRefreshTokenRequest()
                        // both is overridden by access_token
                        //bearerAuth(refreshToken)
                        //header("Bearer", refreshToken)
                        header(KeyRefreshToken, "Bearer $refreshToken")
                    }
                    if (!resp.status.isSuccess()) {
                        log.w { "refresh failed: $resp" }
                        // TODO trigger full logout if token was revoked
                        if (resp.status == HttpStatusCode.Unauthorized) {
                            log.w { "refresh rejected - signing out..." }
                            onUnauthorized()
                        }
                        return@refreshTokens null
                    }
                    // TODO update linked properties?? need direct update on change, server needs to flag change for client
                    //      but at the same time this could be a regular check
                    val success = resp.body<ApiSuccessResponse<ApiAuthSession>>()
                    val session = success.data.toEntity()

                    BearerTokens(session.access_token, session.refresh_token)
                }
            }
        }
    }
}
