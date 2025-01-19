package app.mindspaces.clipboard.plugins

import app.mindspaces.clipboard.api.ApiError
import app.mindspaces.clipboard.api.ApiErrorResponse
import app.mindspaces.clipboard.routes.ValidationException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.ShutDownUrl
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.util.logging.KtorSimpleLogger
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException

fun Application.configureAdministration() {
    val log = KtorSimpleLogger("admin")

    install(ShutDownUrl.ApplicationCallPlugin) {
        shutDownUrl = "/admin/shutdown"
        exitCodeSupplier = { 0 }
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (cause) {
                // TODO provide get without ?, will return default resp
                // TODO don't know field, suboptimal response
                is EntityNotFoundException -> {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ApiErrorResponse(mapOf("entity" to arrayOf(ApiError.Reference())))
                    )
                }

                is ValidationException -> {
                    val status =
                        if (cause.errors.any { it is ApiError.Conflict }) HttpStatusCode.Conflict
                        else if (cause.errors.any { it is ApiError.Unauthenticated }) HttpStatusCode.Unauthorized
                        else if (cause.errors.any { it is ApiError.Forbidden }) HttpStatusCode.Forbidden
                        else HttpStatusCode.UnprocessableEntity
                    call.respond(status, ApiErrorResponse(mapOf(cause.field to cause.errors)))
                }

                else -> {
                    log.info("uncaught exception:", cause)

                    // TODO disable for prod :)
                    // eg:500: io.ktor.server.plugins.BadRequestException: Failed to convert request body to class app.opia.routes.CreateMessage
                    call.respondText(
                        text = "500: $cause", status = HttpStatusCode.InternalServerError
                    )
                }
            }
        }
    }
}
