package app.mindspaces.clipboard.plugins

import io.ktor.server.application.Application

fun Application.configureDatabases() {
    DatabaseSingleton.init(environment.config)
}
