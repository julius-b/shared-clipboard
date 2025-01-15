package app.mindspaces.clipboard.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.mindspaces.clipboard.data.RealTimeClient
import app.mindspaces.clipboard.data.listAllMedia
import app.mindspaces.clipboard.repo.AuthRepository
import app.mindspaces.clipboard.repo.InstallationRepository
import app.mindspaces.clipboard.repo.MediaRepository
import ca.gosyer.appdirs.AppDirs
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject

@Inject
class SyncWorker(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    client: HttpClient,
    authRepository: AuthRepository,
    installationRepository: InstallationRepository,
    private val mediaRepository: MediaRepository,
    private val appDirs: AppDirs
) : CoroutineWorker(appContext, workerParams) {
    private val log = Logger.withTag("SyncWorker")

    private val rtClient =
        RealTimeClient(client, installationRepository, authRepository, mediaRepository)

    // TODO move some contents of this method to a common file
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        log.i { "starting..." }
        log.i { "mediaRepository: $mediaRepository" }

        launch {
            // TODO TODO listen for changes in MediaStore
            log.i { "stage 'media query': launching..." }
            listAllMedia(mediaRepository, applicationContext.contentResolver)
            log.i { "stage 'media query': done" }
        }

        launch {
            log.i { "stage 'sync thumbnails': launching..." }
            //syncThumbnails(mediaRepository, applicationContext.contentResolver, appDirs)
            log.i { "stage 'sync thumbnails': done" }
        }

        launch {
            log.i { "stage 'rt-client': launching..." }
            rtClient.connect()
            log.i { "stage 'rt-client': done" }
        }

        launch {
            mediaRepository.allRequests().collect { requests ->
                log.i { "active media requests (${requests.size}):" }
                for (req in requests) {
                    log.i { "- $req" }
                }
            }
        }

        // never returns
        mediaRepository.handleRequests()

        //val rootDir = Environment.getExternalStorageDirectory()
        //traverseFiles(rootDir)

        return@withContext Result.success()
    }
}
