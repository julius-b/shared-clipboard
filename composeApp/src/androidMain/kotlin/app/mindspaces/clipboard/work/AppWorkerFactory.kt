package app.mindspaces.clipboard.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import me.tatarka.inject.annotations.Inject
import kotlin.reflect.KClass

@Inject
class AppWorkerFactory(
    private val workersMap: Map<KClass<out ListenableWorker>, (Context, WorkerParameters) -> ListenableWorker>
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        val entry = workersMap.entries.find {
            Class.forName(workerClassName).isAssignableFrom(it.key.java)
        }
        val workerCreator = entry?.value ?: return null
        return workerCreator(appContext, workerParameters)
    }
}
