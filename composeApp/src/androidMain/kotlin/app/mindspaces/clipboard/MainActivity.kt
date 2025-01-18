package app.mindspaces.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.mindspaces.clipboard.data.Permission
import app.mindspaces.clipboard.data.isGranted
import app.mindspaces.clipboard.data.rememberPermissionState
import app.mindspaces.clipboard.di.AndroidApplicationComponent
import app.mindspaces.clipboard.work.SyncWorker
import co.touchlab.kermit.Logger
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val log = Logger.withTag("MainActivity")

    private val applicationComponent: AndroidApplicationComponent by lazy {
        (applicationContext as ClipboardApplication).component
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        scheduleSyncWorker()

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null) {
            println("clip items: #${clip.itemCount}, desc: ${clip.description}")
            for (i in 0..clip.itemCount) {
                val item = clip.getItemAt(0)
                println("clip[$i]: $item")
                println("- text: ${item.text}")
                println("- uri: ${item.uri}")
                // ':composeApp:lintDebug' Call requires API level 31 (current min is 24)
                //println("- textLinks: ${item.textLinks}")
                println("- intent: ${item.intent}")
                println("- htmlText: ${item.htmlText}")

                item.uri?.let { uri ->
                    //val uriMimeType: String? = cr.getType(uri)
                }
            }
        }

        setContent {
            val storagePermission = rememberPermissionState(Permission.Storage) { granted ->
                // TODO this callback does not work
                if (granted) {
                    Toast.makeText(this, "Storage permission granted", Toast.LENGTH_SHORT).show()
                    log.i { "permission-result - storage permission granted, launching sync-worker..." }
                    scheduleSyncWorker()
                } else {
                    log.w { "permission-result - storage permission denied" }
                    Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
                }
            }
            if (storagePermission.status.isGranted) {
                println("permission-result -  granted :)")
                scheduleSyncWorker()
            } else println("permission-result - not granted :)")

            applicationComponent.clipboardApp()
        }
    }

    // immediately launches the worker as well
    private fun scheduleSyncWorker() {
        log.i { "scheduling sync worker..." }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // the minimum interval allowed by WorkManager
        val periodicWorkRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            // querying files does not require network
            //.setConstraints(constraints)
            .build()

        // CANCEL_AND_REENQUEUE: easier for testing...
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "SyncJob",
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            periodicWorkRequest
        )
        log.i { "scheduling sync worker: enqueued" }
    }

    // eg after permission, kill to ensure tasks that are "out of work" are restarted, query MediaStore again, etc.
    /*private fun launchWorkerNow() {
        log.i { "launching sync worker..." }
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .build()
        // TODO don't run in parallel!
        WorkManager.getInstance(this).enqueue(req)
    }*/

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val text = handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        log.i { "intent: text=$text" }

        if (text == null) return null
        applicationComponent.noteRepository.saveClip(text)
        return text
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    // DriverFactory(LocalContext.current)
    //App()
}
