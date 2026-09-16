package com.example.meowmba.ui.companion
import org.junit.Assert.*
import org.junit.Test
class ComputerCommandFrameTest {
    @Test fun `echo and partial output never prove completion`() {
        val f = ComputerCommandFrame("test123")
        assertNull(f.result(f.wrap("sleep 2")))
        assertNull(f.result("MEOW_test123_BEGIN\nstill working"))
    }
    @Test fun `real shell frame measures exit status and delayed output`() {
        val f = ComputerCommandFrame("test123")
        val p = ProcessBuilder("/bin/sh", "-c", f.wrap("printf hello; sleep 0.1; printf world; exit 7")).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        val result = f.result(out)!!
        assertEquals(7, result.exitCode); assertFalse(result.ok); assertEquals("helloworld", result.output)
    }
    @Test fun `empty successful output still has a verified exit`() {
        val f = ComputerCommandFrame("test123")
        val p = ProcessBuilder("/bin/sh", "-c", f.wrap("true")).start()
        val result = f.result(p.inputStream.bufferedReader().readText())!!
        p.waitFor(); assertTrue(result.ok); assertEquals(0, result.exitCode)
    }
}
