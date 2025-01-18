package app.mindspaces.clipboard.data

// src: https://github.com/google/accompanist/tree/main/permissions/src/main/java/com/google/accompanist/permissions

/**
 * Model of the status of a permission. It can be granted or denied.
 * If denied, the user might need to be presented with a rationale.
 */
sealed interface PermissionStatus {
    data object Granted : PermissionStatus
    data class Denied(
        val shouldShowRationale: Boolean
    ) : PermissionStatus
}

/**
 * `true` if the permission is granted.
 */
val PermissionStatus.isGranted: Boolean
    get() = this == PermissionStatus.Granted

/**
 * `true` if a rationale should be presented to the user.
 */
val PermissionStatus.shouldShowRationale: Boolean
    get() = when (this) {
        PermissionStatus.Granted -> false
        is PermissionStatus.Denied -> shouldShowRationale
    }
