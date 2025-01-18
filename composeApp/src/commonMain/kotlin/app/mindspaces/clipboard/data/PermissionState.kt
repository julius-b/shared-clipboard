package app.mindspaces.clipboard.data

import androidx.compose.runtime.Composable

// src: https://github.com/google/accompanist/tree/main/permissions/src/main/java/com/google/accompanist/permissions

/**
 * Creates a [PermissionState] that is remembered across compositions.
 *
 * It's recommended that apps exercise the permissions workflow as described in the
 * [documentation](https://developer.android.com/training/permissions/requesting#workflow_for_requesting_permissions).
 *
 * @param permission the permission to control and observe.
 * @param onPermissionResult will be called with whether or not the user granted the permission
 *  after [PermissionState.launchPermissionRequest] is called.
 */
@Composable
fun rememberPermissionState(
    permission: Permission,
    onPermissionResult: (Boolean) -> Unit = {}
): PermissionState {
    return rememberPermissionState(permission, onPermissionResult, PermissionStatus.Granted)
}

/**
 * Creates a [PermissionState] that is remembered across compositions.
 *
 * It's recommended that apps exercise the permissions workflow as described in the
 * [documentation](https://developer.android.com/training/permissions/requesting#workflow_for_requesting_permissions).
 *
 * @param permission the permission to control and observe.
 * @param onPermissionResult will be called with whether or not the user granted the permission
 *  after [PermissionState.launchPermissionRequest] is called.
 * @param previewPermissionStatus provides a [PermissionStatus] when running in a preview.
 */
@Composable
fun rememberPermissionState(
    permission: Permission,
    onPermissionResult: (Boolean) -> Unit = {},
    previewPermissionStatus: PermissionStatus = PermissionStatus.Granted
): PermissionState {
    return when {
        //LocalInspectionMode.current -> PreviewPermissionState(permission, previewPermissionStatus)
        else -> rememberMutablePermissionState(permission, onPermissionResult)
    }
}

/**
 * A state object that can be hoisted to control and observe [permission] status changes.
 *
 * In most cases, this will be created via [rememberPermissionState].
 *
 * It's recommended that apps exercise the permissions workflow as described in the
 * [documentation](https://developer.android.com/training/permissions/requesting#workflow_for_requesting_permissions).
 */
interface PermissionState {

    /**
     * The permission to control and observe.
     */
    val permission: Permission

    /**
     * [permission]'s status
     */
    // TODO val
    var status: PermissionStatus

    /**
     * Request the [permission] to the user.
     *
     * This should always be triggered from non-composable scope, for example, from a side-effect
     * or a non-composable callback. Otherwise, this will result in an IllegalStateException.
     *
     * This triggers a system dialog that asks the user to grant or revoke the permission.
     * Note that this dialog might not appear on the screen if the user doesn't want to be asked
     * again or has denied the permission multiple times.
     * This behavior varies depending on the Android level API.
     */
    fun launchPermissionRequest()
}
