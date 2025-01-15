package app.mindspaces.clipboard.plugins

import app.mindspaces.clipboard.api.ApiError
import app.mindspaces.clipboard.api.ApiErrorResponse
import app.mindspaces.clipboard.routes.accountsApi
import app.mindspaces.clipboard.routes.authSessionsApi
import app.mindspaces.clipboard.routes.installationsApi
import app.mindspaces.clipboard.routes.mediasApi
import io.ktor.resources.Resource
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.resources.Resources
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable
@Resource("/notes")
class Notes(val sort: String? = "new")

fun Application.configureRouting() {
    install(Resources)
    routing {
        get("/") {
            call.respondText("Hello World!")
        }
        get<Notes> { note ->
            call.respond("List of notes sorted starting from ${note.sort}")
        }
        get("test") {
            call.respond(ApiErrorResponse(ApiError.Conflict()))
        }
        route("api") {
            route("v1") {
                installationsApi()
                accountsApi()
                authSessionsApi()
                mediasApi()
            }
        }
    }
}
