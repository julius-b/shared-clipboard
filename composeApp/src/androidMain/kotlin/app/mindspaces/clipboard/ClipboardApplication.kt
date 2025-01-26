package app.mindspaces.clipboard

import android.app.Application
import androidx.work.Configuration
import app.mindspaces.clipboard.di.AndroidApplicationComponent
import app.mindspaces.clipboard.di.create
import ca.gosyer.appdirs.impl.attachAppDirs
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader

class ClipboardApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    val component: AndroidApplicationComponent by lazy {
        AndroidApplicationComponent.create(this)
    }
    //val component = AndroidApplicationComponent::class.create(this)

    init {
        // TODO possible race with component lazy access?
        attachAppDirs()
        // force init InstallationRepo, it doesn't happen otherwise
        println("init: ${component.installationRepository}")
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(component.workerFactory)
            .build()

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return component.imageLoader
    }
}
