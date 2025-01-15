package app.mindspaces.clipboard

import app.mindspaces.clipboard.api.ApiInstallation.Platform

// TODO System.getProperty("java.version")
actual fun getPlatform(): Platform {
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("win") -> Platform.Windows
        os.contains("nix") || os.contains("nux") || os.contains("aix") -> Platform.Linux
        os.contains("mac") -> Platform.MacOS
        else -> Platform.Desktop
    }
}
