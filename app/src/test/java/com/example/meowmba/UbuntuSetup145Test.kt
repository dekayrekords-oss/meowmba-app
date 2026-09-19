package com.example.meowmba

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Executes the real detection function; package installation is a bounded fake. */
class UbuntuSetup145Test {
    @get:Rule val temporary = TemporaryFolder()
    private fun runFixture(layout: String?, mode: String = "new"): Pair<Int, String> {
        val root = temporary.newFolder()
        layout?.let { File(root, it).mkdirs() }
        if (mode == "dangling") Files.createSymbolicLink(File(root, "containers/ubuntu").toPath(), File(root, "missing").toPath())
        val source = File("src/main/assets/computer/setup-phone.sh").readText()
        val function = source.substringAfter("# BEGIN UBUNTU DETECTION\n").substringBefore("# END UBUNTU DETECTION")
        val harness = """
            set -eu
            meow_fixture="${'$'}1"
            meow_mode="${'$'}2"
            proot-distro() {
                [ "${'$'}*" = 'install ubuntu' ] || exit 90
                printf '%s\n' INSTALL_CALLED
                case "${'$'}meow_mode" in
                    failure) return 42 ;;
                    unrecognized) return 0 ;;
                    *) mkdir -p "${'$'}meow_fixture/containers/ubuntu/rootfs" ;;
                esac
            }
            $function
            meow_ensure_ubuntu "${'$'}meow_fixture"
            meow_ensure_ubuntu "${'$'}meow_fixture"
        """.trimIndent()
        val process = ProcessBuilder("bash", "-c", harness, "test", root.absolutePath, mode).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        return process.waitFor() to output
    }
    @Test fun existingModernContainerIsReusedWithoutInstallEvenOnRepeat() {
        val (exit, output) = runFixture("containers/ubuntu/rootfs")
        assertEquals(output, 0, exit); assertFalse(output.contains("INSTALL_CALLED"))
    }
    @Test fun existingLegacyContainerIsReusedWithoutInstallEvenOnRepeat() {
        val (exit, output) = runFixture("installed-rootfs/ubuntu")
        assertEquals(output, 0, exit); assertFalse(output.contains("INSTALL_CALLED"))
    }
    @Test fun incompleteContainerIsPreserved() {
        val (exit, output) = runFixture("containers/ubuntu")
        assertEquals(output, 24, exit); assertFalse(output.contains("INSTALL_CALLED"))
    }
    @Test fun brokenContainerLinkIsPreserved() {
        val (exit, output) = runFixture("containers", "dangling")
        assertEquals(output, 24, exit); assertFalse(output.contains("INSTALL_CALLED"))
    }
    @Test fun freshInstallRunsOnlyOnceThenReusesNewLayout() {
        val (exit, output) = runFixture(null)
        assertEquals(output, 0, exit)
        assertEquals(1, output.lines().count { it == "INSTALL_CALLED" })
    }
    @Test fun failedInstallDoesNotContinue() {
        val (exit, output) = runFixture(null, "failure")
        assertEquals(output, 42, exit)
        assertEquals(1, output.lines().count { it == "INSTALL_CALLED" })
    }
    @Test fun unknownLayoutDoesNotClaimSuccess() {
        val (exit, output) = runFixture(null, "unrecognized")
        assertEquals(output, 24, exit)
    }
}
