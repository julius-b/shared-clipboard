package app.mindspaces.clipboard.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DriverFactory(private val context: Context) {

    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(
            Database.Schema, context, "access-anywhere-main.db", useNoBackupDirectory = true
        )
    }
}
