package app.mindspaces.clipboard

import app.mindspaces.clipboard.api.ApiInstallation.Platform

// TODO Build.VERSION.SDK_INT
actual fun getPlatform() = Platform.Android
