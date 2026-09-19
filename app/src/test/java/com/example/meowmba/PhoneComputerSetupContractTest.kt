package com.example.meowmba

import java.io.File
import org.junit.Assert.*
import org.junit.Test
import com.example.meowmba.core.computer.PhoneComputerSetup
import com.example.meowmba.core.computer.OpenCodeLocalConnection

class PhoneComputerSetupContractTest {
    private fun script() = File("src/main/assets/computer/setup-phone.sh").readText()

    @Test fun installerIsValidBash() {
        val process = ProcessBuilder("bash", "-n", "src/main/assets/computer/setup-phone.sh").start()
        assertEquals(process.errorStream.bufferedReader().readText(), 0, process.waitFor())
    }

    @Test fun preservesExistingUbuntuAndRestrictsServer() {
        val source = script()
        assertTrue(source.contains("installed-rootfs/ubuntu"))
        assertTrue(source.contains("if ! command -v opencode"))
        assertTrue(source.contains("--hostname 127.0.0.1 --port 4096"))
        assertTrue(source.contains("\"permission\":{\"*\":\"ask\"}"))
        assertFalse(source.contains("0.0.0.0"))
        assertFalse(source.contains("proot-distro remove"))
        assertFalse(source.contains("--auto"))
    }

    @Test fun secretIsReadFromInputNotWrittenToFile() {
        val source = script()
        assertTrue(source.contains("IFS= read -r meow_password"))
        assertTrue(source.contains("IFS= read -r OPENCODE_SERVER_PASSWORD"))
        assertFalse(source.contains("export OPENCODE_SERVER_PASSWORD="))
        assertTrue(source.contains("set +x"))
    }

    @Test fun phoneWorkerUsesOnDemandSearchWithoutDisablingTaskToolsOrChangingModels() {
        val source = script()
        val server = source.substringAfter("IFS= read -r OPENCODE_SERVER_PASSWORD")
        assertTrue(server.contains("cd \"${'$'}HOME/MeowProjects\""))
        assertTrue(server.contains("export OPENCODE_DISABLE_FFF=1"))
        assertTrue(server.indexOf("export OPENCODE_DISABLE_FFF=1") < server.indexOf("exec opencode serve"))
        assertTrue(server.contains("\"permission\":{\"*\":\"ask\"}"))
        assertFalse(server.contains("--model"))
        assertFalse(server.contains("\"tools\""))
        assertFalse(server.contains("pkill"))
    }

    @Test fun recoveryUsesOnlyTheSeparatePortAndPreservesExistingServer() {
        val original = script()
        assertEquals(original, PhoneComputerSetup.scriptForAddress(original, OpenCodeLocalConnection.ORIGINAL_ADDRESS))
        val recovery = PhoneComputerSetup.scriptForAddress(original, OpenCodeLocalConnection.RECOVERY_ADDRESS)
        assertFalse(recovery.contains("4096"))
        assertTrue(recovery.contains("--hostname 127.0.0.1 --port 4097"))
        assertTrue(recovery.contains("/dev/tcp/127.0.0.1/4097"))
        assertTrue(recovery.contains("setup-server-4097.log"))
        assertFalse(recovery.contains("pkill"))
        assertFalse(recovery.contains("kill -9"))
        assertThrows(IllegalArgumentException::class.java) {
            PhoneComputerSetup.scriptForAddress(original, "http://example.com:4097")
        }
        val process = ProcessBuilder("bash", "-n").start()
        process.outputStream.bufferedWriter().use { it.write(recovery) }
        assertEquals(process.errorStream.bufferedReader().readText(), 0, process.waitFor())
    }
}
