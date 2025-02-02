package app.mindspaces.clipboard.routes

import app.mindspaces.clipboard.api.AccountHints
import app.mindspaces.clipboard.api.AccountLinkParams
import app.mindspaces.clipboard.api.AccountParams
import app.mindspaces.clipboard.api.AccountPropertyParams
import app.mindspaces.clipboard.api.ApiAccount
import app.mindspaces.clipboard.api.ApiAccountProperty
import app.mindspaces.clipboard.api.ApiError
import app.mindspaces.clipboard.api.ApiSuccessResponse
import app.mindspaces.clipboard.api.HintedApiSuccessResponse
import app.mindspaces.clipboard.api.KeyChallengeResponse
import app.mindspaces.clipboard.api.KeyInstallationID
import app.mindspaces.clipboard.api.MinSecretSize
import app.mindspaces.clipboard.api.SignupParams
import app.mindspaces.clipboard.services.accountLinksService
import app.mindspaces.clipboard.services.accountPropertiesService
import app.mindspaces.clipboard.services.accountsService
import app.mindspaces.clipboard.services.authSessionsService
import app.mindspaces.clipboard.services.installationsService
import app.mindspaces.clipboard.services.sanitizeName
import app.mindspaces.clipboard.services.sanitizeSecret
import app.mindspaces.clipboard.services.toAuthSession
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.util.logging.KtorSimpleLogger
import java.util.UUID

fun Route.accountsApi() {
    val log = KtorSimpleLogger("accounts-api")

    route("accounts") {
        // shortcut for property + account + auth-session
        // unvalidated properties
        post("signup") {
            val installationId = UUID.fromString(call.request.headerOrFail(KeyInstallationID))
            val req = call.receive<SignupParams>().sanitize()

            // save unvalidated property
            val property = accountPropertiesService.create(
                installationId, ApiAccountProperty.Type.Email, req.email
            )
            val properties = mutableListOf(property)

            val account =
                createAccount(UUID.randomUUID().toString(), req.name, req.secret, properties)

            val secretUpdate = accountsService.getLatestSecretUpdate(account.id, account.secret)
            if (secretUpdate == null) {
                log.error("signup - failed to find SecretUpdate, account=${account.id}")
                throw ValidationException("secret_update", ApiError.Internal())
            }

            // does not need to run in the same tx, but it'd be nice
            val link = installationsService.linkInstallation(account.id, installationId)
            val accessToken = genAccessToken(account.id, link.id, installationId, account.handle)
            val dbAuthSession = authSessionsService.create(
                account.id, installationId, link.id, secretUpdate.id.value
            )
            val authSession = dbAuthSession.toAuthSession(accessToken)

            call.respond(
                HttpStatusCode.Created,
                HintedApiSuccessResponse(
                    data = account,
                    hints = AccountHints(properties = properties, session = authSession)
                )
            )
        }
        post {
            val installationId = UUID.fromString(call.request.headerOrFail(KeyInstallationID))
            val req = call.receive<AccountParams>().sanitize()

            val responses = call.request.headers.getAll(KeyChallengeResponse)
                ?: throw ValidationException(KeyChallengeResponse, ApiError.Required())
            val properties = mutableListOf<ApiAccountProperty>()
            // NOTE: multiple values for one key might be joined by commas or semicolons
            responses.map { it.trim().split(';', ',') }.flatten().forEach { resp ->
                val split = resp.split("=")
                if (split.size != 2) {
                    log.warn("post - invalid challenge-response: $resp ($split)")
                    throw ValidationException(
                        KeyChallengeResponse, ApiError.Schema(resp, "<uuid>=<code>")
                    )
                }
                val id = UUID.fromString(split[0])
                var property = accountPropertiesService.get(id) ?: throw ValidationException(
                    KeyChallengeResponse, ApiError.Reference(id.toString())
                )
                if (property.installationId != installationId) throw ValidationException(
                    KeyChallengeResponse, ApiError.Forbidden(id.toString(), "installation_id")
                )
                // already assigned to another account
                if (property.accountId != null) throw ValidationException(
                    KeyChallengeResponse, ApiError.Forbidden(id.toString(), "account_id")
                )
                if (property.valid) {
                    properties.add(property)
                    return@forEach
                }
                val code = split[1]
                if (property.verificationCode != code) throw ValidationException(
                    KeyChallengeResponse, ApiError.Forbidden(code, "code")
                )
                // save validated property
                // don't own yet (outside of tx)
                property = accountPropertiesService.validateProperty(property.id)
                    ?: throw ValidationException(
                        KeyChallengeResponse, ApiError.Reference(id.toString())
                    )
                properties.add(property)
            }
            /*if (properties.none { it.type == ApiAccountProperty.Type.PhoneNo }) {
                throw ValidationException1(
                    KeyChallengeResponse, ErrorStatus.Required(category = ApiAccountProperty.Type.PhoneNo.name)
                )
            }*/

            val account =
                createAccount(UUID.randomUUID().toString(), req.name, req.secret, properties)

            val secretUpdate = accountsService.getLatestSecretUpdate(account.id, account.secret)
            if (secretUpdate == null) {
                log.error("post - failed to find SecretUpdate, account=${account.id}")
                throw ValidationException("secret_update", ApiError.Internal())
            }

            // does not need to run in the same tx, but it'd be nice
            val link = installationsService.linkInstallation(account.id, installationId)
            val accessToken = genAccessToken(account.id, link.id, installationId, account.handle)
            val dbAuthSession = authSessionsService.create(
                account.id, installationId, link.id, secretUpdate.id.value
            )
            val authSession = dbAuthSession.toAuthSession(accessToken)

            call.respond(
                HttpStatusCode.Created,
                HintedApiSuccessResponse(
                    data = account,
                    hints = AccountHints(properties = properties, session = authSession)
                )
            )
        }
        authenticate("auth-jwt") {
            get {
                val accounts = accountsService.all()
                call.respond(ApiSuccessResponse(accounts.size, accounts))
            }
            get("{id}") {
                val id = UUID.fromString(call.parameters["id"])
                val account =
                    accountsService.get(id) ?: throw ValidationException("id", ApiError.Required())
                call.respond(HttpStatusCode.OK, ApiSuccessResponse(data = account))
            }
            // TODO protect, only self or admin...
            // delete is status-only
            delete("{id}") {
                val id = UUID.fromString(call.parameters["id"])
                if (accountsService.delete(id)) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.UnprocessableEntity)
                }
            }
            get("by-handle/{handle}") {
                val handle = call.parameters["handle"] ?: throw ValidationException(
                    "handle", ApiError.Required()
                )
                val account = accountsService.getByHandle(handle) ?: throw ValidationException(
                    "handle", ApiError.Reference(value = handle)
                )
                call.respond(HttpStatusCode.OK, ApiSuccessResponse(data = account))
            }
            route("links") {
                post {
                    val principal = call.principal<JWTPrincipal>()!!
                    val handle = principal.payload.getClaim("handle").asString()
                    val selfId =
                        UUID.fromString(principal.payload.getClaim("account_id").asString())
                    log.info("post - handle: $handle, self-id: $selfId")

                    val req = call.receive<AccountLinkParams>()

                    val linkAccountId = req.accountId ?: selfId
                    val link = accountLinksService.create(linkAccountId, req.peerId)
                    call.respond(HttpStatusCode.Created, ApiSuccessResponse(data = link))
                }
                get {
                    val principal = call.principal<JWTPrincipal>()!!
                    val handle = principal.payload.getClaim("handle").asString()
                    val selfId =
                        UUID.fromString(principal.payload.getClaim("account_id").asString())
                    log.info("get - handle: $handle, self-id: $selfId")

                    val links = accountLinksService.listByAccount(selfId)
                    call.respond(ApiSuccessResponse(links.size, links))
                }
            }
            get("{id}/installations") {
                val accountId = UUID.fromString(call.parameters["id"])

                val peerInstallations = installationsService.listLinks(accountId)
                call.respond(ApiSuccessResponse(peerInstallations.size, peerInstallations))
            }
        }
        route("properties") {
            post {
                val installationId = UUID.fromString(call.request.headerOrFail(KeyInstallationID))
                val req = call.receive<AccountPropertyParams>().sanitize()
                val type = req.type
                    ?: if (req.content.contains('@')) ApiAccountProperty.Type.Email else ApiAccountProperty.Type.PhoneNo

                // TODO validate phone no
                // NOTE: content is only unique among primary properties
                // since it's not primary during creating, verify for conflict manually
                if (req.scope == AccountPropertyParams.Scope.Signup) {
                    if (accountPropertiesService.getPrimaryByContent(req.content) != null) {
                        log.warn("properties/post - value already exists as primary: ${req.content}")
                        throw ValidationException(
                            "content", ApiError.Conflict(req.content, type.name)
                        )
                    }
                }

                val accountProperty =
                    accountPropertiesService.create(installationId, type, req.content)
                call.respond(HttpStatusCode.Created, ApiSuccessResponse(data = accountProperty))
            }
        }
    }
}

suspend fun createAccount(
    handle: String, name: String, secret: String, properties: MutableList<ApiAccountProperty>
): ApiAccount {
    //if (handle.length < 3 || handle.length > HandleMaxLength) throw ValidationException(Code.Constraint, "handle")
    if (name.isEmpty() || name.length > 50)
        throw ValidationException("name", ApiError.Constraint(name, min = 1, max = 50))
    if (secret.length < MinSecretSize)
        throw ValidationException(
            "secret", ApiError.Constraint(secret, min = MinSecretSize.toLong())
        )

    // TODO properties: possibly multiple of the same type, only primarize one
    return accountsService.create(handle, name, secret, properties)
}

fun AccountParams.sanitize() = copy(name.sanitizeName(), secret.sanitizeSecret())

fun SignupParams.sanitize() = copy(name.sanitizeName(), secret.sanitizeSecret(), email.trim())

fun AccountPropertyParams.sanitize() = copy(content.trim(), type, scope)
