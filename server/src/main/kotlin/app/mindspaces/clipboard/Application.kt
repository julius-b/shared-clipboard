package app.mindspaces.clipboard

import app.mindspaces.clipboard.plugins.configureAdministration
import app.mindspaces.clipboard.plugins.configureAuthentication
import app.mindspaces.clipboard.plugins.configureDatabases
import app.mindspaces.clipboard.plugins.configureRouting
import app.mindspaces.clipboard.plugins.configureSerialization
import app.mindspaces.clipboard.plugins.configureSockets
import app.mindspaces.clipboard.plugins.configureStartup
import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    configureAdministration()
    configureDatabases()
    configureStartup()
    configureSerialization()
    configureAuthentication()
    configureSockets()
    configureRouting()
}
