package app.mindspaces.clipboard.di

// src: https://github.com/simonlebras/rickandmorty/blob/main/core/coil-logger/src/commonMain/kotlin/app/rickandmorty/core/coil/logger/CoilLoggerComponent.kt

import app.mindspaces.clipboard.data.ThumbFetcher
import app.mindspaces.clipboard.db.Media
import ca.gosyer.appdirs.AppDirs
import co.touchlab.kermit.Severity
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.fetch.Fetcher
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.util.Logger
import coil3.util.Logger.Level
import io.ktor.client.HttpClient
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import co.touchlab.kermit.Logger as KermitLogger
import coil3.util.Logger as CoilLogger

private const val CoilLoaderTag = "CoilLoader"

@ContributesTo(AppScope::class)
interface ImageLoaderComponent {

    val imageLoader: ImageLoader

    @Provides
    @SingleIn(AppScope::class)
    fun provideImageLoader(
        context: PlatformContext,
        httpClient: () -> HttpClient,
        appDirs: AppDirs,
        logger: Logger,
    ): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(KtorNetworkFetcherFactory(httpClient))
            add(Fetcher.Factory<Media> { data, options, imageLoader ->
                ThumbFetcher(data, appDirs)
            })
        }
        .logger(logger)
        .build()

    @Provides
    @SingleIn(AppScope::class)
    fun providePlatformContext(): PlatformContext

    @Provides
    @SingleIn(AppScope::class)
    fun provideLogger(): CoilLogger = KermitLogger.asCoilLogger()
}

fun KermitLogger.asCoilLogger(): CoilLogger = object : CoilLogger {
    override var minLevel: Level = Level.Debug

    override fun log(tag: String, level: Level, message: String?, throwable: Throwable?) {
        this@asCoilLogger.log(level.toSeverity(), CoilLoaderTag, throwable, message.orEmpty())
    }
}

private fun Level.toSeverity(): Severity = when (this) {
    Level.Verbose -> Severity.Verbose
    Level.Debug -> Severity.Debug
    Level.Info -> Severity.Info
    Level.Warn -> Severity.Warn
    Level.Error -> Severity.Error
}
