package app.mindspaces.clipboard.di

import android.app.Application
import app.mindspaces.clipboard.ClipboardApp
import app.mindspaces.clipboard.db.DriverFactory
import app.mindspaces.clipboard.work.AppWorkerFactory
import coil3.PlatformContext
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@Component
@MergeComponent(AppScope::class)
@SingleIn(AppScope::class)
abstract class AndroidApplicationComponent(@get:Provides protected val application: Application) :
    SharedApplicationComponent, WorkManagerComponent, AndroidApplicationComponentMerged {

    abstract val clipboardApp: ClipboardApp
    abstract val workerFactory: AppWorkerFactory

    override fun getDriverFactory() = DriverFactory(application)

    override fun providePlatformContext(): PlatformContext = application.applicationContext

    companion object
}
