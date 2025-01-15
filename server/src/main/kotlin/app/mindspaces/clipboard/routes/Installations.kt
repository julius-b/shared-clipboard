package app.mindspaces.clipboard.routes

import app.mindspaces.clipboard.api.ApiSuccessResponse
import app.mindspaces.clipboard.api.InstallationParams
import app.mindspaces.clipboard.services.installationsService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.util.logging.KtorSimpleLogger
import java.util.UUID

fun Route.installationsApi() {
    val log = KtorSimpleLogger("installations-api")

    route("installations") {
        put {
            val req = call.receive<InstallationParams>()
            val installation =
                installationsService.create(req.id, req.name, req.desc, req.os, req.client)
            call.respond(HttpStatusCode.Created, ApiSuccessResponse(data = installation))
        }
        get {
            val installations = installationsService.all()
            call.respond(ApiSuccessResponse(count = installations.size, data = installations))
        }
        get("{id}") {
            val id = UUID.fromString(call.parameters["id"])
            val installation = installationsService.get(id)
            if (installation != null) {
                call.respond(HttpStatusCode.OK, ApiSuccessResponse(data = installation))
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
        delete("{id}") {
            val id = UUID.fromString(call.parameters["id"])
            if (installationsService.delete(id)) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.UnprocessableEntity)
            }
        }
    }
}
