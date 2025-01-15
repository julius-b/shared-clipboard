package app.mindspaces.clipboard.plugins

import app.mindspaces.clipboard.services.AccountLinks
import app.mindspaces.clipboard.services.AccountProperties
import app.mindspaces.clipboard.services.Accounts
import app.mindspaces.clipboard.services.AuthSessions
import app.mindspaces.clipboard.services.InstallationLinks
import app.mindspaces.clipboard.services.Installations
import app.mindspaces.clipboard.services.MediaReceipts
import app.mindspaces.clipboard.services.Medias
import app.mindspaces.clipboard.services.SecretUpdates
import io.ktor.server.config.ApplicationConfig
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseSingleton {
    private val LOGGER = KtorSimpleLogger("DB")

    fun init(config: ApplicationConfig) {
        val driver = config.property("storage.driver").getString()
        val url = config.property("storage.url").getString()

        LOGGER.info("driver: $driver, url: $url")
        val database = Database.connect(url, driver)
        transaction(database) {
            SchemaUtils.create(Installations)
            SchemaUtils.create(Accounts)
            SchemaUtils.create(InstallationLinks)
            SchemaUtils.create(AccountProperties)
            SchemaUtils.create(AccountLinks)
            SchemaUtils.create(SecretUpdates)
            SchemaUtils.create(AuthSessions)
            SchemaUtils.create(Medias)
            SchemaUtils.create(MediaReceipts)
        }
    }

    suspend fun <T> tx(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
