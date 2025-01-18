package app.mindspaces.clipboard.data

import androidx.compose.runtime.Composable

@Composable
actual fun rememberMutablePermissionState(
    permission: Permission,
    onPermissionResult: (Boolean) -> Unit
): MutablePermissionState {
    return MutablePermissionState(permission, PermissionStatus.Granted)
}

actual class MutablePermissionState(
    override val permission: Permission,
    override var status: PermissionStatus
) : PermissionState {
    override fun launchPermissionRequest() {
        // NO-OP
    }
}
