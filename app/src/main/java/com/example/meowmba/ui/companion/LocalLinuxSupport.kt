package com.example.meowmba.ui.companion

import android.content.Context
import android.content.pm.PackageManager
import com.example.meowmba.core.agentic.AgenticSshCapability
import java.io.File

/**
 * Legitimate local Linux paths for the companion Terminal and agentic [local_shell].
 *
 * Preference order (no Magisk / SELinux bypass / phone root / other-app private data):
 * 1. Termux bash/sh when executable from this UID — userspace Linux. Prefer a
 *    **PRoot / proot-distro guest** as root-*in-guest* (`proot -0`, fakeroot, or
 *    `proot-distro login`) for a micro Linux with apt; that guest root is NOT
 *    Android/device root.
 * 2. Bootstrap OpenSSH **client** inside Termux or the proot guest so the guest
 *    can `ssh` out to meow (network). App [ConnectionProfile] SSH remains available.
 * 3. Termux [com.termux.RUN_COMMAND] when Termux is installed but ProcessBuilder
 *    cannot exec the binary (SELinux / UID) — documented allow-external-apps path.
 * 4. App-sandbox `/system/bin/sh` + [LocalShellBuiltins] Java ls/pwd.
 *
 * Honest limits: the APK cannot leave its Android sandbox. Guest root ≠ phone root.
 * SSH from guest uses the network. App SSH profiles also reach a full machine (meow).
 * See docs/LOCAL_LINUX.md.
 */
enum class LocalLinuxKind {
    /** Termux bash/sh is ProcessBuilder-executable. */
    TERMUX_LINUX,
    /** Termux installed/visible but not executable from this UID — RUN_COMMAND / sandbox UI. */
    TERMUX_RUN_COMMAND,
    /** Sandbox system sh only. */
    LOCAL_SANDBOX,
}

data class LocalLinuxProbe(
    val kind: LocalLinuxKind,
    /** Best interactive shell path (Termux when executable, else system sh). */
    val shellPath: String?,
    val termuxInstalled: Boolean,
    val termuxPrefixVisible: Boolean,
    val termuxExecutable: Boolean,
    val label: String,
    val bannerHint: String,
    val installHint: String?,
) {
    val usesTermuxHome: Boolean
        get() = kind == LocalLinuxKind.TERMUX_LINUX
}

object LocalLinuxSupport {
    const val TERMUX_BASH = "${AgenticSshCapability.TERMUX_BIN}/bash"
    const val TERMUX_SH = "${AgenticSshCapability.TERMUX_BIN}/sh"
    const val TERMUX_HOME = "/data/data/com.termux/files/home"
    const val TERMUX_USR = "/data/data/com.termux/files/usr"

    /** Clear UI copy when the user expects apt/ls and only has sandbox sh. */
    const val INSTALL_TERMUX_MSG =
        "Install Termux for a real local Linux; sandbox sh cannot do ls/apt"

    /**
     * Documented bootstrap for micro Linux + outbound SSH from the guest.
     * Root here is **guest** root (PRoot/proot-distro), never Android root.
     */
    const val BOOTSTRAP_HINT =
        "Bootstrap: pkg install proot proot-distro openssh  OR  in proot-distro guest: " +
            "apt install openssh-client. Then: proot-distro login <distro>  (root-in-guest). " +
            "ssh user@meow from guest uses network. Guest root != phone root."

    /** One-liner operators can paste in Termux (userspace only). */
    const val TERMUX_BOOTSTRAP_CMD =
        "pkg install -y proot proot-distro openssh && " +
            "echo 'Next: proot-distro install ubuntu && proot-distro login ubuntu' && " +
            "echo 'In guest (root-in-guest): apt update && apt install -y openssh-client'"

    const val PROOT_LOGIN_HINT =
        "Root-in-guest: proot-distro login <distro>  OR  proot -0 -r <rootfs> /bin/bash. " +
            "Not Magisk / not SELinux bypass / not phone root."

    private val TERMUX_SHELLS = listOf(TERMUX_BASH, TERMUX_SH)
    private val SANDBOX_SHELLS = listOf("/system/bin/sh", "/bin/sh")

    fun isTermuxInstalled(context: Context): Boolean =
        try {
            context.packageManager.getPackageInfo(AgenticSshCapability.TERMUX_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    fun firstExecutable(candidates: List<String>): String? {
        for (path in candidates) {
            val f = File(path)
            if (f.exists() && f.canExecute()) return path
        }
        return null
    }

    fun findTermuxShell(): String? = firstExecutable(TERMUX_SHELLS)

    fun findSandboxShell(): String? = firstExecutable(SANDBOX_SHELLS)

    /** Prefer Termux bash/sh, else sandbox sh. */
    fun findPreferredShell(): String? = findTermuxShell() ?: findSandboxShell()

    fun probe(context: Context): LocalLinuxProbe {
        val installed = isTermuxInstalled(context)
        val prefixVisible = File(AgenticSshCapability.TERMUX_BIN).isDirectory ||
            File(TERMUX_USR).isDirectory
        val termuxShell = findTermuxShell()
        val sandboxShell = findSandboxShell()

        return when {
            termuxShell != null -> LocalLinuxProbe(
                kind = LocalLinuxKind.TERMUX_LINUX,
                shellPath = termuxShell,
                termuxInstalled = installed || prefixVisible,
                termuxPrefixVisible = prefixVisible,
                termuxExecutable = true,
                label = "Termux Linux",
                bannerHint =
                    "Termux userspace Linux — use proot-distro / proot -0 for root-in-guest " +
                        "(not phone root). Install openssh client in guest to ssh out to meow. " +
                        "App SSH profiles also work.",
                installHint = BOOTSTRAP_HINT,
            )
            installed || prefixVisible -> LocalLinuxProbe(
                kind = LocalLinuxKind.TERMUX_RUN_COMMAND,
                shellPath = sandboxShell,
                termuxInstalled = true,
                termuxPrefixVisible = prefixVisible,
                termuxExecutable = false,
                label = "Local sandbox",
                bannerHint =
                    "Termux is installed but its binaries are not executable from this app (SELinux). " +
                        "Enable Termux allow-external-apps for RUN_COMMAND one-shots; " +
                        "interactive shell stays sandbox. $INSTALL_TERMUX_MSG",
                installHint =
                    "Enable Termux allow-external-apps for RUN_COMMAND. " + BOOTSTRAP_HINT +
                        " Or use an app SSH profile for meow.",
            )
            else -> LocalLinuxProbe(
                kind = LocalLinuxKind.LOCAL_SANDBOX,
                shellPath = sandboxShell,
                termuxInstalled = false,
                termuxPrefixVisible = false,
                termuxExecutable = false,
                label = "Local sandbox",
                bannerHint =
                    "Local APK cannot leave the Android sandbox. $INSTALL_TERMUX_MSG. " +
                        "For userspace micro Linux + guest root + ssh client: install Termux, " +
                        "then proot-distro. App SSH profiles still reach meow.",
                installHint = INSTALL_TERMUX_MSG + " — " + BOOTSTRAP_HINT,
            )
        }
    }

    fun terminalTarget(context: Context?): CompanionTerminalTarget.Local {
        if (context == null) {
            return CompanionTerminalTarget.Local(LocalLinuxKind.LOCAL_SANDBOX)
        }
        return CompanionTerminalTarget.Local(probe(context).kind)
    }

    fun resolveCwd(filesDir: File?, shellPath: String?, kind: LocalLinuxKind): File? {
        if (kind == LocalLinuxKind.TERMUX_LINUX || (shellPath != null && shellPath.contains("com.termux"))) {
            val home = File(TERMUX_HOME)
            if (home.isDirectory) return home
            val usr = File(TERMUX_USR)
            if (usr.isDirectory) return usr
        }
        if (filesDir != null) {
            filesDir.mkdirs()
            if (filesDir.isDirectory && filesDir.canRead()) return filesDir
        }
        return null
    }
}
