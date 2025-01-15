package app.mindspaces.clipboard.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import co.touchlab.kermit.Logger

actual class DriverFactory {
    private val log = Logger.withTag("DriverFactory")

    actual fun createDriver(): SqlDriver {
        log.d { "init" }
        //val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val driver: SqlDriver = JdbcSqliteDriver("jdbc:sqlite:/tmp/access-anywhere.db")
        Database.Schema.create(driver)
        return driver
    }
}
