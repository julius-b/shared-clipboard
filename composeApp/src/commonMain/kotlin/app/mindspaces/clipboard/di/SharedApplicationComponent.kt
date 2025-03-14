package app.mindspaces.clipboard.di

import app.mindspaces.clipboard.ClipboardApp
import app.mindspaces.clipboard.api.newHttpClient
import app.mindspaces.clipboard.data.PlatformIO
import app.mindspaces.clipboard.db.Database
import app.mindspaces.clipboard.db.DriverFactory
import app.mindspaces.clipboard.db.cleanup
import app.mindspaces.clipboard.db.createDatabase
import app.mindspaces.clipboard.repo.AuthRepository
import app.mindspaces.clipboard.repo.InstallationRepository
import app.mindspaces.clipboard.repo.MediaRepository
import app.mindspaces.clipboard.repo.NoteRepository
import ca.gosyer.appdirs.AppDirs
import co.touchlab.kermit.Logger
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.ui.Ui
import io.ktor.client.HttpClient
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
interface SharedApplicationComponent {
    val driverFactory: DriverFactory
    val db: Database

    val httpClient: HttpClient
    val installationRepository: InstallationRepository
    val authRepository: AuthRepository
    val noteRepository: NoteRepository
    val mediaRepository: MediaRepository

    val presenterFactories: Set<Presenter.Factory>
    val uiFactories: Set<Ui.Factory>
    val circuit: Circuit

    // NOTE: SiteData not user-accessible on Linux
    val appDirs: AppDirs
    val platformIO: PlatformIO
    val clipboardApp: ClipboardApp

    @Provides
    @SingleIn(AppScope::class)
    fun provideHttpClient(): HttpClient = newHttpClient(db) {
        val log = Logger.withTag("shared")
        log.i { "initiating logout..." }
        cleanup(db)
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppDirs() = AppDirs("AccessAnywhere")

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabase() = createDatabase(driverFactory)

    @Provides
    @SingleIn(AppScope::class)
    fun circuit(presenterFactories: Set<Presenter.Factory>, uiFactories: Set<Ui.Factory>): Circuit {
        return Circuit.Builder()
            .addPresenterFactories(presenterFactories)
            .addUiFactories(uiFactories)
            .build()
    }
}
