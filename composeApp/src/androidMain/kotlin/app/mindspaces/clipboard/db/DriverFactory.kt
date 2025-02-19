package app.mindspaces.clipboard.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.mindspaces.clipboard.di.AppContext
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@Inject
actual class DriverFactory(@AppContext private val context: Context) {

    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(
            Database.Schema, context, "access-anywhere-main.db", useNoBackupDirectory = true
        )
    }
}
