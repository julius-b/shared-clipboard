package app.mindspaces.clipboard.repo

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.mindspaces.clipboard.db.Database
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers

class AccountRepository(db: Database, private val client: HttpClient) {
    private val log = Logger.withTag("AccountRepo")

    private val accountQueries = db.accountQueries

    fun getSelf() = accountQueries.getSelf().asFlow().mapToOneOrNull(Dispatchers.IO)
}
