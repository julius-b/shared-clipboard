package app.mindspaces.clipboard.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/*fun requestManageExternalStoragePermission(activity: ComponentActivity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // Check if permission is already granted
        if (Environment.isExternalStorageManager()) {
            Toast.makeText(activity, "All Files Access permission already granted", Toast.LENGTH_SHORT).show()
        } else {
            // Direct user to the "All Files Access" settings screen
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${activity.packageName}")
                activity.startActivityForResult(intent, 1001)
            } catch (e: Throwable) {
                // Fallback for devices that don't support the direct app settings intent
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                activity.startActivity(intent)
            }
        }
    } else {
        // For Android versions below 11, request normal storage permissions
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
            1002
        )
    }
}*/

@Composable
actual fun hasStoragePermission(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        return Environment.isExternalStorageManager()
    } else {
        val context = LocalContext.current
        val isGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        )
        return isGranted == PackageManager.PERMISSION_GRANTED;
    }
}

fun hasStoragePermissionAndroid(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        return Environment.isExternalStorageManager()
    } else {
        val isGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        )
        return isGranted == PackageManager.PERMISSION_GRANTED;
    }
}
