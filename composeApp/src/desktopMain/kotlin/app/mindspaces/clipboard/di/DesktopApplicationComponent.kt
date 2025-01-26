package app.mindspaces.clipboard.di

import app.mindspaces.clipboard.ClipboardApp
import app.mindspaces.clipboard.db.DriverFactory
import co.touchlab.kermit.Logger
import coil3.PlatformContext
import me.tatarka.inject.annotations.Component
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@Component
@MergeComponent(AppScope::class)
@SingleIn(AppScope::class)
abstract class DesktopApplicationComponent : SharedApplicationComponent,
    DesktopApplicationComponentMerged {

    init {
        Logger.withTag("AppComponent").d { "init" }
    }

    abstract val clipboardApp: ClipboardApp

    override fun getDriverFactory() = DriverFactory()

    override fun providePlatformContext(): PlatformContext = PlatformContext.INSTANCE

    companion object
}
