package com.example.meowmba

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Runs the shipped setup-phone.sh against stand-in Termux tools and checks the OpenCode
 * server really is launched. A stray apostrophe in a comment inside the single-quoted
 * `bash -c` block once ended the quote early: the worker exited at once with no output,
 * setup reported "code 1", and `bash -n` still passed.
 */
class PhoneSetupScriptLaunchTest {
    private val script = File("src/main/assets/computer/setup-phone.sh").readText()

    @Test fun serverBlockReachesOpencodeServe() {
        val bash = File("/bin/bash")
        assumeTrue("needs bash", bash.canExecute())
        val root = Files.createTempDirectory("meow-setup").toFile()
        try {
            val home = File(root, "home").apply { mkdirs() }
            val prefix = File(root, "usr").apply { File(this, "var/lib/proot-distro/containers/ubuntu/rootfs").mkdirs() }
            val bin = File(root, "bin").apply { mkdirs() }
            val record = File(root, "calls.txt")
            // proot-distro stand-in: runs the inner script with a fake opencode on PATH.
            File(bin, "opencode").writeExecutable("#!/bin/bash\n[ \"$1\" = serve ] && { echo \"serve $*\" >> '${record.path}'; exec sleep 5; }\nexit 0\n")
            File(bin, "proot-distro").writeExecutable(
                "#!/bin/bash\n[ \"$1\" = login ] || exit 0\nshift 2; [ \"$1\" = -- ] && shift\nexport HOME='${home.path}' PATH='${bin.path}':\$PATH\nexec \"$@\"\n")
            File(bin, "flock").writeExecutable("#!/bin/bash\nexit 0\n")
            val process = ProcessBuilder(bash.path, "-c", script.replace("4096", "47961").replace("#!/data/data/com.termux/files/usr/bin/bash", "#!/bin/bash"))
                .directory(root).redirectErrorStream(true).apply {
                    environment()["HOME"] = home.path
                    environment()["PREFIX"] = prefix.path
                    environment()["TERMUX__PREFIX"] = prefix.path
                    environment()["PATH"] = bin.path + ":/usr/bin:/bin"
                }.start()
            process.outputStream.use { it.write("probe-password-12345\n".toByteArray()) }
            assertTrue("setup script hung", process.waitFor(30, TimeUnit.SECONDS))
            val output = process.inputStream.bufferedReader().readText()
            assertEquals("setup exited non-zero:\n$output", 0, process.exitValue())
            assertTrue("opencode serve was never launched:\n$output", record.readText().contains("serve serve --hostname 127.0.0.1 --port 47961"))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun File.writeExecutable(text: String) { writeText(text); setExecutable(true) }
}
