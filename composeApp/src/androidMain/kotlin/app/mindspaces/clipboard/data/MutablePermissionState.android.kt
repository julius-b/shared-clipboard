package app.mindspaces.clipboard.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import co.touchlab.kermit.Logger

// src: https://github.com/google/accompanist/tree/main/permissions/src/main/java/com/google/accompanist/permissions
// adapted to support "all files access", multiplatform

/**
 * Creates a [MutablePermissionState] that is remembered across compositions.
 *
 * It's recommended that apps exercise the permissions workflow as described in the
 * [documentation](https://developer.android.com/training/permissions/requesting#workflow_for_requesting_permissions).
 *
 * @param permission the permission to control and observe.
 * @param onPermissionResult will be called with whether or not the user granted the permission
 *  after [PermissionState.launchPermissionRequest] is called.
 */
@Composable
actual fun rememberMutablePermissionState(
    permission: Permission,
    onPermissionResult: (Boolean) -> Unit
): MutablePermissionState {
    val context = LocalContext.current
    val permissionState = remember(permission) {
        MutablePermissionState(permission, context, context.findActivity())
    }

    // Refresh the permission status when the lifecycle is resumed
    PermissionLifecycleCheckerEffect(permissionState)

    // Remember RequestPermission launcher and assign it to permissionState
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            permissionState.refreshPermissionStatus()
            onPermissionResult(it)
        }
    val managePermissionLauncher =
        if (permission == Permission.Storage && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                val isGranted = Environment.isExternalStorageManager()
                permissionState.refreshPermissionStatus()
                onPermissionResult(isGranted)
            } else null
    DisposableEffect(permissionState, permissionLauncher) {
        permissionState.launcher = permissionLauncher
        permissionState.activityLauncher = managePermissionLauncher
        onDispose {
            permissionState.launcher = null
            permissionState.activityLauncher = null
        }
    }

    return permissionState
}

/**
 * A mutable state object that can be used to control and observe permission status changes.
 *
 * In most cases, this will be created via [rememberMutablePermissionState].
 *
 * @param permission the permission to control and observe.
 * @param context to check the status of the [permission].
 * @param activity to check if the user should be presented with a rationale for [permission].
 */
actual class MutablePermissionState(
    override val permission: Permission,
    private val context: Context,
    private val activity: Activity
) : PermissionState {

    private val log = Logger.withTag("PermissionState")

    private val androidPermission = permission.toAndroidPermission()

    override var status: PermissionStatus by mutableStateOf(getPermissionStatus())

    override fun launchPermissionRequest() {
        // For Android 11+ (API 30), request MANAGE_EXTERNAL_STORAGE
        if (permission == Permission.Storage && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activityLauncher?.let {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:${context.packageName}")
                    it.launch(intent)
                } catch (e: Throwable) {
                    log.w { "launchPermissionRequest - launching fallback intent..." }
                    // Fallback for devices that don't support the direct app settings intent
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    it.launch(intent)
                }
            } ?: throw IllegalStateException("ActivityResultLauncher-activity cannot be null")
        } else {
            // For Android 10 and below, request regular storage permission
            launcher?.launch(androidPermission)
                ?: throw IllegalStateException("ActivityResultLauncher cannot be null")
        }
    }

    internal var launcher: ActivityResultLauncher<String>? = null
    internal var activityLauncher: ActivityResultLauncher<Intent>? = null

    internal fun refreshPermissionStatus() {
        status = getPermissionStatus()
    }

    private fun getPermissionStatus(): PermissionStatus {
        val hasPermission =
            if (permission == Permission.Storage && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                Environment.isExternalStorageManager()
            else context.checkPermission(androidPermission)
        return if (hasPermission) {
            PermissionStatus.Granted
        } else {
            PermissionStatus.Denied(activity.shouldShowRationale(androidPermission))
        }
    }
}
