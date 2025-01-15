package app.mindspaces.clipboard.routes

import app.mindspaces.clipboard.api.ApiError
import io.ktor.server.request.ApplicationRequest

fun ApplicationRequest.headerOrFail(name: String): String =
    headers[name] ?: throw ValidationException(name, ApiError.Required())

class ValidationException(val field: String, vararg val errors: ApiError) : Exception() {
    override val message: String
        get() = "${this.field}: ${errors.contentToString()}"
}

class SimpleValidationException(val error: ApiError) : Exception() {
    override val message: String
        get() = "$error"
}
