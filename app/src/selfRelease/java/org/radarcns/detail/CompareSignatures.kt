package org.radarcns.detail

import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.Signature
import android.util.Log

fun isSignatureSame(
    context: Context,
    archiveFilePath: String
): Boolean {
    return isSignaturesSame(
        getInstalledPackageSignatures(context),
        getUpdatePackageSignatures(context, archiveFilePath)
    )
}

fun isSignaturesSame(s1: Array<Signature>?, s2: Array<Signature>?): Boolean {
    if (s1 == null) {
        return false
    }
    if (s2 == null) {
        return false
    }
    val set1: HashSet<Signature> = HashSet()
    for (sig in s1) {
        set1.add(sig)
    }
    val set2: HashSet<Signature> = HashSet()
    for (sig in s2) {
        set2.add(sig)
    }
    return set1 == set2
}

fun getInstalledPackageSignatures(context: Context): Array<Signature>? {
    try {
        return context.packageManager.getPackageInfo(context.packageName, 0).signatures
    } catch (e: NameNotFoundException) {
        Log.e("CompareSignatures", "Cannot resolve info for " + context.packageName, e)
        e.printStackTrace()
    }
    return null
}

fun getUpdatePackageSignatures(context: Context, apkPath: String): Array<Signature>? {
    try {
        return context.packageManager.getPackageArchiveInfo(apkPath, 0)?.signatures
    } catch (e: NameNotFoundException) {
        Log.e("CompareSignatures", "Cannot resolve info for $apkPath", e)
        e.printStackTrace()
    }
    return null
}