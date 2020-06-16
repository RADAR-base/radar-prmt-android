package org.radarbase.android.util

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
//import com.crashlytics.android.Crashlytics
import org.radarbase.android.R
import org.radarbase.android.RadarService
import org.slf4j.LoggerFactory

open class PermissionHandler(private val activity: AppCompatActivity, private val mHandler: SafeHandler, private val requestPermissionTimeoutMs: Long) {
    private val broadcaster = LocalBroadcastManager.getInstance(activity)

    private var needsPermissions: MutableSet<String> = HashSet()
    private val isRequestingPermissions: MutableSet<String> = HashSet()
    private var isRequestingPermissionsTime = java.lang.Long.MAX_VALUE

    private fun onPermissionRequestResult(permission: String, granted: Boolean) {
        mHandler.execute {
            needsPermissions.remove(permission)

            val result = if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
            broadcaster.send(RadarService.ACTION_PERMISSIONS_GRANTED) {
                putExtra(RadarService.EXTRA_PERMISSIONS, arrayOf(Context.LOCATION_SERVICE))
                putExtra(RadarService.EXTRA_GRANT_RESULTS, intArrayOf(result))
            }

            isRequestingPermissions.remove(permission)
            checkPermissions()
        }
    }

    private fun checkPermissions() {
        mHandler.executeReentrant {
            val currentlyNeeded = needsPermissions - isRequestingPermissions
            when {
                needsPermissions.isEmpty() -> {
                    broadcaster.send(RadarService.ACTION_PERMISSIONS_GRANTED) {
                        putExtra(RadarService.EXTRA_PERMISSIONS, arrayOfNulls<String>(0))
                        putExtra(RadarService.EXTRA_GRANT_RESULTS, IntArray(0))
                    }
                }
                currentlyNeeded.isEmpty() -> {
                }
                Context.LOCATION_SERVICE in currentlyNeeded -> {
                    addRequestingPermissions(Context.LOCATION_SERVICE)
                    requestLocationProvider()
                }
                RadarService.PACKAGE_USAGE_STATS_COMPAT in currentlyNeeded -> {
                    addRequestingPermissions(RadarService.PACKAGE_USAGE_STATS_COMPAT)
                    requestPackageUsageStats()
                }
                RadarService.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS_COMPAT in currentlyNeeded -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        addRequestingPermissions(RadarService.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS_COMPAT)
                        requestDisableBatteryOptimization()
                    } else {
                        needsPermissions.remove(RadarService.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS_COMPAT)
                    }
                }
                else -> {
                    addRequestingPermissions(currentlyNeeded)
                    try {
                        ActivityCompat.requestPermissions(activity,
                                currentlyNeeded.toTypedArray(), REQUEST_ENABLE_PERMISSIONS)
                    } catch (ex: IllegalStateException) {
                        logger.warn("Cannot request permission on closing activity")
                    }
                }
            }
        }
    }

    private fun resetRequestingPermission() {
        isRequestingPermissions.clear()
        isRequestingPermissionsTime = java.lang.Long.MAX_VALUE
    }

    private fun addRequestingPermissions(permission: String) {
        addRequestingPermissions(setOf(permission))
    }

    private fun addRequestingPermissions(permissions: Set<String>) {
        isRequestingPermissions.addAll(permissions)

        if (isRequestingPermissionsTime != java.lang.Long.MAX_VALUE) {
            isRequestingPermissionsTime = System.currentTimeMillis()
            mHandler.delay(requestPermissionTimeoutMs) {
                resetRequestingPermission()
                checkPermissions()
            }
        }
    }

    private fun alertDialog(configure: AlertDialog.Builder.() -> Unit) {
        try {
            activity.runOnUiThread {
                AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert)
                        .apply(configure)
                        .show()
            }
        } catch (ex: IllegalStateException) {
            logger.warn("Cannot show dialog on closing activity")
        }
    }

    private fun requestLocationProvider() {
        alertDialog {
            setTitle(R.string.enable_location_title)
            setMessage(R.string.enable_location)
            setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.cancel()
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                if (intent.resolveActivity(activity.packageManager) != null) {
                    activity.startActivityForResult(intent, LOCATION_REQUEST_CODE)
                }
            }
            setIcon(android.R.drawable.ic_dialog_alert)
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @SuppressLint("BatteryLife")
    private fun requestDisableBatteryOptimization() {
        val intent = Intent().apply {
            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            data = Uri.parse("package:" + activity.applicationContext.packageName)
        }
        if (intent.resolveActivity(activity.packageManager) != null) {
            activity.startActivityForResult(intent, BATTERY_OPT_CODE)
        }
    }


    private fun requestPackageUsageStats() {
        alertDialog {
            setTitle(R.string.enable_package_usage_title)
            setMessage(R.string.enable_package_usage)
            setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.cancel()
                var intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                if (intent.resolveActivity(activity.packageManager) == null) {
                    intent = Intent(Settings.ACTION_SETTINGS)
                }
                try {
                    activity.startActivityForResult(intent, USAGE_REQUEST_CODE)
                } catch (ex: ActivityNotFoundException) {
                    logger.error("Failed to ask for usage code", ex)
                    //Crashlytics.logException(ex)
                } catch (ex: IllegalStateException) {
                    logger.warn("Cannot start activity on closed app")
                }
            }
            setIcon(android.R.drawable.ic_dialog_alert)
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int) {
        when (requestCode) {
            LOCATION_REQUEST_CODE -> {
                onPermissionRequestResult(Context.LOCATION_SERVICE, resultCode == Activity.RESULT_OK)
            }
            USAGE_REQUEST_CODE -> {
                onPermissionRequestResult(
                        RadarService.PACKAGE_USAGE_STATS_COMPAT,
                        resultCode == Activity.RESULT_OK)
            }
            BATTERY_OPT_CODE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val powerManager = activity.getSystemService(Context.POWER_SERVICE) as PowerManager?
                    val granted = resultCode == Activity.RESULT_OK
                            || powerManager?.isIgnoringBatteryOptimizations(activity.applicationContext.packageName) != false
                    onPermissionRequestResult(RadarService.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS_COMPAT, granted)
                }
            }
        }
    }

    fun invalidateCache() {
        mHandler.execute {
            if (!isRequestingPermissions.isEmpty()) {
                val now = System.currentTimeMillis()
                val expires = isRequestingPermissionsTime + requestPermissionTimeoutMs
                if (expires <= now) {
                    resetRequestingPermission()
                } else {
                    mHandler.delay(expires - now, ::resetRequestingPermission)
                }
            }
        }
    }

    fun replaceNeededPermissions(newPermissions: Array<out String>?) {
        newPermissions?.also { permissions ->
            mHandler.execute {
                needsPermissions = mutableSetOf(*permissions)
                checkPermissions()
            }
        }
    }

    fun permissionsGranted(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_ENABLE_PERMISSIONS) {
            broadcaster.send(RadarService.ACTION_PERMISSIONS_GRANTED) {
                putExtra(RadarService.EXTRA_PERMISSIONS, permissions)
                putExtra(RadarService.EXTRA_GRANT_RESULTS, grantResults)
            }
        }
    }

    fun saveInstanceState(savedInstanceState: Bundle) {
        savedInstanceState.putStringArrayList("isRequestingPermissions", ArrayList(isRequestingPermissions))
        savedInstanceState.putLong("isRequestingPermissionsTime", isRequestingPermissionsTime)
    }

    fun restoreInstanceState(savedInstanceState: Bundle) {
        val isRequesting = savedInstanceState.getStringArrayList("isRequestingPermissions")
        if (isRequesting != null) {
            isRequestingPermissions += isRequesting
        }
        isRequestingPermissionsTime = savedInstanceState.getLong("isRequestingPermissionsTime", java.lang.Long.MAX_VALUE)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PermissionHandler::class.java)

        private const val REQUEST_ENABLE_PERMISSIONS = 2
        // can only use lower 16 bits for request code
        private const val LOCATION_REQUEST_CODE = 232619694 and 0xFFFF
        private const val USAGE_REQUEST_CODE = 232619695 and 0xFFFF
        private const val BATTERY_OPT_CODE = 232619696 and 0xFFFF
    }
}
