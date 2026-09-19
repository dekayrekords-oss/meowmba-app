package com.example.meowmba.core.computer

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Inspect actual IPC capabilities, not just the package name or installer label. */
enum class TermuxSetupSupport {
    MISSING, AUTOMATIC, GUIDED;

    companion object {
        fun detect(context: Context): TermuxSetupSupport {
            val pm = context.packageManager
            try { pm.getPackageInfo(TermuxComputerLinks.TERMUX_PACKAGE, 0) }
            catch (_: PackageManager.NameNotFoundException) { return MISSING }
            val permission = runCatching { pm.getPermissionInfo(PhoneComputerSetup.PERMISSION, 0) }.getOrNull()
            val service = runCatching { pm.getServiceInfo(ComponentName("com.termux", "com.termux.app.RunCommandService"), 0) }.getOrNull()
            return classify(true, permission?.packageName == "com.termux", service?.let {
                it.exported && it.enabled && it.applicationInfo.enabled && it.permission == PhoneComputerSetup.PERMISSION
            } == true)
        }

        internal fun classify(installed: Boolean, permissionDeclared: Boolean, serviceAvailable: Boolean) = when {
            !installed -> MISSING
            permissionDeclared && serviceAvailable -> AUTOMATIC
            else -> GUIDED
        }
    }
}
