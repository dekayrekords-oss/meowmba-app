package com.example.meowmba

import com.example.meowmba.core.agent.ModelDownloadPolicy
import com.example.meowmba.core.agent.ModelDownloader
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class ModelDownloadPolicyTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `Hub token is scoped to HTTPS standard-port exact Hub origin`() {
        assertEquals("hf_example", ModelDownloadPolicy.tokenForUrl("https://huggingface.co/user/model/resolve/main/model.task", "hf_example"))
        assertEquals("hf_example", ModelDownloadPolicy.tokenForUrl("https://huggingface.co:443/file", "hf_example"))
        listOf("https://huggingface.co.evil.example/file", "https://cdn-lfs.huggingface.co/file", "https://huggingface.co:8443/file",
            "http://huggingface.co/file", "https://example.com/huggingface.co/file", "https://token@huggingface.co/file",
            "http://192.168.1.69/model.task", "https://cas-bridge.xethub.hf.co/weights").forEach {
            assertNull(it, ModelDownloadPolicy.tokenForUrl(it, "hf_example"))
        }
        assertNull(ModelDownloadPolicy.tokenForUrl("https://huggingface.co/file", "hf_example\nheader"))
    }

    @Test fun `signed CDN redirect remains usable but carries no Hub token`() {
        val target = ModelDownloadPolicy.redirectTarget("https://huggingface.co/owner/repo/resolve/main/model.task",
            "https://cas-bridge.xethub.hf.co/xet/weights?signature=abc%2B123&expires=999")
        assertEquals("https://cas-bridge.xethub.hf.co/xet/weights?signature=abc%2B123&expires=999", target)
        assertNull(ModelDownloadPolicy.tokenForUrl(target, "hf_example"))
        assertEquals("https://huggingface.co/api/resolve/file", ModelDownloadPolicy.redirectTarget("https://huggingface.co/owner/repo/file", "/api/resolve/file"))
    }

    @Test fun `redirects enforce cleartext boundary and refuse HTTPS downgrade`() {
        assertEquals("http://192.168.1.69/models/chat.task", ModelDownloadPolicy.redirectTarget("http://192.168.1.69/download", "/models/chat.task"))
        assertTrue(runCatching { ModelDownloadPolicy.redirectTarget("https://huggingface.co/file", "http://example.com/file") }.isFailure)
        assertTrue(runCatching { ModelDownloadPolicy.redirectTarget("https://huggingface.co/file", "http://192.168.1.69/file") }.isFailure)
        assertTrue(runCatching { ModelDownloadPolicy.redirectTarget("http://192.168.1.69/file", "http://public.example/file") }.isFailure)
        assertTrue(runCatching { ModelDownloadPolicy.redirectTarget("https://example.com/file", "file:///etc/passwd") }.isFailure)
        assertTrue(runCatching { ModelDownloadPolicy.validateUrl("https://user:password@example.com/file") }.isFailure)
    }

    @Test fun `destination names stay simple and preserve GGUF image support`() {
        val downloader = ModelDownloader(temporary.newFolder("models"))
        assertEquals("sdxl-lightning.q8_0.gguf", downloader.destinationFor("sdxl-lightning.q8_0.gguf").name)
        assertEquals("chat.task", downloader.destinationFor("chat.task").name)
        listOf("", ".", "..", "../escape.task", "/tmp/escape.task", "folder/model.task", "folder\\model.task", "model\n.task")
            .forEach { assertTrue(it, runCatching { downloader.destinationFor(it) }.isFailure) }
    }

    @Test fun `symlink destinations cannot escape or alias a different model`() {
        val folder = temporary.newFolder("models")
        val other = temporary.newFile("other.task").apply { writeText("keep") }
        Files.createSymbolicLink(File(folder, "link.task").toPath(), other.toPath())
        assertTrue(runCatching { ModelDownloader(folder).destinationFor("link.task") }.isFailure)
        assertEquals("keep", other.readText())
    }

    @Test fun `custom download engine never receives the saved Hub token`() = runTest {
        val tokens = mutableListOf<String?>()
        val downloader = ModelDownloader(temporary.newFolder("models"), hfTokenProvider = { "hf_example" },
            engine = { _, dest, token, _ -> tokens += token; dest.writeBytes(byteArrayOf(1, 2, 3)) })
        downloader.download("https://example.com/model.gguf", "image.gguf") {}
        downloader.download("http://192.168.1.69/model.task", "lan.task") {}
        downloader.download("https://huggingface.co/user/repo/resolve/main/model.task", "hub.task") {}
        assertEquals(listOf(null, null, "hf_example"), tokens)
        assertTrue(downloader.destinationFor("image.gguf").isFile)
    }

    @Test fun `invalid destination and public HTTP fail before file or network changes`() = runTest {
        var calls = 0
        val folder = temporary.newFolder("models")
        val downloader = ModelDownloader(folder, engine = { _, _, _, _ -> calls++ })
        assertTrue(runCatching { downloader.download("https://example.com/chat.task", "../escape.task") {} }.isFailure)
        assertTrue(runCatching { downloader.download("http://example.com/chat.task", "chat.task") {} }.isFailure)
        assertEquals(0, calls)
        assertFalse(File(folder, "chat.task.part").exists())
    }

    @Test fun `actual download requests drop authorization across signed CDN redirect`() = runTest {
        val headers = mutableListOf<Pair<String, String?>>()
        val engine = MockEngine { request ->
            headers += request.url.host to request.headers[HttpHeaders.Authorization]
            if (request.url.host == "huggingface.co") respond("", HttpStatusCode.Found,
                headersOf(HttpHeaders.Location, "https://cas-bridge.xethub.hf.co/model?signature=signed"))
            else respond("data", HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, "4"))
        }
        val client = HttpClient(engine) { followRedirects = false }
        val file = temporary.newFile("download.part")
        try {
            ModelDownloader.downloadWithClient(client, "https://huggingface.co/user/repo/resolve/main/chat.task", file, "hf_example") { _, _ -> }
            assertEquals(listOf("huggingface.co" to "Bearer hf_example", "cas-bridge.xethub.hf.co" to null), headers)
            assertEquals("data", file.readText())
        } finally { client.close() }
    }

    @Test fun `custom origin cannot cause a saved Hub token to appear on a redirect`() = runTest {
        val headers = mutableListOf<String?>()
        val engine = MockEngine { request ->
            headers += request.headers[HttpHeaders.Authorization]
            if (request.url.host == "example.com") respond("", HttpStatusCode.TemporaryRedirect,
                headersOf(HttpHeaders.Location, "https://huggingface.co/user/repo/resolve/main/chat.task"))
            else respond("data", HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, "4"))
        }
        val client = HttpClient(engine) { followRedirects = false }
        try {
            ModelDownloader.downloadWithClient(client, "https://example.com/chat.task", temporary.newFile("custom.part"), "hf_example") { _, _ -> }
            assertEquals(listOf(null, null), headers)
        } finally { client.close() }
    }

    @Test fun `download redirect loops stop after eight hops`() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://huggingface.co/loop"))
        }
        val client = HttpClient(engine) { followRedirects = false }
        try {
            val result = runCatching {
                ModelDownloader.downloadWithClient(client, "https://huggingface.co/loop", temporary.newFile("loop.part"), "hf_example") { _, _ -> }
            }
            assertTrue(result.isFailure)
            assertEquals(9, calls)
        } finally { client.close() }
    }
}
