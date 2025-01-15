package app.mindspaces.clipboard.di

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import app.mindspaces.clipboard.work.AppWorkerFactory
import app.mindspaces.clipboard.work.SyncWorker
import me.tatarka.inject.annotations.IntoMap
import me.tatarka.inject.annotations.Provides
import kotlin.reflect.KClass

interface WorkManagerComponent {

    @Provides
    @IntoMap
    fun provideSyncWorkerEntry(
        workerCreator: (appContext: Context, workerParams: WorkerParameters) -> SyncWorker,
    ): Pair<KClass<out ListenableWorker>, (Context, WorkerParameters) -> ListenableWorker> {
        return Pair(SyncWorker::class, workerCreator)
    }

    val AppWorkerFactory.provide: WorkerFactory
        @Provides get() = this

    @Provides
    fun provideWorkManager(context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }
}
