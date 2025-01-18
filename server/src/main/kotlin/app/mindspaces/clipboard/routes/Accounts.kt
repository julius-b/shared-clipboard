package app.mindspaces.clipboard.routes

import app.mindspaces.clipboard.api.AccountHints
import app.mindspaces.clipboard.api.AccountLinkParams
import app.mindspaces.clipboard.api.AccountParams
import app.mindspaces.clipboard.api.AccountPropertyParams
import app.mindspaces.clipboard.api.ApiAccountProperty
import app.mindspaces.clipboard.api.ApiError
import app.mindspaces.clipboard.api.ApiSuccessResponse
import app.mindspaces.clipboard.api.HintedApiSuccessResponse
import app.mindspaces.clipboard.api.KeyChallengeResponse
import app.mindspaces.clipboard.api.KeyInstallationID
import app.mindspaces.clipboard.services.accountLinksService
import app.mindspaces.clipboard.services.accountPropertiesService
import app.mindspaces.clipboard.services.accountsService
import app.mindspaces.clipboard.services.installationsService
import app.mindspaces.clipboard.services.sanitizeSecret
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
        // TODO validate & create account in one transaction
        // tx not necessary, updating Valid is fine to do multiple times
        post {
            val installationId = UUID.fromString(call.request.headerOrFail(KeyInstallationID))
            val req = call.receive<AccountParams>()

            // used for account paths at a later point
            //val handle = req.handle.trim()
            val handle = UUID.randomUUID().toString()
            val name = req.name.trim()
            val secret = req.secret.sanitizeSecret()

            // TODO do all constraint checks based on definition of Length/min/max, etc. then return list of all ErrorStatus
            // TODO Constraint -> Schema? Layout? sent length constraints?
            // UUID default: 36
            //if (handle.length < 3 || handle.length > handleWidth) throw ValidationException(Code.Constraint, "handle")
            if (name.isEmpty() || name.length > 50)
                throw ValidationException("name", ApiError.Size(min = 1, max = 50, value = name))
            if (secret.length < 8)
                throw ValidationException("secret", ApiError.Size(min = 8, value = secret))

            val responses =
                call.request.headers.getAll(KeyChallengeResponse) ?: throw ValidationException(
                    KeyChallengeResponse, ApiError.Required()
                )
            val properties = mutableListOf<ApiAccountProperty>()
            // NOTE: multiple values for one key might be joined by commas or semicolons
            responses.map { it.split(';', ',') }.flatten().forEach { resp ->
                val split = resp.split("=")
                if (split.size != 2) {
                    log.warn("accounts/post - invalid challenge-response: $resp ($split)")
                    // ref unknown
                    throw ValidationException(
                        KeyChallengeResponse,
                        ApiError.Schema(schema = "<uuid>=<code>", value = resp)
                    )
                }
                val id = UUID.fromString(split[0])
                var property = accountPropertiesService.get(id) ?: throw ValidationException(
                    KeyChallengeResponse, ApiError.Reference(ref = id.toString())
                )
                if (property.installationId != installationId) throw ValidationException(
                    KeyChallengeResponse,
                    ApiError.Forbidden(
                        auth = KeyInstallationID,
                        ref = id.toString(),
                        value = installationId.toString()
                    )
                )
                if (property.valid) {
                    properties.add(property)
                    return@forEach
                }
                val code = split[1]
                if (property.verificationCode != code) throw ValidationException(
                    KeyChallengeResponse,
                    ApiError.Forbidden(auth = "code", ref = id.toString(), value = code)
                )
                // save validated property
                property = accountPropertiesService.validateProperty(property.id)
                    ?: throw ValidationException(
                        KeyChallengeResponse, ApiError.Reference(ref = id.toString())
                    )
                properties.add(property)
            }
            /*if (properties.none { it.type == ApiAccountProperty.Type.PhoneNo }) {
                throw ValidationException1(
                    KeyChallengeResponse, ErrorStatus.Required(category = ApiAccountProperty.Type.PhoneNo.name)
                )
            }*/

            val account = accountsService.create(handle, name, secret)
            for (i in 0 until properties.size) {
                // TODO possibly multiple of the same type, only primarize one
                val owned =
                    accountPropertiesService.ownAndPrimarizeProperty(properties[i].id, account.id)
                        ?: throw ValidationException(
                            KeyChallengeResponse,
                            ApiError.Reference(ref = properties[i].id.toString())
                        )
                properties[i] = owned
            }

            accountsService.createSecretUpdate(account.id, account.secret)
            call.respond(
                HttpStatusCode.Created,
                HintedApiSuccessResponse(
                    data = account, hints = AccountHints(properties = properties)
                )
            )
        }
        authenticate("auth-jwt") {
            get {
                val accounts = accountsService.all()
                call.respond(ApiSuccessResponse(count = accounts.size, data = accounts))
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

                    val createAccountLink = call.receive<AccountLinkParams>()

                    val linkAccountId = createAccountLink.accountId ?: selfId
                    val link = accountLinksService.create(linkAccountId, createAccountLink.peerId)
                    call.respond(HttpStatusCode.Created, ApiSuccessResponse(data = link))
                }
                get {
                    val principal = call.principal<JWTPrincipal>()!!
                    val handle = principal.payload.getClaim("handle").asString()
                    val selfId =
                        UUID.fromString(principal.payload.getClaim("account_id").asString())
                    log.info("get - handle: $handle, self-id: $selfId")

                    val links = accountLinksService.listByAccount(selfId)
                    call.respond(ApiSuccessResponse(count = links.size, data = links))
                }
            }

            get("{id}/installations") {
                val accountId = UUID.fromString(call.parameters["id"])

                val peerInstallations = installationsService.listLinks(accountId)
                call.respond(
                    HttpStatusCode.OK,
                    ApiSuccessResponse(count = peerInstallations.size, data = peerInstallations)
                )
            }
        }
        route("properties") {
            post {
                val installationId = UUID.fromString(call.request.headerOrFail(KeyInstallationID))
                val createAccountProperty = call.receive<AccountPropertyParams>()
                val content = createAccountProperty.content.trim()
                val type = createAccountProperty.type
                    ?: if (content.contains('@')) ApiAccountProperty.Type.Email else ApiAccountProperty.Type.PhoneNo

                // TODO validate phone no
                // NOTE: content is only unique among primary properties
                // since it's not primary during creating, verify for conflict manually
                if (createAccountProperty.scope == AccountPropertyParams.Scope.Signup) {
                    if (accountPropertiesService.getPrimaryByContent(content) != null) {
                        log.warn("properties/post - value already exists as primary: $content")
                        throw ValidationException(
                            "content", ApiError.Conflict(ref = type.name, value = content)
                        )
                    }
                }

                val accountProperty = accountPropertiesService.create(installationId, type, content)
                call.respond(HttpStatusCode.Created, ApiSuccessResponse(data = accountProperty))
            }
        }
    }
}
