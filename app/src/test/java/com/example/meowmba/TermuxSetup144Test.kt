package com.example.meowmba

import com.example.meowmba.core.computer.*
import java.io.File
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class TermuxSetup144Test {
    private val source get() = File("src/main/assets/computer/setup-phone.sh").readText()

    @Test fun installedPackageWithoutPermissionOrServiceNeedsGuidedSetup() {
        assertEquals(TermuxSetupSupport.MISSING, TermuxSetupSupport.classify(false, true, true))
        assertEquals(TermuxSetupSupport.GUIDED, TermuxSetupSupport.classify(true, false, false))
        assertEquals(TermuxSetupSupport.GUIDED, TermuxSetupSupport.classify(true, true, false))
        assertEquals(TermuxSetupSupport.GUIDED, TermuxSetupSupport.classify(true, false, true))
        assertEquals(TermuxSetupSupport.AUTOMATIC, TermuxSetupSupport.classify(true, true, true))
    }

    @Test fun copiedSetupIsFixedScriptWithHiddenInteractivePasswordNotEmbeddedSecret() {
        val command = PhoneComputerSetup.guidedCommandFromSource(source, OpenCodeLocalConnection.RECOVERY_ADDRESS)
        assertFalse(command.contains('\n'))
        val encoded = command.substringAfter("printf '%s' '").substringBefore("'")
        val script = String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
        assertEquals(PhoneComputerSetup.guidedScript(source, OpenCodeLocalConnection.RECOVERY_ADDRESS), script)
        assertTrue(script.contains("read -r -s -p"))
        assertTrue(script.contains("</dev/tty"))
        assertTrue(script.contains("Repeat password:"))
        assertTrue(script.contains("set +x"))
        assertTrue(script.contains("unset meow_password"))
        assertTrue(script.contains("/dev/tcp/127.0.0.1/4097"))
        assertFalse(script.contains("4096"))
        assertTrue(script.contains("--hostname 127.0.0.1 --port 4097"))
        assertTrue(script.contains("installed-rootfs/ubuntu"))
        assertFalse(script.contains("pkill"))
        assertFalse(script.contains("proot-distro remove"))
        for (text in listOf(command, script)) {
            val process = ProcessBuilder("bash", "-n").start()
            process.outputStream.bufferedWriter().use { it.write(text) }
            assertEquals(process.errorStream.bufferedReader().readText(), 0, process.waitFor())
        }
    }

    @Test fun rejectsUnknownEndpointsAndChangedPasswordInputContract() {
        assertThrows(IllegalArgumentException::class.java) {
            PhoneComputerSetup.guidedScript(source, "http://example.com")
        }
        assertThrows(IllegalStateException::class.java) {
            PhoneComputerSetup.guidedScript(source.replace("IFS= read -r meow_password", "false"), OpenCodeLocalConnection.ORIGINAL_ADDRESS)
        }
    }

    @Test fun onboardingHasNoBlindShellStartOrUnverifiedMirrorDownload() {
        val onboarding = File("src/main/java/com/example/meowmba/ui/onboarding/SetupWizardRoute.kt").readText()
        assertFalse(onboarding.contains("onStartOpenCode()"))
        assertFalse(onboarding.contains("TERMUX_NETBIRD"))
        assertTrue(onboarding.contains("TermuxInstallGuidance(support)"))
        assertTrue(onboarding.contains("OpenCodeLocalConnectionCard(onVerified"))
        assertTrue(TermuxComputerLinks.INSTALL_GUIDANCE.contains("Download APK"))
    }
}
