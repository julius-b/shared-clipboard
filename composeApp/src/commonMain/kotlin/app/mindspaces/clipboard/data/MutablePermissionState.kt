package app.mindspaces.clipboard.data

import androidx.compose.runtime.Composable

// src: https://github.com/google/accompanist/tree/main/permissions/src/main/java/com/google/accompanist/permissions

@Composable
expect fun rememberMutablePermissionState(
    permission: Permission,
    onPermissionResult: (Boolean) -> Unit = {}
): MutablePermissionState

expect class MutablePermissionState : PermissionState
