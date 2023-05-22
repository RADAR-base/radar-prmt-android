package org.radarcns.detail

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager.NameNotFoundException
import org.slf4j.LoggerFactory

class PackageUtil(private val context: Context) {
    private val packageName = context.packageName

    fun packageNameMatches(apkPath: String): Boolean {
        return packageName == readArchiveInfo(apkPath)?.packageName
    }

    val installedPackageVersion: String?
        get() = try {
            context.packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: NameNotFoundException) {
            logger.error("Cannot resolve version for {}", packageName, e)
            null
        }

    private fun readArchiveInfo(apkPath: String): PackageInfo? {
        return try {
            context.packageManager.getPackageArchiveInfo(apkPath, 0)
        } catch (e: NameNotFoundException) {
            logger.error("Cannot resolve package for {}", apkPath, e)
            null
        }
    }

    fun assetIsUpdate(asset: OnlineAsset): Boolean {
        return try {
            val currentPackageVersion = SemVer.parse(installedPackageVersion ?: return false)
            val assetTag = asset.tag.removePrefix("v")
            val updatePackageVersion = SemVer.parse(assetTag)

            updatePackageVersion > currentPackageVersion
        } catch (e: Throwable) {
            logger.error("Failed to determine versions", e)
            false
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PackageUtil::class.java)
    }
}
