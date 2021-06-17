package org.radarcns.detail

import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException
import android.util.Log

fun isPackageNameSame(context: Context, apkPath: String): Boolean {
    return context.packageName == getUpdatePackageName(context, apkPath)
}

fun getUpdatePackageName(context: Context, apkPath: String): String? {
    try {
        return context.packageManager.getPackageArchiveInfo(apkPath, 0)?.packageName
    } catch (e: NameNotFoundException) {
        Log.e("ComparePackageNames", "Cannot resolve package name for $apkPath", e)
        e.printStackTrace()
    }
    return null
}