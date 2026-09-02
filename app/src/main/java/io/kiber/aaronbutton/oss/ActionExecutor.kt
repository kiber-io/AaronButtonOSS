package io.kiber.aaronbutton.oss

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import java.io.IOException
import java.util.Locale

internal class ActionExecutor(
    private val activity: ComponentActivity,
    private val preferences: SharedPreferences,
    private val isNfcTrigger: () -> Boolean,
    private val isWriting: () -> Boolean,
    private val isWriteBlocked: () -> Boolean,
    private val toast: (String) -> Unit
) {
    companion object {
        private const val TAG = "AaronButtonOSS"
        private const val CAMERA_REQUEST = 41
        private const val TERMUX_REQUEST = 42
        private const val TERMUX_PACKAGE = "com.termux"
        private const val TERMUX_PERMISSION = "com.termux.permission.RUN_COMMAND"
    }

    private var pendingCameraAction: String? = null
    private var pendingTermuxCommand: String? = null

    fun hasPendingPermission(): Boolean {
        return pendingCameraAction != null || pendingTermuxCommand != null
    }

    fun execute(action: String) {
        if (isWriting() || isWriteBlocked()) return
        val raw = action.trim()
        val normalized = raw.lowercase(Locale.ROOT)
        val logAction = when {
            normalized.startsWith("termux_") -> "termux_command"
            normalized.startsWith(CUSTOM_INTENT_PREFIX) -> "custom_intent"
            else -> raw
        }
        try {
            when {
                normalized == "flash_light" -> runWithCameraPermission(raw)
                normalized == "system_camera" -> activity.startActivity(
                    Intent("android.media.action.STILL_IMAGE_CAMERA")
                )
                normalized.startsWith("open_app_") -> openApp(raw.removePrefix("open_app_").trim())
                normalized.startsWith("open_link_") -> openLink(raw.removePrefix("open_link_").trim())
                normalized.startsWith("termux_") -> runTermuxCommand(raw.substring("termux_".length).trim())
                normalized.startsWith(CUSTOM_INTENT_PREFIX) -> runCustomIntent(
                    raw.removePrefix(CUSTOM_INTENT_PREFIX).trim()
                )
                normalized == "sound" -> toggleSound()
                normalized == "nfc_settings" -> activity.startActivity(
                    Intent(android.provider.Settings.ACTION_NFC_SETTINGS)
                )
                normalized == "location" -> activity.startActivity(
                    Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                )
                normalized == "airplane" -> activity.startActivity(
                    Intent(android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS)
                )
                else -> toast(activity.getString(R.string.unknown_action, raw))
            }
        } catch (e: Exception) {
            Log.e(TAG, "executeAction failed action=$logAction", e)
            toast(activity.getString(R.string.action_failed, e.message))
        }
    }

    fun handlePermissionResult(requestCode: Int, grantResults: IntArray): Boolean {
        if (requestCode == TERMUX_REQUEST) {
            val command = pendingTermuxCommand
            pendingTermuxCommand = null
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && command != null) {
                sendTermuxCommand(command)
            } else {
                toast(activity.getString(R.string.termux_permission_required))
            }
            if (isNfcTrigger()) activity.finish()
            return true
        }
        if (requestCode != CAMERA_REQUEST || pendingCameraAction == null) return false
        val action = pendingCameraAction
        pendingCameraAction = null
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && action != null) {
            execute(action)
        } else {
            toast(activity.getString(R.string.camera_permission_required))
        }
        if (isNfcTrigger()) activity.finish()
        return true
    }

    private fun runWithCameraPermission(action: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            && activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingCameraAction = action
            activity.requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST)
            return
        }
        try {
            toggleTorch()
        } catch (e: Exception) {
            toast(activity.getString(R.string.action_failed, e.message))
        }
    }

    @Throws(Exception::class)
    private fun toggleTorch() {
        val cameraManager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var cameraId: String? = null
        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (hasFlash == true && (facing == null || facing == CameraCharacteristics.LENS_FACING_BACK)) {
                cameraId = id
                break
            }
        }
        if (cameraId == null) throw IOException(activity.getString(R.string.flash_not_available))
        val isOn = preferences.getBoolean("torch_on", false)
        cameraManager.setTorchMode(cameraId, !isOn)
        preferences.edit().putBoolean("torch_on", !isOn).apply()
    }

    private fun openApp(packageName: String) {
        if (packageName.isEmpty()) throw IllegalArgumentException(activity.getString(R.string.app_package_required))
        val launch = activity.packageManager.getLaunchIntentForPackage(packageName)
            ?: throw IllegalArgumentException(activity.getString(R.string.app_not_installed, packageName))
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(launch)
    }

    private fun openLink(link: String) {
        if (link.isEmpty()) throw IllegalArgumentException(activity.getString(R.string.link_required))
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            activity.startActivity(view)
        } catch (_: ActivityNotFoundException) {
            throw IllegalArgumentException(activity.getString(R.string.no_link_handler))
        }
    }

    private fun runCustomIntent(definition: String) {
        val customIntent = CustomIntentSpec.buildIntent(definition).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            activity.startActivity(customIntent)
        } catch (_: ActivityNotFoundException) {
            throw IllegalArgumentException(activity.getString(R.string.custom_intent_no_handler))
        }
    }

    private fun runTermuxCommand(command: String) {
        if (command.isEmpty()) throw IllegalArgumentException(activity.getString(R.string.termux_argument_required))
        if (!isTermuxInstalled()) {
            toast(activity.getString(R.string.termux_not_installed))
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            && activity.checkSelfPermission(TERMUX_PERMISSION) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingTermuxCommand = command
            activity.requestPermissions(arrayOf(TERMUX_PERMISSION), TERMUX_REQUEST)
            return
        }
        sendTermuxCommand(command)
    }

    private fun isTermuxInstalled(): Boolean {
        return try {
            activity.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun sendTermuxCommand(command: String) {
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName(TERMUX_PACKAGE, "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", command))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        try {
            activity.startService(intent)
            toast(activity.getString(R.string.termux_sent))
        } catch (e: Exception) {
            Log.e(TAG, "Termux command failed", e)
            toast(activity.getString(R.string.termux_failed, e.message ?: e.javaClass.simpleName))
        }
    }

    private fun toggleSound() {
        val audio = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notifications = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !notifications.isNotificationPolicyAccessGranted) {
            activity.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return
        }
        audio.ringerMode = if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            AudioManager.RINGER_MODE_SILENT
        } else {
            AudioManager.RINGER_MODE_NORMAL
        }
    }
}
