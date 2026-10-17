package com.Hrshd1eux.filemanager.core.version

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.pm.PackageInfoCompat

/**
 * Contract for retrieving app version metadata.
 * Enables clean decoupling, unit testability, and mockability.
 */
interface AppVersionProvider {
    /**
     * Retrieves the current [AppVersionInfo] dynamically from package metadata.
     */
    fun getAppVersionInfo(): AppVersionInfo
}

/**
 * Standard implementation that queries Android's [PackageManager] for the installed package's metadata.
 *
 * Guarantees that:
 * 1. The version name is read dynamically from package metadata.
 * 2. The version code is read dynamically from package metadata using [PackageInfoCompat].
 * 3. Any app upgrade automatically reflects the new version without hardcoded UI changes.
 * 4. Any build identifier is optional and clearly labeled.
 *
 * @param context Android [Context] used to query the package manager.
 * @param buildIdentifierOverride Optional build identifier override for testing or custom build tooling.
 */
class PackageAppVersionProvider(
    private val context: Context,
    private val buildIdentifierOverride: String? = null
) : AppVersionProvider {

    override fun getAppVersionInfo(): AppVersionInfo {
        return try {
            val pm = context.packageManager
            val pkgName = context.packageName

            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    pkgName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkgName, PackageManager.GET_META_DATA)
            }

            val versionName = packageInfo.versionName?.takeIf { it.isNotBlank() } ?: "Unknown"
            val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)

            // Optional build identifier from override, manifest metadata, or debuggable flag
            val metaDataBuildId = packageInfo.applicationInfo?.metaData?.getString(META_DATA_BUILD_ID)
                ?.takeIf { it.isNotBlank() }
            val isDebuggable = ((packageInfo.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_DEBUGGABLE) != 0

            val resolvedBuildId = buildIdentifierOverride?.takeIf { it.isNotBlank() }
                ?: metaDataBuildId
                ?: (if (isDebuggable) "debug" else null)

            AppVersionInfo(
                versionName = versionName,
                versionCode = versionCode,
                applicationId = pkgName,
                buildIdentifier = resolvedBuildId
            )
        } catch (e: Exception) {
            // Fallback for test or restricted environments where PackageManager might throw
            AppVersionInfo(
                versionName = "Unknown",
                versionCode = 0L,
                applicationId = runCatching { context.packageName }.getOrDefault("com.Hrshd1eux.filemanager"),
                buildIdentifier = buildIdentifierOverride
            )
        }
    }

    companion object {
        /**
         * Optional manifest metadata key for custom build identifiers:
         * `<meta-data android:name="com.Hrshd1eux.filemanager.BUILD_ID" android:value="..." />`
         */
        const val META_DATA_BUILD_ID = "com.Hrshd1eux.filemanager.BUILD_ID"
    }
}

/**
 * Static in-memory implementation of [AppVersionProvider] for previews and unit tests.
 */
class StaticAppVersionProvider(
    private val versionInfo: AppVersionInfo
) : AppVersionProvider {
    override fun getAppVersionInfo(): AppVersionInfo = versionInfo
}

/**
 * Remembers and provides the dynamic [AppVersionInfo] in Compose hierarchies.
 */
@Composable
fun rememberAppVersionInfo(
    context: Context = LocalContext.current,
    provider: AppVersionProvider = remember(context) { PackageAppVersionProvider(context) }
): AppVersionInfo {
    return remember(provider) { provider.getAppVersionInfo() }
}
