package org.radarcns.detail
/*
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import java.lang.Exception


class AutoStartHelper private constructor() {

    fun getAutoStartPermission(context: Context) {
        println("%%% getAutoStartPermission")
        println("%%% Build.BRAND.toLowerCase() " + Build.BRAND.toLowerCase())
        println("%%% BRAND_XIAOMI " + BRAND_XIAOMI)
        println("%%% BRAND_XIAOMI " + Build.MANUFACTURER)
        println("%%% BRAND_XIAOMI " + Build.BOARD)
        println("%%% BRAND_XIAOMI " + Build.DEVICE)
        println("%%% BRAND_XIAOMI " + Build.MODEL)
        println("%%% BRAND_XIAOMI " + Build.PRODUCT)

        when (Build.MANUFACTURER.toLowerCase()) {
            BRAND_XIAOMI -> autoStartXiaomi(context)
            BRAND_LETV -> autoStartLetv(context)
//            BRAND_ASUS -> autoStartAsus(context)
//            BRAND_HONOR -> autoStartHonor(context)
//            BRAND_OPPO -> autoStartOppo(context)
//            BRAND_VIVO -> autoStartVivo(context)
//            BRAND_NOKIA -> autoStartNokia(context)
        }
    }

//    private fun autoStartAsus(context: Context) {
//        if (isPackageExists(context, PACKAGE_ASUS_MAIN)) {
//            showAlert(context) { dialog: DialogInterface, which: Int ->
//                try {
//                    PrefUtil.writeBoolean(context, PrefUtil.PREF_KEY_APP_AUTO_START, true)
//                    startIntent(context, PACKAGE_ASUS_MAIN, PACKAGE_ASUS_COMPONENT)
//                } catch (e: Exception) {
//                    e.printStackTrace()
//                }
//                dialog.dismiss()
//            }
//        }
//    }

    private fun autoStartXiaomi(context: Context) {
        println("%%% autoStartXiaomi")
        if (isPackageExists(context, PACKAGE_XIAOMI_MAIN)) {
            alertDialog(context) {
                setTitle("Enable Package Auto Start") // R.string.enable_package_usage_title)
                setMessage("Enable Package Auto Start Message") //R.string.enable_package_usage)
                setPositiveButton(android.R.string.ok) { dialog, _ ->
                    dialog.cancel()
//                    var intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
//                    if (intent.resolveActivity(context.packageManager) == null) {
//                        intent = Intent(Settings.ACTION_SETTINGS)
//                    }
//                intent.startActivityForResult(USAGE_REQUEST_CODE)
//                intent.resolveActivity(packageManager) ?: return
                    try {
                        startIntent(context, PACKAGE_XIAOMI_MAIN, PACKAGE_XIAOMI_COMPONENT)
//                        startActivityForResult(intent, USAGE_REQUEST_CODE)
//                        startActivityForResult(intent, PACKAGE_XIAOMI_MAIN, PACKAGE_XIAOMI_COMPONENT)
                    } catch (ex: ActivityNotFoundException) {
//            PermissionHandler.logger.error("Failed to ask for usage code", ex)
                    } catch (ex: IllegalStateException) {
//            PermissionHandler.logger.warn("Cannot start activity on closed app")
                    }
                }
                setIcon(android.R.drawable.ic_dialog_alert)
            }
        }
    }

    private fun autoStartLetv(context: Context) {
        if (isPackageExists(context, PACKAGE_LETV_MAIN)) {
            alertDialog(context) {
                setTitle("Enable Package Auto Start") // R.string.enable_package_usage_title)
                setMessage("Enable Package Auto Start Message") //R.string.enable_package_usage)
                setPositiveButton(android.R.string.ok) { dialog, _ ->
                    dialog.cancel()
//                    var intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
//                    if (intent.resolveActivity(context.packageManager) == null) {
//                        intent = Intent(Settings.ACTION_SETTINGS)
//                    }
//                intent.startActivityForResult(USAGE_REQUEST_CODE)
//                intent.resolveActivity(packageManager) ?: return
                    try {
                        startIntent(context, PACKAGE_LETV_MAIN, PACKAGE_LETV_COMPONENT)
//                        startActivityForResult(intent, USAGE_REQUEST_CODE)
//                        startActivityForResult(intent, PACKAGE_XIAOMI_MAIN, PACKAGE_XIAOMI_COMPONENT)
                    } catch (ex: ActivityNotFoundException) {
//            PermissionHandler.logger.error("Failed to ask for usage code", ex)
                    } catch (ex: IllegalStateException) {
//            PermissionHandler.logger.warn("Cannot start activity on closed app")
                    }
                }
                setIcon(android.R.drawable.ic_dialog_alert)
            }
        }
    }
//
//    private fun autoStartHonor(context: Context) {
//        if (isPackageExists(context, PACKAGE_HONOR_MAIN)) {
//            showAlert(context) { dialog, which ->
//                try {
//                    PrefUtil.writeBoolean(context, PrefUtil.PREF_KEY_APP_AUTO_START, true)
//                    startIntent(context, PACKAGE_HONOR_MAIN, PACKAGE_HONOR_COMPONENT)
//                } catch (e: Exception) {
//                    e.printStackTrace()
//                }
//            }
//        }
//    }
//
//    private fun autoStartOppo(context: Context) {
//        if (isPackageExists(context, PACKAGE_OPPO_MAIN) || isPackageExists(
//                context,
//                PACKAGE_OPPO_FALLBACK
//            )
//        ) {
//            showAlert(context) { dialog, which ->
//                try {
//                    PrefUtil.writeBoolean(context, PrefUtil.PREF_KEY_APP_AUTO_START, true)
//                    startIntent(context, PACKAGE_OPPO_MAIN, PACKAGE_OPPO_COMPONENT)
//                } catch (e: Exception) {
//                    e.printStackTrace()
//                    try {
//                        PrefUtil.writeBoolean(context, PrefUtil.PREF_KEY_APP_AUTO_START, true)
//                        startIntent(context, PACKAGE_OPPO_FALLBACK, PACKAGE_OPPO_COMPONENT_FALLBACK)
//                    } catch (ex: Exception) {
//                        ex.printStackTrace()
//                        try {
//                            PrefUtil.writeBoolean(context, PrefUtil.PREF_KEY_APP_AUTO_START, true)
//                            startIntent(
//                                context,
//                                PACKAGE_OPPO_MAIN,
//                                PACKAGE_OPPO_COMPONENT_FALLBACK_A
//                            )
//                        } catch (exx: Exception) {
//                            exx.printStackTrace()
//                        }
//                    }
//                }
//            }
//        }
//    }
//
//    private fun autoStartVivo(context: Context) {
//        if (isPackageExists(context, PACKAGE_VIVO_MAIN) || isPackageExists(
//                context,
//                PACKAGE_VIVO_FALLBACK
//            )
//        ) {
//            showAlert(context) { dialog, which ->
//                try {
//                    PrefUtil.writeBoolean(context, PrefUtil.PREF_KEY_APP_AUTO_START, true)
//                    startIntent(context, PACKAGE_VIVO_MAIN, PACKAGE_VIVO_COMPONENT)
//                } catch (e: Exception) {
//                    e.printStackTrace()
//                    try {
//                        PrefUtil.writeBoolean(context, PrefUtil.PREF_KEY_APP_AUTO_START, true)
//                        startIntent(context, PACKAGE_VIVO_FALLBACK, PACKAGE_VIVO_COMPONENT_FALLBACK)
//                    } catch (ex: Exception) {
//                        ex.printStackTrace()
//                        try {
//                            PrefUtil.writeBoolean(context, PrefUtil.PREF_KEY_APP_AUTO_START, true)
//                            startIntent(
//                                context,
//                                PACKAGE_VIVO_MAIN,
//                                PACKAGE_VIVO_COMPONENT_FALLBACK_A
//                            )
//                        } catch (exx: Exception) {
//                            exx.printStackTrace()
//                        }
//                    }
//                }
//            }
//        }
//    }
//
//    private fun autoStartNokia(context: Context) {
//        if (isPackageExists(context, PACKAGE_NOKIA_MAIN)) {
//            showAlert(context) { dialog, which ->
//                try {
//                    PrefUtil.writeBoolean(context, PrefUtil.PREF_KEY_APP_AUTO_START, true)
//                    startIntent(context, PACKAGE_NOKIA_MAIN, PACKAGE_NOKIA_COMPONENT)
//                } catch (e: Exception) {
//                    e.printStackTrace()
//                }
//            }
//        }
//    }

    private fun alertDialog(context: Context, configure: AlertDialog.Builder.() -> Unit) {
        try {
            (context as Activity).runOnUiThread {
                AlertDialog.Builder(context, android.R.style.Theme_Material_Dialog_Alert)
                    .apply(configure)
                    .show()
            }
        } catch (ex: IllegalStateException) {
//            PermissionHandler.logger.warn("Cannot show dialog on closing activity")
        }
    }

    @Throws(Exception::class)
    private fun startIntent(context: Context, packageName: String, componentName: String) {
        try {
            val intent = Intent()
            intent.component = ComponentName(packageName, componentName)
            context.startActivity(intent)
        } catch (var5: Exception) {
            var5.printStackTrace()
            throw var5
        }
    }

    private fun isPackageExists(context: Context, targetPackage: String): Boolean {
        val packages: List<ApplicationInfo>
        val pm: PackageManager = context.getPackageManager()
        packages = pm.getInstalledApplications(0)
        for (packageInfo in packages) {
            if (packageInfo.packageName == targetPackage) {
                return true
            }
        }
        return false
    }

    companion object {
        val instance: AutoStartHelper
            get() = AutoStartHelper()

        /***
         * Xiaomi
         */
        private const val BRAND_XIAOMI = "xiaomi"
        private const val PACKAGE_XIAOMI_MAIN = "com.miui.securitycenter"
        private const val PACKAGE_XIAOMI_COMPONENT =
            "com.miui.permcenter.autostart.AutoStartManagementActivity"

        /***
         * Letv
         */
        private const val BRAND_LETV = "letv"
        private const val PACKAGE_LETV_MAIN = "com.letv.android.letvsafe"
        private const val PACKAGE_LETV_COMPONENT = "com.letv.android.letvsafe.AutobootManageActivity"
//
//        /***
//         * ASUS ROG
//         */
//        private const val BRAND_ASUS = "asus"
//        private const val PACKAGE_ASUS_MAIN = "com.asus.mobilemanager"
//        private const val PACKAGE_ASUS_COMPONENT = "com.asus.mobilemanager.powersaver.PowerSaverSettings"
//
//        /***
//         * Honor
//         */
//        private const val BRAND_HONOR = "honor"
//        private const val PACKAGE_HONOR_MAIN = "com.huawei.systemmanager"
//        private const val PACKAGE_HONOR_COMPONENT =
//            "com.huawei.systemmanager.optimize.process.ProtectActivity"
//
//        /**
//         * Oppo
//         */
//        private const val BRAND_OPPO = "oppo"
//        private const val PACKAGE_OPPO_MAIN = "com.coloros.safecenter"
//        private const val PACKAGE_OPPO_FALLBACK = "com.oppo.safe"
//        private const val PACKAGE_OPPO_COMPONENT =
//            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
//        private const val PACKAGE_OPPO_COMPONENT_FALLBACK =
//            "com.oppo.safe.permission.startup.StartupAppListActivity"
//        private const val PACKAGE_OPPO_COMPONENT_FALLBACK_A =
//            "com.coloros.safecenter.startupapp.StartupAppListActivity"
//
//        /**
//         * Vivo
//         */
//        private const val BRAND_VIVO = "vivo"
//        private const val PACKAGE_VIVO_MAIN = "com.iqoo.secure"
//        private const val PACKAGE_VIVO_FALLBACK = "com.vivo.perm;issionmanager"
//        private const val PACKAGE_VIVO_COMPONENT = "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
//        private const val PACKAGE_VIVO_COMPONENT_FALLBACK =
//            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
//        private const val PACKAGE_VIVO_COMPONENT_FALLBACK_A =
//            "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
//
//        /**
//         * Nokia
//         */
//        private const val BRAND_NOKIA = "nokia"
//        private const val PACKAGE_NOKIA_MAIN = "com.evenwell.powersaving.g3"
//        private const val PACKAGE_NOKIA_COMPONENT =
//            "com.evenwell.powersaving.g3.exception.PowerSaverExceptionActivity"

    }
}*/
