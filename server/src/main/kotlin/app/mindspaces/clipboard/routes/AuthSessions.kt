package app.mindspaces.clipboard.routes

import app.mindspaces.clipboard.api.ApiError
import app.mindspaces.clipboard.api.ApiSuccessResponse
import app.mindspaces.clipboard.api.AuthHints
import app.mindspaces.clipboard.api.AuthSessionParams
import app.mindspaces.clipboard.api.HintedApiSuccessResponse
import app.mindspaces.clipboard.api.KeyInstallationID
import app.mindspaces.clipboard.api.KeyRefreshToken
import app.mindspaces.clipboard.services.accountPropertiesService
import app.mindspaces.clipboard.services.accountsService
import app.mindspaces.clipboard.services.authSessionsService
import app.mindspaces.clipboard.services.installationsService
import app.mindspaces.clipboard.services.sanitizeSecret
import app.mindspaces.clipboard.services.sanitizeUnique
import app.mindspaces.clipboard.services.toAuthSession
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.util.encodeBase64
import io.ktor.util.logging.KtorSimpleLogger
import java.util.Date
import java.util.UUID
import kotlin.random.Random

fun Route.authSessionsApi() {
    val log = KtorSimpleLogger("auth-sessions-api")

    val tokenSecret = environment.config.property("jwt.secret").getString()
    val tokenIssuer = environment.config.property("jwt.issuer").getString()
    val tokenAudience = environment.config.property("jwt.audience").getString()

    log.info("init - iss: $tokenIssuer, aud: $tokenAudience")

    fun genAccessToken(accountId: UUID, ioid: UUID, installationId: UUID, handle: String): String {
        val twoHours = 60_000 * 120

        // TODO startup check to "Ensure the length of the secret is at least 256 bit long"
        return JWT.create()
            .withAudience(tokenAudience)
            .withIssuer(tokenIssuer)
            .withClaim("handle", handle)
            .withClaim("account_id", accountId.toString())
            .withClaim("ioid", ioid.toString())
            .withClaim("installation_id", installationId.toString())
            .withExpiresAt(Date(System.currentTimeMillis() + twoHours))
            .sign(Algorithm.HMAC256(tokenSecret))
    }

    route("auth_sessions") {
        post {
            val installationId = UUID.fromString(call.request.headerOrFail(KeyInstallationID))
            val req = call.receive<AuthSessionParams>()

            val unique = req.unique.sanitizeUnique()
            val secret = req.secret.sanitizeSecret()
            var ioid = req.ioid

            var account = accountsService.getByHandle(unique)
            if (account == null) {
                val property = accountPropertiesService.getPrimaryByContent(unique)
                    ?: throw ValidationException(
                        "unique", ApiError.Reference(value = unique)
                    )
                account = accountsService.get(property.accountId!!)!!
            }

            if (account.secret != secret) throw ValidationException(
                "secret",
                ApiError.Forbidden(auth = "secret")
            )

            val secretUpdate = accountsService.getLatestSecretUpdate(account.id, account.secret)
            if (secretUpdate == null) {
                log.error("post - failed to find SecretUpdate, account=${account.id}")
                throw IllegalStateException("secret-update expected for account=${account.id}")
            }

            if (ioid != null) {
                val link = installationsService.getLink(ioid)
                if (link == null) {
                    call.respond(HttpStatusCode.UnprocessableEntity)
                    return@post
                }
                if (link.accountId != account.id) {
                    // TODO....... sometimes we use 403, sometimes forbidden in a 400
                    call.respond(HttpStatusCode.Forbidden)
                    return@post
                }
                // TODO delete keys (?)
            } else {
                log.info("post - linking account ${account.id} with installation $installationId")
                installationsService.deleteLinks(account.id, installationId)
                val link = installationsService.linkInstallation(account.id, installationId)

                // TODO broadcast to account socket

                ioid = link.id
            }

            val accessToken = genAccessToken(account.id, ioid, installationId, account.handle)
            val refreshToken = Random.nextBytes(64).encodeBase64()
            //val refreshToken = getRandomString(64, AlphaNumCharset)

            val dbAuthSession = authSessionsService.create(
                account.id, installationId, ioid, secretUpdate.id.value, refreshToken
            )
            val authSession = dbAuthSession.toAuthSession(accessToken)

            log.info("create - authSession: $authSession")

            val properties = accountPropertiesService.list(account.id)

            call.respond(
                HttpStatusCode.Created,
                HintedApiSuccessResponse(data = authSession, hints = AuthHints(properties, account))
            )
        }
        post("refresh") {
            val installationId = UUID.fromString(call.request.headerOrFail(KeyInstallationID))
            //val auth = call.request.headerOrFail(HttpHeaders.Authorization)
            val auth = call.request.headerOrFail(KeyRefreshToken)
            val tokens = auth.split(' ')
            if (tokens.size != 2 || tokens[0] != "Bearer") {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val currRefreshToken = tokens[1]
            val curr = authSessionsService.getByRefreshToken(currRefreshToken, installationId)
            if (curr == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            if (curr.installationId != installationId) {
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }

            val account = accountsService.get(curr.accountId)
            if (account == null) {
                call.respond(HttpStatusCode.UnprocessableEntity)
                return@post
            }

            val accessToken =
                genAccessToken(account.id, curr.ioid, curr.installationId, account.handle)
            val refreshToken = Random.nextBytes(64).encodeBase64()
            val dbAuthSession = authSessionsService.create(
                curr.accountId, curr.installationId, curr.ioid, curr.secretUpdateId, refreshToken
            )
            val authSession = dbAuthSession.toAuthSession(accessToken)

            log.info("refresh - new authSession: $authSession")

            call.respond(HttpStatusCode.Created, ApiSuccessResponse(data = authSession))
        }
    }
}
