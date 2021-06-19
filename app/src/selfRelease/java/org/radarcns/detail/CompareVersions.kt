package org.radarcns.detail

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.json.JSONObject
import org.radarcns.detail.UpdateScheduledService.Companion.UPDATE_VERSION_NAME_KEY
import org.radarcns.detail.UpdateScheduledService.Companion.UPDATE_VERSION_URL_KEY

fun getUpdatePackage(context: Context, response: String): JSONObject? {
    val updatePackage = getUpdatePackageVersionAndUrl(response)
    val currentPackageVersion = getInstalledPackageVersion(context)

    val pattern = Regex("\\d+(\\.\\d+)+")

    val rawCurrentPackageVersion = currentPackageVersion?.let { pattern.find(it)?.value }
    val rawUpdatePackageVersion = updatePackage?.getString(UPDATE_VERSION_NAME_KEY)?.let { pattern.find(it)?.value }

    if (rawCurrentPackageVersion != rawUpdatePackageVersion) {
        return updatePackage
    }
    return null
}

fun getInstalledPackageVersion(context: Context): String? {
    try {
        return context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (e: PackageManager.NameNotFoundException) {
        Log.e("CompareVersions", "Cannot resolve version for " + context.packageName, e)
        e.printStackTrace()
    }
    return null
}

fun getUpdatePackageVersionAndUrl(response: String): JSONObject? {
    val responseObject = Json.parseToJsonElement(response)
    if (responseObject.jsonArray.size > 0) {
        val latestRelease = responseObject.jsonArray[0]
        val tagName = latestRelease.jsonObject["tag_name"]?.toString()?.replace("\"", "")
        val browserDownloadUrl = latestRelease.jsonObject["assets"]?.jsonArray?.get(0)?.jsonObject?.getValue("browser_download_url").toString().replace("\"", "")
        val updateApk = JSONObject()
        updateApk.put(UPDATE_VERSION_URL_KEY, browserDownloadUrl)
        updateApk.put(UPDATE_VERSION_NAME_KEY, tagName)
        return updateApk
    }
    return null
}
