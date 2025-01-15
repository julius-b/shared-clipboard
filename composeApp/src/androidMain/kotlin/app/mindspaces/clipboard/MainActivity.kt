package app.mindspaces.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.tooling.preview.Preview
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.mindspaces.clipboard.data.hasStoragePermissionAndroid
import app.mindspaces.clipboard.di.AndroidApplicationComponent
import app.mindspaces.clipboard.work.SyncWorker
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val log = Logger.withTag("MainActivity")

    private lateinit var manageStoragePermissionLauncher: ActivityResultLauncher<Intent>
    private lateinit var storagePermissionLauncher: ActivityResultLauncher<String>

    private lateinit var applicationComponent: AndroidApplicationComponent

    private val isStoragePermissionGranted = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applicationComponent = (applicationContext as ClipboardApplication).component

        scheduleSyncWorker()

        log.i { "perm: ${applicationComponent.sharedState.isStoragePermissionGranted} / ${applicationComponent.sharedState.isStoragePermissionGranted.state}" }
        log.i { "mediaRepository: ${applicationComponent.mediaRepository}" }
        //log.i { "--- ${applicationComponent.isStoragePermissionGranted()}" }

        // set current value before callbacks can trigger
        // TODO when permission is revoked while the app is running, it's probably not recognized
        val hasStoragePermissionAndroid = hasStoragePermissionAndroid(this)
        log.i { "hasStoragePermissionAndroid: $hasStoragePermissionAndroid" }
        //applicationComponent.isStoragePermissionGranted().value = hasStoragePermissionAndroid
        //applicationComponent.isStoragePermissionGranted().updateState(hasStoragePermissionAndroid)
        applicationComponent.sharedState.isStoragePermissionGranted.updateState(
            hasStoragePermissionAndroid
        )

        manageStoragePermissionLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val isGranted = Environment.isExternalStorageManager()
                    println("manageStoragePermissionLauncher got result: $isGranted")
                    //applicationComponent.isStoragePermissionGranted().value = isGranted
                    //applicationComponent.isStoragePermissionGranted().updateState(isGranted)
                    applicationComponent.sharedState.isStoragePermissionGranted.updateState(
                        isGranted
                    )
                    if (isGranted) {
                        Toast.makeText(this, "All files access granted", Toast.LENGTH_SHORT).show()
                        scheduleSyncWorker()
                    } else {
                        Toast.makeText(this, "All files access denied", Toast.LENGTH_SHORT).show()
                    }
                }
            }

        storagePermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                println("storagePermissionLauncher got result: $isGranted")
                //applicationComponent.isStoragePermissionGranted().value = isGranted
                //applicationComponent.isStoragePermissionGranted().updateState(isGranted)
                applicationComponent.sharedState.isStoragePermissionGranted.updateState(isGranted)
                if (isGranted) {
                    Toast.makeText(this, "Storage permission granted", Toast.LENGTH_SHORT).show()
                    scheduleSyncWorker()
                } else {
                    Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
                }
            }

        val sharedMessage = handleShareIntent(intent)

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
            // TODO Lifecyclething
            LaunchedEffect(Unit) {
                applicationComponent.sharedState.requestStoragePermission.state.collect {
                    requestStoragePermission()
                }
            }

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

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11+ (API 30), request MANAGE_EXTERNAL_STORAGE
            if (!Environment.isExternalStorageManager()) {
                log.i { "requestStoragePermission - launching intent..." }
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    manageStoragePermissionLauncher.launch(intent)
                } catch (e: Throwable) {
                    log.w { "requestStoragePermission - launching fallback intent..." }
                    // Fallback for devices that don't support the direct app settings intent
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    manageStoragePermissionLauncher.launch(intent)
                }
            } else {
                // no ActivityResult is sent when already granted
                //applicationComponent.isStoragePermissionGranted().value = true
                //applicationComponent.isStoragePermissionGranted().updateState(true)
                applicationComponent.sharedState.isStoragePermissionGranted.updateState(true)
                Toast.makeText(this, "All Files Access permission granted", Toast.LENGTH_SHORT)
                    .show()
            }
        } else {
            // TODO maybe query to update current state first
            // For Android 10 and below, request regular storage permission
            storagePermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun handleShareIntent(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        log.i { "intent: text=$text" }

        runBlocking {
            // no effect
            //launch {
            log.i { "intent: emitting..." }
            text?.let {
                //applicationComponent.getShareFlow().emit(it)
                applicationComponent.sharedState.share.updateState(it)
            }
            log.i { "intent: emit done" }
            //}
        }
        return text
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    // DriverFactory(LocalContext.current)
    //App()
}
