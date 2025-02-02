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
import app.mindspaces.clipboard.services.sanitizeHandle
import app.mindspaces.clipboard.services.sanitizeSecret
import app.mindspaces.clipboard.services.toAuthSession
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.util.logging.KtorSimpleLogger
import java.util.Date
import java.util.UUID

fun Route.authSessionsApi() {
    val log = KtorSimpleLogger("auth-sessions-api")

    route("auth_sessions") {
        post {
            val installationId = UUID.fromString(call.request.headerOrFail(KeyInstallationID))
            val req = call.receive<AuthSessionParams>()

            // property is not saved as lowercase
            val unique = req.unique.trim()
            val secret = req.secret.sanitizeSecret()

            var account = accountsService.getByHandle(unique.sanitizeHandle())
            if (account == null) {
                val property = accountPropertiesService.getPrimaryByContent(unique)
                    ?: throw ValidationException("unique", ApiError.Reference(value = unique))
                account = accountsService.get(property.accountId!!)!!
            }

            if (account.secret != secret) throw ValidationException("secret", ApiError.Forbidden())

            val secretUpdate = accountsService.getLatestSecretUpdate(account.id, account.secret)
            if (secretUpdate == null) {
                log.error("post - failed to find SecretUpdate, account=${account.id}")
                throw ValidationException("secret_update", ApiError.Internal())
            }

            val link = if (req.linkId != null) {
                val link = installationsService.getLink(req.linkId!!) ?: throw ValidationException(
                    "link_id", ApiError.Reference(req.linkId.toString())
                )
                if (link.installationId != installationId) throw ValidationException(
                    "link_id", ApiError.Forbidden(req.linkId.toString(), "installation_id")
                )
                if (link.accountId != account.id) throw ValidationException(
                    "link_id", ApiError.Forbidden(req.linkId.toString(), "account_id")
                )
                link
                // TODO delete keys (?)
            } else {
                // If client is not aware of previous link,
                // it's most likely not aware of that links client-side generated resources either
                log.info("post - linking account ${account.id} with installation $installationId")
                installationsService.deleteLinks(account.id, installationId)
                installationsService.linkInstallation(account.id, installationId)
                // TODO broadcast to account socket
            }

            val accessToken = genAccessToken(account.id, link.id, installationId, account.handle)
            val dbAuthSession = authSessionsService.create(
                account.id, installationId, link.id, secretUpdate.id.value
            )
            val authSession = dbAuthSession.toAuthSession(accessToken)
            log.info("post - authSession: $authSession")

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
                genAccessToken(account.id, curr.linkId, curr.installationId, account.handle)
            val dbAuthSession = authSessionsService.create(
                curr.accountId, curr.installationId, curr.linkId, curr.secretUpdateId
            )
            val authSession = dbAuthSession.toAuthSession(accessToken)
            log.info("refresh - new authSession: $authSession")

            call.respond(HttpStatusCode.Created, ApiSuccessResponse(data = authSession))
        }
    }
}

fun Route.genAccessToken(
    accountId: UUID, linkId: UUID, installationId: UUID, handle: String
): String {
    val log = KtorSimpleLogger("gen-access-token")
    val twoHours = 60_000 * 120

    val tokenSecret = environment.config.property("jwt.secret").getString()
    val tokenIssuer = environment.config.property("jwt.issuer").getString()
    val tokenAudience = environment.config.property("jwt.audience").getString()

    log.info("gen-access-token - iss: $tokenIssuer, aud: $tokenAudience, handle: $handle")

    // TODO startup check to "Ensure the length of the secret is at least 256 bit long"
    return JWT.create()
        .withAudience(tokenAudience)
        .withIssuer(tokenIssuer)
        .withClaim("handle", handle)
        .withClaim("account_id", accountId.toString())
        .withClaim("link_id", linkId.toString())
        .withClaim("installation_id", installationId.toString())
        .withExpiresAt(Date(System.currentTimeMillis() + twoHours))
        .sign(Algorithm.HMAC256(tokenSecret))
}
