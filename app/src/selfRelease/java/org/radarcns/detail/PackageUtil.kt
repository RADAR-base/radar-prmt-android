package org.radarcns.detail

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.radarcns.detail.UpdatesActivity.Companion.UPDATE_VERSION_NAME_KEY
import org.radarcns.detail.UpdatesActivity.Companion.UPDATE_VERSION_URL_KEY
import org.slf4j.LoggerFactory

class PackageUtil(private val context: Context) {
    private val packageName = context.packageName

    fun isPackageNameSame(apkPath: String): Boolean {
        return packageName == getUpdatePackageName(apkPath)
    }

    fun getUpdatePackageName(apkPath: String): String? {
        try {
            return context.packageManager.getPackageArchiveInfo(apkPath, 0)?.packageName
        } catch (e: NameNotFoundException) {
            logger.error("Cannot resolve package name for {}", apkPath, e)
        }
        return null
    }

    fun getUpdatePackage(response: String): JSONObject? {
        val updatePackage = getUpdatePackageVersionAndUrl(response)
        val currentPackageVersion = getInstalledPackageVersion() ?: return null

        val pattern = Regex("\\d+(\\.\\d+)+")

        val rawCurrentPackageVersion = pattern.find(currentPackageVersion)?.value ?: return null
        val rawUpdatePackageVersion = updatePackage?.getString(UPDATE_VERSION_NAME_KEY)?.let { pattern.find(it)?.value }

        return if (rawCurrentPackageVersion != rawUpdatePackageVersion) {
            updatePackage
        } else null
    }

    fun getInstalledPackageVersion(): String? {
        return try {
            context.packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            logger.error("Cannot resolve version for {}", packageName, e)
            null
       }
    }

    fun getUpdatePackageVersionAndUrl(response: String): JSONObject? {
        try {
            val responseArray = JSONArray(response)
            if (responseArray.length() > 0) {
                val latestRelease = responseArray[0] as JSONObject
                val browserDownloadUrl =
                    ((latestRelease["assets"] as JSONArray).get(0) as JSONObject)["browser_download_url"]
                return JSONObject().apply {
                    put(UPDATE_VERSION_URL_KEY, browserDownloadUrl)
                    put(UPDATE_VERSION_NAME_KEY, latestRelease["tag_name"])
                }
            }
        } catch (t: Throwable) {
            logger.error("CompareVersions", "Could not parse malformed JSON: \"{}\"", response)
        }
        return null
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PackageUtil::class.java)
    }
}
