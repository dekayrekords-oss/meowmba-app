package com.example.meowmba

import java.io.File
import org.junit.Assert.*
import org.junit.Test

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
}
